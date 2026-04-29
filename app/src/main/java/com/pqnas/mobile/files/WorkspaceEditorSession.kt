package com.pqnas.mobile.files

import android.content.Context
import com.pqnas.mobile.auth.EncryptedAuthValue
import java.util.UUID

object WorkspaceEditorSession {
    private const val PREFS_NAME = "workspace_editor_session"
    private const val KEY_SESSION_ID = "session_id"

    @Volatile
    private var cachedSessionId: String? = null

    fun id(context: Context): String {
        cachedSessionId?.let { return it }

        synchronized(this) {
            cachedSessionId?.let { return it }

            val prefs = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

            val rawExisting = prefs.getString(KEY_SESSION_ID, null).orEmpty()
            val existing = EncryptedAuthValue.decryptOrLegacy(rawExisting)
                .takeIf { it.isNotBlank() }

            if (rawExisting.isNotBlank() && !EncryptedAuthValue.isEncrypted(rawExisting)) {
                prefs.edit()
                    .putString(KEY_SESSION_ID, EncryptedAuthValue.encrypt(rawExisting))
                    .apply()
            } else if (EncryptedAuthValue.needsUpgrade(rawExisting)) {
                val plain = EncryptedAuthValue.decryptOrLegacy(rawExisting)
                if (plain.isNotBlank()) {
                    prefs.edit()
                        .putString(KEY_SESSION_ID, EncryptedAuthValue.encrypt(plain))
                        .apply()
                }
            }

            val sessionId = existing ?: UUID.randomUUID().toString().also { generated ->
                prefs.edit()
                    .putString(KEY_SESSION_ID, EncryptedAuthValue.encrypt(generated))
                    .apply()
            }

            cachedSessionId = sessionId
            return sessionId
        }
    }

    fun migrateIfPresent(context: Context) {
        synchronized(this) {
            val prefs = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

            val rawExisting = prefs.getString(KEY_SESSION_ID, null).orEmpty()
            if (rawExisting.isBlank()) return

            if (!EncryptedAuthValue.isEncrypted(rawExisting)) {
                prefs.edit()
                    .putString(KEY_SESSION_ID, EncryptedAuthValue.encrypt(rawExisting))
                    .apply()
                return
            }

            if (EncryptedAuthValue.needsUpgrade(rawExisting)) {
                val plain = EncryptedAuthValue.decryptOrLegacy(rawExisting)
                if (plain.isNotBlank()) {
                    prefs.edit()
                        .putString(KEY_SESSION_ID, EncryptedAuthValue.encrypt(plain))
                        .apply()
                }
            }
        }
    }

    fun clear(context: Context) {
        synchronized(this) {
            cachedSessionId = null
            context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            ).edit().clear().apply()
        }
    }
}
