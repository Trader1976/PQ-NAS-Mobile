package com.pqnas.mobile.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
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

    @POST("/api/v4/files/delete")
    suspend fun deleteFile(
        @Query("path") path: String
    ): DeleteFileResponse

    @POST("/api/v4/files/move")
    suspend fun moveFile(
        @Query("from") from: String,
        @Query("to") to: String
    ): MoveFileResponse

    @POST("/api/v4/files/mkdir")
    suspend fun mkdir(
        @Query("path") path: String
    ): MkdirResponse

    @PUT("/api/v4/files/put")
    suspend fun uploadFile(
        @Query("path") path: String,
        @Query("overwrite") overwrite: Int = 0,
        @Body body: RequestBody
    ): UploadFileResponse
}