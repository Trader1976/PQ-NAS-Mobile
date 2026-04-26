package com.pqnas.mobile.files

import android.content.Context
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

            val existing = prefs.getString(KEY_SESSION_ID, null)
                ?.takeIf { it.isNotBlank() }

            val sessionId = existing ?: UUID.randomUUID().toString().also { generated ->
                prefs.edit()
                    .putString(KEY_SESSION_ID, generated)
                    .apply()
            }

            cachedSessionId = sessionId
            return sessionId
        }
    }
}