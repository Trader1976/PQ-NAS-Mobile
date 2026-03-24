package com.pqnas.mobile.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApi {
    @GET("/api/v5/session")
    suspend fun startSession(): V5SessionResponse

    @GET("/api/v5/status")
    suspend fun getStatus(@Query("k") k: String): V5StatusResponse

    @POST("/api/v5/consume_app")
    suspend fun consumeApp(@Body req: ConsumeAppRequest): ConsumeAppResponse

    @POST("/api/v5/token/refresh")
    suspend fun refresh(@Body req: RefreshTokenRequest): RefreshTokenResponse

    @POST("/api/v5/app_pair/consume")
    suspend fun consumePair(
        @Body req: PairConsumeRequest
    ): PairConsumeResponse

}