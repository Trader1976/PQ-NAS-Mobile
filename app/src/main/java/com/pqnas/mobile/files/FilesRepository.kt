package com.pqnas.mobile.files

import com.pqnas.mobile.api.ApiFactory
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
}