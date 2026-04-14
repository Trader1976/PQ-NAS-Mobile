package com.pqnas.mobile.api

data class WorkspaceListItemDto(
    val workspace_id: String,
    val name: String = "",
    val role: String = "",
    val status: String = "",
    val storage_state: String? = null,
    val quota_bytes: Long? = null,
    val storage_used_bytes: Long? = null
)

data class WorkspacesResponse(
    val ok: Boolean,
    val actor_fp: String? = null,
    val workspaces: List<WorkspaceListItemDto> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

data class WorkspaceEditLeaseDto(
    val workspace_id: String? = null,
    val path: String? = null,
    val holder_fp: String? = null,
    val mode: String? = null,
    val acquired_epoch: Long? = null,
    val last_seen_epoch: Long? = null,
    val expires_epoch: Long? = null,
    val acquired_at: String? = null,
    val last_seen_at: String? = null,
    val expires_at: String? = null
)

data class WorkspaceEditFlagsDto(
    val can_edit: Boolean? = null,
    val read_only: Boolean? = null,
    val locked_by_other: Boolean? = null
)

data class WorkspaceEditLeaseRequest(
    val workspace_id: String,
    val path: String,
    val session_id: String,
    val lease_seconds: Long = 60L
)

data class WorkspaceEditLeaseReleaseRequest(
    val workspace_id: String,
    val path: String,
    val session_id: String
)

data class WorkspaceEditLeaseResponse(
    val ok: Boolean,
    val workspace_id: String? = null,
    val path: String? = null,
    val lease: WorkspaceEditLeaseDto? = null,
    val edit: WorkspaceEditFlagsDto? = null,
    val error: String? = null,
    val message: String? = null
)

data class WorkspaceEditLeaseReleaseResponse(
    val ok: Boolean,
    val workspace_id: String? = null,
    val path: String? = null,
    val released: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

data class WorkspaceWriteTextRequest(
    val workspace_id: String,
    val session_id: String,
    val path: String,
    val text: String,
    val expected_mtime_epoch: Long? = null,
    val expected_sha256: String? = null
)

data class WorkspaceCreateShareRequest(
    val path: String,
    val type: String,
    val expires_sec: Long? = 86400L,
    val workspace_id: String
)