package com.pqnas.mobile.auth

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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

        val all: List<Preferences.Key<String>> = listOf(
            BASE_URL,
            ACCESS_TOKEN,
            REFRESH_TOKEN,
            DEVICE_ID,
            FINGERPRINT_HEX,
            ROLE
        )
    }

    val authState: Flow<AuthState> = context.dataStore.data
        .catch {
            if (it is IOException) emit(emptyPreferences()) else throw it
        }
        .map { prefs ->
            AuthState(
                baseUrl = decryptPref(prefs[Keys.BASE_URL]),
                accessToken = decryptPref(prefs[Keys.ACCESS_TOKEN]),
                refreshToken = decryptPref(prefs[Keys.REFRESH_TOKEN]),
                deviceId = decryptPref(prefs[Keys.DEVICE_ID]),
                fingerprintHex = decryptPref(prefs[Keys.FINGERPRINT_HEX]),
                role = decryptPref(prefs[Keys.ROLE])
            )
        }

    suspend fun getAuthStateOnce(): AuthState {
        migrateLegacyPlaintextIfNeeded()
        return authState.first()
    }

    suspend fun saveBaseUrl(baseUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = encryptPref(baseUrl.trim().removeSuffix("/"))
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
            prefs[Keys.ACCESS_TOKEN] = encryptPref(accessToken)
            prefs[Keys.REFRESH_TOKEN] = encryptPref(refreshToken)
            prefs[Keys.DEVICE_ID] = encryptPref(deviceId)
            prefs[Keys.FINGERPRINT_HEX] = encryptPref(fingerprintHex)
            prefs[Keys.ROLE] = encryptPref(role)
        }
    }

    suspend fun updateAfterRefresh(
        accessToken: String,
        deviceId: String = "",
        fingerprintHex: String = "",
        role: String = ""
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = encryptPref(accessToken)
            if (deviceId.isNotBlank()) prefs[Keys.DEVICE_ID] = encryptPref(deviceId)
            if (fingerprintHex.isNotBlank()) prefs[Keys.FINGERPRINT_HEX] = encryptPref(fingerprintHex)
            if (role.isNotBlank()) prefs[Keys.ROLE] = encryptPref(role)
        }
    }

    suspend fun updateAccessToken(accessToken: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = encryptPref(accessToken)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    private suspend fun migrateLegacyPlaintextIfNeeded() {
        context.dataStore.edit { prefs ->
            for (key in Keys.all) {
                val raw = prefs[key].orEmpty()
                if (raw.isNotBlank() && !EncryptedAuthValue.isEncrypted(raw)) {
                    prefs[key] = encryptPref(raw)
                }
            }
        }
    }

    private fun encryptPref(value: String): String =
        EncryptedAuthValue.encrypt(value)

    private fun decryptPref(value: String?): String =
        EncryptedAuthValue.decryptOrLegacy(value.orEmpty())
}