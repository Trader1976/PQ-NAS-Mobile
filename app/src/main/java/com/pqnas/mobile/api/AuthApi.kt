package com.pqnas.mobile.api

import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("/api/v5/token/refresh")
    suspend fun refresh(@Body req: RefreshTokenRequest): RefreshTokenResponse

    @POST("/api/v5/app_pair/consume")
    suspend fun consumePair(
        @Body req: PairConsumeRequest
    ): PairConsumeResponse
}