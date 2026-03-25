package com.pqnas.mobile.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

interface FilesApi {
    @GET("/api/v4/files/list")
    suspend fun listFiles(
        @Query("path") path: String? = null
    ): FilesListResponse

    @Streaming
    @GET("/api/v4/files/get")
    suspend fun downloadFile(
        @Query("path") path: String
    ): ResponseBody
}