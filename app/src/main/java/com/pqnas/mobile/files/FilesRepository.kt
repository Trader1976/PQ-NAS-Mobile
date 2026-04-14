package com.pqnas.mobile.files

import com.pqnas.mobile.api.ApiFactory
import com.pqnas.mobile.api.FavoriteMutateRequest
import com.pqnas.mobile.auth.TokenStore
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

class FilesRepository(
    private val tokenStore: TokenStore,
    private val baseUrlProvider: () -> String
) {
    suspend fun list(path: String? = null) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).listFiles(path)

    fun baseUrlForDisplay(): String = baseUrlProvider()

    internal fun createWorkspaceFilesApiInternal() =
        ApiFactory.createWorkspaceFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        )

    suspend fun getMyStorage() =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).getMyStorage()

    suspend fun getFavorites() =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).listFavorites()

    suspend fun addFavorite(path: String, type: String) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).addFavorite(
            FavoriteMutateRequest(
                path = path,
                type = if (type == "dir") "dir" else "file"
            )
        )

    suspend fun removeFavorite(path: String, type: String) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).removeFavorite(
            FavoriteMutateRequest(
                path = path,
                type = if (type == "dir") "dir" else "file"
            )
        )

    suspend fun getShares(workspaceId: String? = null) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).listShares(workspaceId)

    suspend fun createShare(path: String, type: String, expiresSec: Long? = 86400L) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).createShare(
            com.pqnas.mobile.api.CreateShareRequest(
                path = path,
                type = if (type == "dir") "dir" else "file",
                expires_sec = expiresSec
            )
        )

    suspend fun revokeShare(token: String) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).revokeShare(
            com.pqnas.mobile.api.RevokeShareRequest(token)
        )

    suspend fun download(path: String): ResponseBody =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).downloadFile(path)

    suspend fun readText(path: String) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).readTextFile(path)

    suspend fun writeText(
        path: String,
        text: String,
        expectedMtimeEpoch: Long? = null,
        expectedSha256: String? = null
    ) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).writeTextFile(
            com.pqnas.mobile.api.WriteTextRequest(
                path = path,
                text = text,
                expected_mtime_epoch = expectedMtimeEpoch,
                expected_sha256 = expectedSha256
            )
        )

    suspend fun delete(path: String) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).deleteFile(path)

    suspend fun move(from: String, to: String) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).moveFile(from, to)

    suspend fun mkdir(path: String) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).mkdir(path)

    suspend fun upload(path: String, body: RequestBody, overwrite: Boolean = false) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).uploadFile(
            path = path,
            overwrite = if (overwrite) 1 else 0,
            body = body
        )

    suspend fun createTextFile(path: String, text: String = "", overwrite: Boolean = false) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            tokenStore = tokenStore
        ).uploadFile(
            path = path,
            overwrite = if (overwrite) 1 else 0,
            body = text.toByteArray(Charsets.UTF_8).toRequestBody(null)
        )
}