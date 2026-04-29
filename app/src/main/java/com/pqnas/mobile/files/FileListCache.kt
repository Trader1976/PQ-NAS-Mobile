package com.pqnas.mobile.files

import android.content.Context
import com.pqnas.mobile.api.FileItemDto
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
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.filesDir, "file_list_cache")

    fun load(
        namespace: String,
        scope: FileScope,
        path: String?
    ): CachedFileList? {
        return runCatching {
            val file = fileFor(namespace, scope, path)
            if (!file.isFile) return@runCatching null

            val root = JSONObject(file.readText())
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

            fileFor(namespace, scope, path).writeText(root.toString())
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
