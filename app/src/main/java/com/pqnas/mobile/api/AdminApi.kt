package com.pqnas.mobile.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

data class AdminUsersResponse(
    val ok: Boolean = false,
    val users: List<AdminUserDto> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

data class AdminUserDto(
    val fingerprint: String = "",
    val name: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val role: String? = null,
    val status: String? = null,
    val added_at: String? = null,
    val last_seen: String? = null,
    val storage_state: String? = null,
    val quota_bytes: Long? = null,
    val used_bytes: Long? = null,
    val storage_used_bytes: Long? = null,
    val pool_id: String? = null,
    val pool: String? = null,
    val storage_pool_id: String? = null
)

data class AdminOkResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

data class AdminFingerprintRequest(
    val fingerprint: String
)

data class AdminStatusRequest(
    val fingerprint: String,
    val status: String
)

data class AdminAllocateStorageRequest(
    val fingerprint: String,
    val quota_bytes: Long,
    val pool_id: String = "default",
    val force: Boolean = false
)


data class AdminUserStorageRequest(
    val fingerprint: String,
    val quota_gb: Long,
    val force: Boolean = true,
    val pool_id: String = "default"
)

interface AdminApi {
    @GET("/api/v4/admin/users")
    suspend fun listUsers(): AdminUsersResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/admin/users/enable")
    suspend fun enableUser(
        @Body request: AdminFingerprintRequest
    ): AdminOkResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/admin/users/status")
    suspend fun setUserStatus(
        @Body request: AdminStatusRequest
    ): AdminOkResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/admin/users/storage")
    suspend fun allocateStorage(
        @Body request: AdminUserStorageRequest
    ): AdminOkResponse

    // Fallback aliases. The repository tries these only if the primary route fails.
    @Headers("Content-Type: application/json")
    @POST("/api/v4/admin/users/storage/allocate")
    suspend fun allocateStorageAlias1(
        @Body request: AdminUserStorageRequest
    ): AdminOkResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/admin/users/quota")
    suspend fun allocateStorageAlias2(
        @Body request: AdminAllocateStorageRequest
    ): AdminOkResponse
}
