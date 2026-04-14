package com.pqnas.mobile.files

import okhttp3.RequestBody
import okhttp3.ResponseBody

class ScopedFilesOps(
    private val repo: FilesRepository
) {
    fun canWrite(scope: FileScope): Boolean {
        return when (scope) {
            FileScope.User -> true
            is FileScope.Workspace -> scope.canWrite
        }
    }

    fun workspaceIdOrNull(scope: FileScope): String? {
        return (scope as? FileScope.Workspace)?.workspaceId
    }

    suspend fun list(scope: FileScope, path: String? = null) =
        when (scope) {
            FileScope.User -> repo.list(path)
            is FileScope.Workspace -> repo.listWorkspaceFiles(scope.workspaceId, path)
        }

    suspend fun getShares(scope: FileScope) =
        when (scope) {
            FileScope.User -> repo.getShares()
            is FileScope.Workspace -> repo.getWorkspaceShares(scope.workspaceId)
        }

    suspend fun createShare(
        scope: FileScope,
        path: String,
        type: String,
        expiresSec: Long? = 86400L
    ) =
        when (scope) {
            FileScope.User -> repo.createShare(path, type, expiresSec)
            is FileScope.Workspace -> repo.createWorkspaceShare(scope.workspaceId, path, type, expiresSec)
        }

    suspend fun download(scope: FileScope, path: String): ResponseBody =
        when (scope) {
            FileScope.User -> repo.download(path)
            is FileScope.Workspace -> repo.downloadWorkspaceFile(scope.workspaceId, path)
        }

    suspend fun readText(scope: FileScope, path: String) =
        when (scope) {
            FileScope.User -> repo.readText(path)
            is FileScope.Workspace -> repo.readWorkspaceText(scope.workspaceId, path, WorkspaceEditorSession.id())
        }

    suspend fun writeText(
        scope: FileScope,
        path: String,
        text: String,
        expectedMtimeEpoch: Long? = null,
        expectedSha256: String? = null
    ) =
        when (scope) {
            FileScope.User -> repo.writeText(path, text, expectedMtimeEpoch, expectedSha256)
            is FileScope.Workspace -> repo.writeWorkspaceText(
                workspaceId = scope.workspaceId,
                path = path,
                text = text,
                sessionId = WorkspaceEditorSession.id(),
                expectedMtimeEpoch = expectedMtimeEpoch,
                expectedSha256 = expectedSha256
            )
        }

    suspend fun delete(scope: FileScope, path: String) =
        when (scope) {
            FileScope.User -> repo.delete(path)
            is FileScope.Workspace -> repo.deleteWorkspaceFile(scope.workspaceId, path)
        }

    suspend fun move(scope: FileScope, from: String, to: String) =
        when (scope) {
            FileScope.User -> repo.move(from, to)
            is FileScope.Workspace -> repo.moveWorkspaceFile(scope.workspaceId, from, to)
        }

    suspend fun mkdir(scope: FileScope, path: String) =
        when (scope) {
            FileScope.User -> repo.mkdir(path)
            is FileScope.Workspace -> repo.mkdirWorkspace(scope.workspaceId, path)
        }

    suspend fun upload(
        scope: FileScope,
        path: String,
        body: RequestBody,
        overwrite: Boolean = false
    ) =
        when (scope) {
            FileScope.User -> repo.upload(path, body, overwrite)
            is FileScope.Workspace -> repo.uploadWorkspaceFile(scope.workspaceId, path, body, overwrite)
        }

    suspend fun acquireEditLease(scope: FileScope, path: String) {
        if (scope is FileScope.Workspace && scope.canWrite) {
            repo.acquireWorkspaceEditLease(
                workspaceId = scope.workspaceId,
                path = path,
                sessionId = WorkspaceEditorSession.id(),
                leaseSeconds = 60L
            )
        }
    }

    suspend fun refreshEditLease(scope: FileScope, path: String) {
        if (scope is FileScope.Workspace && scope.canWrite) {
            repo.refreshWorkspaceEditLease(
                workspaceId = scope.workspaceId,
                path = path,
                sessionId = WorkspaceEditorSession.id(),
                leaseSeconds = 60L
            )
        }
    }

    suspend fun releaseEditLease(scope: FileScope, path: String) {
        if (scope is FileScope.Workspace && scope.canWrite) {
            repo.releaseWorkspaceEditLease(
                workspaceId = scope.workspaceId,
                path = path,
                sessionId = WorkspaceEditorSession.id()
            )
        }
    }
}