package com.pqnas.mobile.files

sealed interface FileScope {
    data object User : FileScope

    data class Workspace(
        val workspaceId: String,
        val workspaceName: String = "",
        val workspaceRole: String = ""
    ) : FileScope {
        val canWrite: Boolean
            get() = workspaceRole == "owner" || workspaceRole == "editor"
    }
}