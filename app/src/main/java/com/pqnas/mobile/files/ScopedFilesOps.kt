package com.pqnas.mobile.files

import android.content.Context
import okhttp3.RequestBody
import java.io.File
import okhttp3.ResponseBody
import com.pqnas.mobile.api.FileLockInfoDto

class FileOperationLockedException(
    val blockedLocks: Map<String, FileLockInfoDto>
) : IllegalStateException(
    blockedLocks.entries.joinToString(
        separator = "\n",
        prefix = "Locked by another user:\n"
    ) { (path, lock) ->
        val label = lock.locked_by_label ?: lock.locked_by_fp_short ?: "another user"
        "/$path — $label"
    }
)

class ScopedFilesOps(
    private val repo: FilesRepository,
    private val context: Context
) {
    private fun workspaceSessionId(): String =
        WorkspaceEditorSession.id(context)

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
            is FileScope.Workspace -> repo.readWorkspaceText(
                workspaceId = scope.workspaceId,
                path = path,
                sessionId = workspaceSessionId()
            )
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
                sessionId = workspaceSessionId(),
                expectedMtimeEpoch = expectedMtimeEpoch,
                expectedSha256 = expectedSha256
            )
        }

    suspend fun delete(scope: FileScope, path: String) =
        when (scope) {
            FileScope.User -> repo.delete(path)
            is FileScope.Workspace -> repo.deleteWorkspaceFile(scope.workspaceId, path)
        }

    private fun normalizeRel(path: String): String =
        path
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")

    private suspend fun ensureNoOtherUserLocks(scope: FileScope, paths: List<String>) {
        val cleanPaths = paths
            .map { normalizeRel(it) }
            .filter { it.isNotBlank() }
            .distinct()

        if (cleanPaths.isEmpty()) return

        val response = repo.getFileLockStatusBatch(scope, cleanPaths)
        val blocked = response.locks.filterValues { lock ->
            !lock.own_lock
        }

        if (blocked.isNotEmpty()) {
            throw FileOperationLockedException(blocked)
        }
    }

    suspend fun move(scope: FileScope, from: String, to: String) {
        ensureNoOtherUserLocks(scope, listOf(from, to))

        when (scope) {
            FileScope.User -> repo.move(from, to)
            is FileScope.Workspace -> repo.moveWorkspaceFile(scope.workspaceId, from, to)
        }
    }

    suspend fun copy(scope: FileScope, from: String, to: String) {
        ensureNoOtherUserLocks(scope, listOf(from, to))

        when (scope) {
            FileScope.User -> repo.copy(from, to)
            is FileScope.Workspace -> repo.copyWorkspaceFile(scope.workspaceId, from, to)
        }
    }

    suspend fun mkdir(scope: FileScope, path: String) =
        when (scope) {
            FileScope.User -> repo.mkdir(path)
            is FileScope.Workspace -> repo.mkdirWorkspace(scope.workspaceId, path)
        }

    suspend fun getFileNote(scope: FileScope, path: String) =
        repo.getFileNote(scope, path)

    suspend fun saveFileNote(
        scope: FileScope,
        path: String,
        itemKind: String,
        description: String
    ) =
        repo.saveFileNote(scope, path, itemKind, description)

    suspend fun resolveFileNotes(scope: FileScope, paths: List<String>) =
        repo.resolveFileNotes(scope, paths)

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

    suspend fun uploadTempFile(
        scope: FileScope,
        path: String,
        file: File,
        mimeType: String? = null,
        overwrite: Boolean = false,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ) =
        when (scope) {
            FileScope.User -> repo.uploadChunkedFromTempFile(
                path = path,
                file = file,
                mimeType = mimeType,
                overwrite = overwrite,
                onProgress = onProgress,
                isCancelled = isCancelled
            )

            is FileScope.Workspace -> repo.uploadWorkspaceFile(
                workspaceId = scope.workspaceId,
                path = path,
                body = tempFileRequestBody(
                    file = file,
                    mimeType = mimeType,
                    onProgress = onProgress
                ),
                overwrite = overwrite
            )
        }

    suspend fun listVersions(scope: FileScope, path: String) =
        when (scope) {
            FileScope.User -> repo.listFileVersions(path)
            is FileScope.Workspace -> repo.listWorkspaceFileVersions(scope.workspaceId, path)
        }

    suspend fun restoreVersion(
        scope: FileScope,
        path: String,
        versionId: String
    ) =
        when (scope) {
            FileScope.User -> repo.restoreFileVersion(path, versionId)
            is FileScope.Workspace -> repo.restoreWorkspaceFileVersion(scope.workspaceId, path, versionId)
        }

    suspend fun acquireEditLease(scope: FileScope, path: String) {
        if (scope is FileScope.Workspace && scope.canWrite) {
            repo.acquireWorkspaceEditLease(
                workspaceId = scope.workspaceId,
                path = path,
                sessionId = workspaceSessionId(),
                leaseSeconds = 60L
            )
        }
    }

    suspend fun refreshEditLease(scope: FileScope, path: String) {
        if (scope is FileScope.Workspace && scope.canWrite) {
            repo.refreshWorkspaceEditLease(
                workspaceId = scope.workspaceId,
                path = path,
                sessionId = workspaceSessionId(),
                leaseSeconds = 60L
            )
        }
    }

    suspend fun releaseEditLease(scope: FileScope, path: String) {
        if (scope is FileScope.Workspace && scope.canWrite) {
            repo.releaseWorkspaceEditLease(
                workspaceId = scope.workspaceId,
                path = path,
                sessionId = workspaceSessionId()
            )
        }
    }
}