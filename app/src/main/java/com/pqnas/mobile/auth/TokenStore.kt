package com.pqnas.mobile.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "pqnas_auth")

data class AuthState(
    val baseUrl: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val deviceId: String = "",
    val fingerprintHex: String = "",
    val role: String = ""
) {
    val isLoggedIn: Boolean
        get() = baseUrl.isNotBlank() &&
                accessToken.isNotBlank() &&
                refreshToken.isNotBlank() &&
                deviceId.isNotBlank()
}

class TokenStore(private val context: Context) {
    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val FINGERPRINT_HEX = stringPreferencesKey("fingerprint_hex")
        val ROLE = stringPreferencesKey("role")
    }

    val authState: Flow<AuthState> = context.dataStore.data
        .catch {
            if (it is IOException) emit(emptyPreferences()) else throw it
        }
        .map { prefs ->
            AuthState(
                baseUrl = prefs[Keys.BASE_URL] ?: "",
                accessToken = prefs[Keys.ACCESS_TOKEN] ?: "",
                refreshToken = prefs[Keys.REFRESH_TOKEN] ?: "",
                deviceId = prefs[Keys.DEVICE_ID] ?: ""
            )
        }

    suspend fun saveBaseUrl(baseUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = baseUrl.trim().removeSuffix("/")
        }
    }

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        deviceId: String,
        fingerprintHex: String = "",
        role: String = ""
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.DEVICE_ID] = deviceId
            prefs[Keys.FINGERPRINT_HEX] = fingerprintHex
            prefs[Keys.ROLE] = role
        }
    }

    suspend fun updateAccessToken(accessToken: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}