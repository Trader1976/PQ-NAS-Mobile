package com.pqnas.mobile.auth

import com.pqnas.mobile.api.ApiFactory
import com.pqnas.mobile.api.ConsumeAppRequest
import kotlinx.coroutines.delay

class AuthRepository(
    private val tokenStore: TokenStore
) {
    suspend fun saveBaseUrl(baseUrl: String) {
        tokenStore.saveBaseUrl(baseUrl)
    }

    suspend fun startSession(baseUrl: String) =
        ApiFactory.createAuthApi(baseUrl).startSession()

    suspend fun waitForApproval(baseUrl: String, k: String, maxPolls: Int = 60): Boolean {
        val api = ApiFactory.createAuthApi(baseUrl)
        repeat(maxPolls) {
            val status = api.getStatus(k)
            if (status.approved == true) return true
            if (status.pending != true && status.approved != true) return false
            delay(2000)
        }
        return false
    }

    suspend fun consumeApp(baseUrl: String, k: String): Boolean {
        val api = ApiFactory.createAuthApi(baseUrl)
        val resp = api.consumeApp(
            ConsumeAppRequest(
                k = k,
                device_name = "PQ-NAS Android",
                platform = "android",
                app_version = "0.1.0"
            )
        )
        tokenStore.saveTokens(
            accessToken = resp.access_token,
            refreshToken = resp.refresh_token,
            deviceId = resp.device_id,
            fingerprintHex = resp.fingerprint_hex,
            role = resp.role
        )

        return resp.ok
    }
    suspend fun consumePair(
        baseUrl: String,
        pairToken: String,
        deviceName: String = "PQ-NAS Android"
    ): Boolean {
        val api = ApiFactory.createAuthApi(baseUrl)
        val resp = api.consumePair(
            com.pqnas.mobile.api.PairConsumeRequest(
                pair_token = pairToken,
                device_name = deviceName,
                platform = "android",
                app_version = "0.1.0"
            )
        )

        tokenStore.saveTokens(
            accessToken = resp.access_token,
            refreshToken = resp.refresh_token,
            deviceId = resp.device_id,
            fingerprintHex = resp.fingerprint_hex,
            role = resp.role
        )
        return resp.ok
    }
}