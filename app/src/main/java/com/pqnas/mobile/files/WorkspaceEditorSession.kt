package com.pqnas.mobile.files

import java.util.UUID

object WorkspaceEditorSession {
    private val stableSessionId: String = UUID.randomUUID().toString()

    fun id(): String = stableSessionId
}