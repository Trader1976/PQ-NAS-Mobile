package com.pqnas.mobile.files

import com.pqnas.mobile.api.ApiFactory
import com.pqnas.mobile.api.ChunkedUploadCancelRequest
import com.pqnas.mobile.api.ChunkedUploadFinishRequest
import com.pqnas.mobile.api.ChunkedUploadStartRequest
import com.pqnas.mobile.api.FavoriteMutateRequest
import com.pqnas.mobile.auth.TokenStore
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.File
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import com.pqnas.mobile.api.DropZoneCreateRequest
import com.pqnas.mobile.api.DropZoneDisableRequest

class FilesRepository(
    private val tokenStore: TokenStore,
    private val baseUrlProvider: () -> String
) {
    private val filesApi by lazy {
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        )
    }

    private val workspaceFilesApi by lazy {
        ApiFactory.createWorkspaceFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        )
    }

    private val echoStackApi by lazy {
        ApiFactory.createEchoStackApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        )
    }

    private val authedOkHttpClient by lazy {
        ApiFactory.createAuthedOkHttpClient(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        )
    }

    suspend fun list(path: String? = null) =
        try {
            filesApi.listFiles(path)
        } catch (e: HttpException) {
            throwSafeApiError(e, "Load")
        }

    fun baseUrlForDisplay(): String = baseUrlProvider()

    fun createAuthedOkHttpClient() =
        authedOkHttpClient

    internal fun createWorkspaceFilesApiInternal() =
        workspaceFilesApi

    internal fun createEchoStackApiInternal() =
        echoStackApi
    internal fun createFilesApiInternal() =
        filesApi
    suspend fun getMyStorage() =
        filesApi.getMyStorage()

    suspend fun isServerAppAvailable(id: String): Boolean {
        return runCatching {
            val r = filesApi.hasApp(id)
            r.ok && r.available && r.mobile
        }.getOrDefault(false)
    }

    suspend fun listDropZones() =
        filesApi.listDropZones()

    suspend fun createDropZone(
        name: String = "Drop Zone",
        destinationPath: String = "",
        password: String = "",
        expiresInSeconds: Long = 7L * 24L * 60L * 60L,
        maxFileBytes: Long = 0L,
        maxTotalBytes: Long = 0L
    ) =
        filesApi.createDropZone(
            DropZoneCreateRequest(
                name = name,
                destination_path = destinationPath,
                password = password,
                expires_in_seconds = expiresInSeconds,
                max_file_bytes = maxFileBytes,
                max_total_bytes = maxTotalBytes
            )
        )

    suspend fun disableDropZone(id: String, disabled: Boolean = true) =
        filesApi.disableDropZone(
            DropZoneDisableRequest(
                id = id,
                disabled = disabled
            )
        )

    suspend fun getFavorites() =
        filesApi.listFavorites()

    suspend fun addFavorite(path: String, type: String) =
        filesApi.addFavorite(
            FavoriteMutateRequest(
                path = path,
                type = if (type == "dir") "dir" else "file"
            )
        )

    suspend fun removeFavorite(path: String, type: String) =
        filesApi.removeFavorite(
            FavoriteMutateRequest(
                path = path,
                type = if (type == "dir") "dir" else "file"
            )
        )

    suspend fun getShares(workspaceId: String? = null) =
        filesApi.listShares(workspaceId)

    suspend fun createShare(path: String, type: String, expiresSec: Long? = 86400L) =
        filesApi.createShare(
            com.pqnas.mobile.api.CreateShareRequest(
                path = path,
                type = if (type == "dir") "dir" else "file",
                expires_sec = expiresSec
            )
        )

    suspend fun revokeShare(token: String) =
        filesApi.revokeShare(
            com.pqnas.mobile.api.RevokeShareRequest(token)
        )

    suspend fun download(path: String): ResponseBody =
        filesApi.downloadFile(path)

    suspend fun readText(path: String) =
        filesApi.readTextFile(path)

    suspend fun writeText(
        path: String,
        text: String,
        expectedMtimeEpoch: Long? = null,
        expectedSha256: String? = null
    ) =
        filesApi.writeTextFile(
            com.pqnas.mobile.api.WriteTextRequest(
                path = path,
                text = text,
                expected_mtime_epoch = expectedMtimeEpoch,
                expected_sha256 = expectedSha256
            )
        )

    suspend fun delete(path: String) =
        filesApi.deleteFile(path)

    suspend fun move(from: String, to: String) =
        filesApi.moveFile(from, to)

    suspend fun mkdir(path: String) =
        filesApi.mkdir(path)

    suspend fun upload(path: String, body: RequestBody, overwrite: Boolean = false) =
        filesApi.uploadFile(
            path = path,
            overwrite = if (overwrite) 1 else 0,
            body = body
        )

    suspend fun uploadChunkedFromTempFile(
        path: String,
        file: File,
        mimeType: String? = null,
        overwrite: Boolean = false,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ) {
        val api = filesApi

        val totalBytes = file.length()
        var uploadId = ""
        var finishStarted = false

        try {
            val start = api.startChunkedUpload(
                ChunkedUploadStartRequest(
                    path = path,
                    size_bytes = totalBytes,
                    overwrite = overwrite
                )
            )

            uploadId = start.upload_id

            val chunkSize = start.chunk_size.coerceAtLeast(1L)
            val chunksTotal = start.chunks_total.coerceAtLeast(
                if (totalBytes <= 0L) 0L else ((totalBytes + chunkSize - 1L) / chunkSize)
            )

            var committedBytes = 0L

            for (index in 0L until chunksTotal) {
                if (isCancelled()) {
                    throw CancellationException("Upload cancelled")
                }

                val offset = index * chunkSize
                val remaining = (totalBytes - offset).coerceAtLeast(0L)
                val thisChunkBytes = minOf(chunkSize, remaining)

                val body = tempFileSliceRequestBody(
                    file = file,
                    offset = offset,
                    byteCount = thisChunkBytes,
                    mimeType = mimeType,
                    onProgress = { sent, _ ->
                        onProgress((committedBytes + sent).coerceAtMost(totalBytes), totalBytes)
                    }
                )

                api.uploadChunk(
                    uploadId = uploadId,
                    index = index,
                    body = body
                )

                committedBytes = (committedBytes + thisChunkBytes).coerceAtMost(totalBytes)
                onProgress(committedBytes, totalBytes)
            }

            if (isCancelled()) {
                throw CancellationException("Upload cancelled")
            }

            // All bytes are uploaded now. The server may still need time to
            // assemble/move/index the final file, especially for multi-GB videos.
            onProgress(totalBytes, totalBytes)
            finishStarted = true

            api.finishChunkedUpload(
                ChunkedUploadFinishRequest(upload_id = uploadId)
            )

            uploadId = ""
            onProgress(totalBytes, totalBytes)
        } catch (e: Exception) {
            if (uploadId.isNotBlank() && !finishStarted) {
                runCatching {
                    api.cancelChunkedUpload(
                        ChunkedUploadCancelRequest(upload_id = uploadId)
                    )
                }
            }
            throw e
        }
    }

    suspend fun createTextFile(path: String, text: String = "", overwrite: Boolean = false) =
        filesApi.uploadFile(
            path = path,
            overwrite = if (overwrite) 1 else 0,
            body = text.toByteArray(Charsets.UTF_8).toRequestBody(null)
        )
}