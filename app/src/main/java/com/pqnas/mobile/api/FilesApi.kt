package com.pqnas.mobile.api

import retrofit2.http.GET
import retrofit2.http.Query

interface FilesApi {
    @GET("/api/v4/files/list")
    suspend fun listFiles(@Query("path") path: String? = null): FilesListResponse
}