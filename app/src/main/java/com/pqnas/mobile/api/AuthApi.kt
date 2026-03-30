package com.pqnas.mobile.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApi {
    @POST("/api/v5/token/refresh")
    suspend fun refresh(@Body req: RefreshTokenRequest): RefreshTokenResponse

    @POST("/api/v5/app_pair/consume")
    suspend fun consumePair(
        @Body req: PairConsumeRequest
    ): PairConsumeResponse

    @GET("/api/v4/files/read_text")
    suspend fun readText(
        @Query("path") path: String
    ): ReadTextResponse

    @POST("/api/v4/files/write_text")
    suspend fun writeText(
        @Body req: WriteTextRequest
    ): WriteTextResponse
}