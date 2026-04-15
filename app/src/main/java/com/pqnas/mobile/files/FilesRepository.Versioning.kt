package com.pqnas.mobile.files

import com.pqnas.mobile.api.RestoreVersionRequest
import com.pqnas.mobile.api.WorkspaceRestoreVersionRequest
import org.json.JSONObject
import retrofit2.HttpException

private fun throwVersionApiBodyAwareError(e: HttpException, fallback: String): Nothing {
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

suspend fun FilesRepository.listFileVersions(
    path: String
) =
    try {
        createFilesApiInternal().listFileVersions(path)
    } catch (e: HttpException) {
        throwVersionApiBodyAwareError(e, "list file versions failed")
    }

suspend fun FilesRepository.restoreFileVersion(
    path: String,
    versionId: String
) =
    try {
        createFilesApiInternal().restoreFileVersion(
            RestoreVersionRequest(
                path = path,
                version_id = versionId
            )
        )
    } catch (e: HttpException) {
        throwVersionApiBodyAwareError(e, "restore file version failed")
    }

suspend fun FilesRepository.listWorkspaceFileVersions(
    workspaceId: String,
    path: String
) =
    try {
        createWorkspaceFilesApiInternal().listWorkspaceFileVersions(
            workspaceId = workspaceId,
            path = path
        )
    } catch (e: HttpException) {
        throwVersionApiBodyAwareError(e, "list workspace file versions failed")
    }

suspend fun FilesRepository.restoreWorkspaceFileVersion(
    workspaceId: String,
    path: String,
    versionId: String
) =
    try {
        createWorkspaceFilesApiInternal().restoreWorkspaceFileVersion(
            WorkspaceRestoreVersionRequest(
                workspace_id = workspaceId,
                path = path,
                version_id = versionId
            )
        )
    } catch (e: HttpException) {
        throwVersionApiBodyAwareError(e, "restore workspace file version failed")
    }