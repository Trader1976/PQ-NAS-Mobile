package com.pqnas.mobile.files

import com.pqnas.mobile.api.ApiFactory

class FilesRepository(
    private val baseUrlProvider: () -> String,
    private val accessTokenProvider: () -> String
) {
    suspend fun list(path: String? = null) =
        ApiFactory.createFilesApi(
            baseUrl = baseUrlProvider(),
            accessTokenProvider = accessTokenProvider
        ).listFiles(path)
}