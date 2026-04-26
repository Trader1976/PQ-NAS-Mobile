package com.pqnas.mobile.auth

import android.os.Build
import com.pqnas.mobile.BuildConfig
import com.pqnas.mobile.api.ApiFactory
import com.pqnas.mobile.api.PairConsumeRequest

class AuthRepository(
    private val tokenStore: TokenStore
) {
    suspend fun saveBaseUrl(baseUrl: String) {
        tokenStore.saveBaseUrl(baseUrl)
    }

    suspend fun consumePair(
        baseUrl: String,
        pairToken: String,
        tlsPinSha256: String,
        deviceName: String = "DNA-Nexus Android"
    ): Boolean {
        val api = ApiFactory.createAuthApi(
            baseUrl = baseUrl,
            tlsPinSha256 = tlsPinSha256
        )

        val manufacturer = Build.MANUFACTURER ?: ""
        val model = Build.MODEL ?: ""
        val osVersion = "Android ${Build.VERSION.RELEASE ?: ""}".trim()

        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                "PQNAS_PAIR",
                "pair metadata manufacturer='$manufacturer' model='$model' os='$osVersion'"
            )
        }

        val resp = api.consumePair(
            PairConsumeRequest(
                pair_token = pairToken,
                device_name = deviceName,
                platform = "android",
                app_version = BuildConfig.VERSION_NAME,
                device_model = model,
                device_manufacturer = manufacturer,
                os_version = osVersion
            )
        )

        if (!resp.ok ||
            resp.access_token.isBlank() ||
            resp.refresh_token.isBlank() ||
            resp.device_id.isBlank()
        ) {
            return false
        }

        tokenStore.saveTokens(
            accessToken = resp.access_token,
            refreshToken = resp.refresh_token,
            deviceId = resp.device_id,
            fingerprintHex = resp.fingerprint_hex,
            role = resp.role,
            baseUrl = baseUrl,
            tlsPinSha256 = tlsPinSha256
        )

        return true
    }
}
