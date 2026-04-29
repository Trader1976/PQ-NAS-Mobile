package com.pqnas.mobile.files

import android.content.Context
import com.pqnas.mobile.api.FileItemDto
import com.pqnas.mobile.auth.EncryptedAuthValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class CachedFileList(
    val path: String?,
    val items: List<FileItemDto>,
    val savedAtEpochMs: Long
)

class FileListCache(
    context: Context
) {
    private companion object {
        const val MAX_CACHE_AGE_MS = 24L * 60L * 60L * 1000L
    }

    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.filesDir, "file_list_cache")

    init {
        migrateLegacyPlaintextCacheFiles()
    }

    fun load(
        namespace: String,
        scope: FileScope,
        path: String?
    ): CachedFileList? {
        return runCatching {
            val file = fileFor(namespace, scope, path)
            if (!file.isFile) return@runCatching null

            val raw = file.readText()
            val jsonText = EncryptedAuthValue.decryptOrLegacy(raw)
            if (jsonText.isBlank()) return@runCatching null

            val root = JSONObject(jsonText)

            val savedAt = root.optLong("savedAtEpochMs", 0L)
            val ageMs = System.currentTimeMillis() - savedAt
            if (savedAt <= 0L || ageMs < 0L || ageMs > MAX_CACHE_AGE_MS) {
                runCatching { file.delete() }
                return@runCatching null
            }

            val arr = root.optJSONArray("items") ?: JSONArray()

            val cachedItems = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name", "")
                    if (name.isBlank()) continue

                    add(
                        FileItemDto(
                            name = name,
                            type = o.optString("type", "file"),
                            size_bytes = nullableLong(o, "size_bytes"),
                            mtime_unix = nullableLong(o, "mtime_unix"),
                            isFavorite = o.optBoolean("isFavorite", false),
                            isShared = o.optBoolean("isShared", false)
                        )
                    )
                }
            }

            CachedFileList(
                path = normalizePath(root.optString("path", "")),
                items = cachedItems,
                savedAtEpochMs = root.optLong("savedAtEpochMs", 0L)
            )
        }.getOrNull()
    }

    fun save(
        namespace: String,
        scope: FileScope,
        path: String?,
        items: List<FileItemDto>
    ) {
        runCatching {
            cacheDir.mkdirs()

            val arr = JSONArray()
            for (item in items) {
                arr.put(
                    JSONObject()
                        .put("name", item.name)
                        .put("type", item.type)
                        .put("size_bytes", item.size_bytes)
                        .put("mtime_unix", item.mtime_unix)
                        .put("isFavorite", item.isFavorite)
                        .put("isShared", item.isShared)
                )
            }

            val root = JSONObject()
                .put("path", normalizePath(path).orEmpty())
                .put("savedAtEpochMs", System.currentTimeMillis())
                .put("items", arr)

            // Cache contains private filenames/metadata, so keep it encrypted at rest.
            // decryptOrLegacy() above still allows old plaintext cache files to be read once.
            fileFor(namespace, scope, path).writeText(
                EncryptedAuthValue.encrypt(root.toString())
            )
        }
    }

    private fun migrateLegacyPlaintextCacheFiles() {
        runCatching {
            if (!cacheDir.isDirectory) return@runCatching

            val files = cacheDir.listFiles { file ->
                file.isFile && file.name.endsWith(".json")
            } ?: return@runCatching

            for (file in files) {
                val raw = file.readText()
                if (raw.isBlank()) continue

                if (EncryptedAuthValue.needsUpgrade(raw)) {
                    val jsonText = EncryptedAuthValue.decryptOrLegacy(raw)
                    if (jsonText.isNotBlank()) {
                        runCatching {
                            JSONObject(jsonText)
                            file.writeText(EncryptedAuthValue.encrypt(jsonText))
                        }
                    }
                    continue
                }

                if (EncryptedAuthValue.isEncrypted(raw)) continue

                // Only migrate valid legacy JSON cache files. Invalid/corrupt files are ignored.
                runCatching {
                    JSONObject(raw)
                    file.writeText(EncryptedAuthValue.encrypt(raw))
                }
            }
        }
    }

    private fun fileFor(
        namespace: String,
        scope: FileScope,
        path: String?
    ): File {
        val key = listOf(
            namespace.trim().trimEnd('/'),
            scopeKey(scope),
            normalizePath(path).orEmpty()
        ).joinToString("|")

        return File(cacheDir, "${sha256Hex(key)}.json")
    }

    private fun scopeKey(scope: FileScope): String {
        return when (scope) {
            FileScope.User -> "user"
            is FileScope.Workspace -> "workspace:${scope.workspaceId}"
        }
    }

    private fun normalizePath(path: String?): String? {
        return path.orEmpty()
            .replace("\\", "/")
            .trim('/')
            .split("/")
            .filter { it.isNotBlank() }
            .joinToString("/")
            .ifBlank { null }
    }

    private fun nullableLong(o: JSONObject, key: String): Long? {
        if (!o.has(key) || o.isNull(key)) return null
        return o.optLong(key)
    }

    private fun sha256Hex(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))

        return bytes.joinToString("") { "%02x".format(it) }
    }
}
