package com.pqnas.mobile.files

import com.pqnas.mobile.api.ApiFactory
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

class FilesRepository(
    private val baseUrlProvider: () -> String,
    private val accessTokenProvider: () -> String
) {
    suspend fun list(path: String? = null) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            accessTokenProvider = accessTokenProvider
        ).listFiles(path)

    suspend fun download(path: String): ResponseBody =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            accessTokenProvider = accessTokenProvider
        ).downloadFile(path)

    suspend fun delete(path: String) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            accessTokenProvider = accessTokenProvider
        ).deleteFile(path)

    suspend fun move(from: String, to: String) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            accessTokenProvider = accessTokenProvider
        ).moveFile(from, to)

    suspend fun mkdir(path: String) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            accessTokenProvider = accessTokenProvider
        ).mkdir(path)

    suspend fun upload(path: String, body: RequestBody, overwrite: Boolean = false) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            accessTokenProvider = accessTokenProvider
        ).uploadFile(
            path = path,
            overwrite = if (overwrite) 1 else 0,
            body = body
        )

    suspend fun createTextFile(path: String, text: String = "", overwrite: Boolean = false) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            accessTokenProvider = accessTokenProvider
        ).uploadFile(
            path = path,
            overwrite = if (overwrite) 1 else 0,
            body = text.toByteArray(Charsets.UTF_8).toRequestBody(null)
        )
}