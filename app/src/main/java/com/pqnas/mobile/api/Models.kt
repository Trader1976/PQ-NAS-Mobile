package com.pqnas.mobile.api

data class V5SessionResponse(
    val ok: Boolean,
    val sid: String,
    val st: String,
    val k: String,
    val iat: Long,
    val exp: Long,
    val qr_svg: String
)

data class V5StatusResponse(
    val ok: Boolean,
    val approved: Boolean? = null,
    val pending: Boolean? = null,
    val reason: String? = null,
    val expires_at: Long? = null,
    val k: String? = null
)

data class ConsumeAppRequest(
    val k: String,
    val device_name: String,
    val platform: String,
    val app_version: String
)

data class ConsumeAppResponse(
    val ok: Boolean,
    val access_token: String,
    val refresh_token: String,
    val device_id: String,
    val expires_in: Long,
    val refresh_expires_in: Long,
    val fingerprint_hex: String,
    val role: String,
    val token_type: String
)

data class RefreshTokenRequest(
    val refresh_token: String,
    val device_id: String
)

data class RefreshTokenResponse(
    val ok: Boolean,
    val access_token: String,
    val device_id: String,
    val expires_in: Long,
    val fingerprint_hex: String,
    val role: String,
    val token_type: String
)

data class FileItemDto(
    val name: String,
    val type: String,
    val size_bytes: Long? = null,
    val mtime_unix: Long? = null
)

data class FilesListResponse(
    val ok: Boolean,
    val path: String,
    val items: List<FileItemDto>,
    val fingerprint_hex: String? = null
)

data class PairConsumeRequest(
    val pair_token: String,
    val device_name: String,
    val platform: String,
    val app_version: String
)

data class PairConsumeResponse(
    val ok: Boolean,
    val token_type: String,
    val access_token: String,
    val expires_in: Long,
    val refresh_token: String,
    val refresh_expires_in: Long,
    val device_id: String,
    val fingerprint_hex: String,
    val role: String
)