package com.pqnas.mobile.api

import com.pqnas.mobile.auth.TokenStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object ApiFactory {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun createAuthApi(baseUrl: String): AuthApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AuthApi::class.java)
    }

    fun createFilesApi(baseUrl: String, tokenStore: TokenStore): FilesApi {
        val authInterceptor = Interceptor { chain ->
            val state = runBlocking { tokenStore.getAuthStateOnce() }
            val token = state.accessToken

            val req = if (token.isNotBlank()) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }

            chain.proceed(req)
        }

        val tokenAuthenticator = Authenticator { _: Route?, response: Response ->
            val path = response.request.url.encodedPath

            if (path == "/api/v5/token/refresh") return@Authenticator null
            if (responseCount(response) >= 2) return@Authenticator null

            runBlocking {
                val state = tokenStore.getAuthStateOnce()
                if (state.refreshToken.isBlank() || state.deviceId.isBlank()) {
                    return@runBlocking null
                }

                val currentHeader = if (state.accessToken.isNotBlank()) {
                    "Bearer ${state.accessToken}"
                } else {
                    null
                }
                val requestHeader = response.request.header("Authorization")

                if (!currentHeader.isNullOrBlank() &&
                    !requestHeader.isNullOrBlank() &&
                    requestHeader != currentHeader
                ) {
                    return@runBlocking response.request.newBuilder()
                        .header("Authorization", currentHeader)
                        .build()
                }

                try {
                    val refreshed = createAuthApi(baseUrl).refresh(
                        RefreshTokenRequest(
                            refresh_token = state.refreshToken,
                            device_id = state.deviceId
                        )
                    )

                    if (!refreshed.ok || refreshed.access_token.isBlank()) {
                        return@runBlocking null
                    }

                    tokenStore.updateAfterRefresh(
                        accessToken = refreshed.access_token,
                        deviceId = refreshed.device_id,
                        fingerprintHex = refreshed.fingerprint_hex,
                        role = refreshed.role
                    )

                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${refreshed.access_token}")
                        .build()
                } catch (_: Exception) {
                    null
                }
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FilesApi::class.java)
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}