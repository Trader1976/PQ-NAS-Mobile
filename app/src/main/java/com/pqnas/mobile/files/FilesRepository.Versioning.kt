package com.pqnas.mobile.files

import com.pqnas.mobile.api.RestoreVersionRequest
import com.pqnas.mobile.api.WorkspaceRestoreVersionRequest
import retrofit2.HttpException

private fun throwVersionApiBodyAwareError(e: HttpException, fallback: String): Nothing {
    throwSafeApiError(e, fallback)
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