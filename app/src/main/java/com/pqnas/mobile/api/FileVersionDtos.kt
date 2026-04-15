package com.pqnas.mobile.api

data class FileVersionItemDto(
    val version_id: String = "",
    val event_kind: String? = null,
    val created_at: String? = null,
    val created_epoch: Long? = null,
    val actor_fp: String? = null,
    val actor_name_snapshot: String? = null,
    val actor_display: String? = null,
    val bytes: Long? = null,
    val sha256_hex: String? = null,
    val is_deleted_event: Boolean? = null,
    val logical_rel_path: String? = null,
    val scope_type: String? = null,
    val scope_id: String? = null,
    val workspace_id: String? = null
)

data class FileVersionsListResponse(
    val ok: Boolean,
    val path: String? = null,
    val logical_rel_path: String? = null,
    val scope_type: String? = null,
    val scope_id: String? = null,
    val workspace_id: String? = null,
    val versions: List<FileVersionItemDto> = emptyList(),
    val items: List<FileVersionItemDto> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val detail: String? = null
) {
    val entries: List<FileVersionItemDto>
        get() = if (versions.isNotEmpty()) versions else items
}

data class RestoreVersionRequest(
    val path: String,
    val version_id: String
)

data class WorkspaceRestoreVersionRequest(
    val workspace_id: String,
    val path: String,
    val version_id: String
)

data class RestoreVersionResponse(
    val ok: Boolean,
    val path: String? = null,
    val logical_rel_path: String? = null,
    val version_id: String? = null,
    val restored_version_id: String? = null,
    val restored_to_path: String? = null,
    val workspace_id: String? = null,
    val error: String? = null,
    val message: String? = null,
    val detail: String? = null
)