package com.pqnas.mobile.files

import com.pqnas.mobile.api.WorkspaceCreateShareRequest
import com.pqnas.mobile.api.WorkspaceEditLeaseReleaseRequest
import com.pqnas.mobile.api.WorkspaceEditLeaseRequest
import com.pqnas.mobile.api.WorkspaceWriteTextRequest
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.HttpException

private fun throwApiBodyAwareError(e: HttpException, fallback: String): Nothing {
    val raw = try {
        e.response()?.errorBody()?.string().orEmpty()
    } catch (_: Exception) {
        ""
    }

    val j = try {
        if (raw.isBlank()) null else JSONObject(raw)
    } catch (_: Exception) {
        null
    }

    val error = j?.optString("error").orEmpty()
    val message = j?.optString("message").orEmpty()
    val detail = j?.optString("detail").orEmpty()

    val parts = listOf(
        error.takeIf { it.isNotBlank() },
        message.takeIf { it.isNotBlank() },
        detail.takeIf { it.isNotBlank() },
        "HTTP ${e.code()}"
    )

    throw IllegalStateException(parts.joinToString(": ").ifBlank { fallback })
}

suspend fun FilesRepository.listWorkspaces() =
    try {
        createWorkspaceFilesApiInternal().listWorkspaces()
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "list workspaces failed")
    }

suspend fun FilesRepository.listWorkspaceFiles(
    workspaceId: String,
    path: String? = null
) =
    try {
        createWorkspaceFilesApiInternal().listWorkspaceFiles(workspaceId, path)
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "list workspace files failed")
    }

suspend fun FilesRepository.downloadWorkspaceFile(
    workspaceId: String,
    path: String
): ResponseBody =
    try {
        createWorkspaceFilesApiInternal().downloadWorkspaceFile(workspaceId, path)
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "download workspace file failed")
    }

suspend fun FilesRepository.readWorkspaceText(
    workspaceId: String,
    path: String,
    sessionId: String = WorkspaceEditorSession.id()
) =
    try {
        createWorkspaceFilesApiInternal().readWorkspaceTextFile(workspaceId, path, sessionId)
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "read workspace text failed")
    }

suspend fun FilesRepository.writeWorkspaceText(
    workspaceId: String,
    path: String,
    text: String,
    sessionId: String = WorkspaceEditorSession.id(),
    expectedMtimeEpoch: Long? = null,
    expectedSha256: String? = null
) =
    try {
        createWorkspaceFilesApiInternal().writeWorkspaceTextFile(
            WorkspaceWriteTextRequest(
                workspace_id = workspaceId,
                session_id = sessionId,
                path = path,
                text = text,
                expected_mtime_epoch = expectedMtimeEpoch,
                expected_sha256 = expectedSha256
            )
        )
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "write workspace text failed")
    }

suspend fun FilesRepository.deleteWorkspaceFile(
    workspaceId: String,
    path: String
) =
    try {
        createWorkspaceFilesApiInternal().deleteWorkspaceFile(workspaceId, path)
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "delete workspace file failed")
    }

suspend fun FilesRepository.moveWorkspaceFile(
    workspaceId: String,
    from: String,
    to: String
) =
    try {
        createWorkspaceFilesApiInternal().moveWorkspaceFile(workspaceId, from, to)
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "move workspace file failed")
    }

suspend fun FilesRepository.mkdirWorkspace(
    workspaceId: String,
    path: String
) =
    try {
        createWorkspaceFilesApiInternal().mkdirWorkspace(workspaceId, path)
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "mkdir workspace failed")
    }

suspend fun FilesRepository.uploadWorkspaceFile(
    workspaceId: String,
    path: String,
    body: RequestBody,
    overwrite: Boolean = false
) =
    try {
        createWorkspaceFilesApiInternal().uploadWorkspaceFile(
            workspaceId = workspaceId,
            path = path,
            overwrite = if (overwrite) 1 else 0,
            body = body
        )
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "upload workspace file failed")
    }

suspend fun FilesRepository.getWorkspaceShares(
    workspaceId: String
) =
    try {
        createWorkspaceFilesApiInternal().listWorkspaceShares(workspaceId)
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "list workspace shares failed")
    }

suspend fun FilesRepository.createWorkspaceShare(
    workspaceId: String,
    path: String,
    type: String,
    expiresSec: Long? = 86400L
) =
    try {
        createWorkspaceFilesApiInternal().createWorkspaceShare(
            WorkspaceCreateShareRequest(
                path = path,
                type = if (type == "dir") "dir" else "file",
                expires_sec = expiresSec,
                workspace_id = workspaceId
            )
        )
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "create workspace share failed")
    }

suspend fun FilesRepository.acquireWorkspaceEditLease(
    workspaceId: String,
    path: String,
    sessionId: String = WorkspaceEditorSession.id(),
    leaseSeconds: Long = 60L
) =
    try {
        createWorkspaceFilesApiInternal().acquireWorkspaceEditLease(
            WorkspaceEditLeaseRequest(
                workspace_id = workspaceId,
                path = path,
                session_id = sessionId,
                lease_seconds = leaseSeconds
            )
        )
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "acquire workspace edit lease failed")
    }

suspend fun FilesRepository.refreshWorkspaceEditLease(
    workspaceId: String,
    path: String,
    sessionId: String = WorkspaceEditorSession.id(),
    leaseSeconds: Long = 60L
) =
    try {
        createWorkspaceFilesApiInternal().refreshWorkspaceEditLease(
            WorkspaceEditLeaseRequest(
                workspace_id = workspaceId,
                path = path,
                session_id = sessionId,
                lease_seconds = leaseSeconds
            )
        )
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "refresh workspace edit lease failed")
    }

suspend fun FilesRepository.releaseWorkspaceEditLease(
    workspaceId: String,
    path: String,
    sessionId: String = WorkspaceEditorSession.id()
) =
    try {
        createWorkspaceFilesApiInternal().releaseWorkspaceEditLease(
            WorkspaceEditLeaseReleaseRequest(
                workspace_id = workspaceId,
                path = path,
                session_id = sessionId
            )
        )
    } catch (e: HttpException) {
        throwApiBodyAwareError(e, "release workspace edit lease failed")
    }