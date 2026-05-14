package com.pqnas.mobile.admin

import com.pqnas.mobile.api.AdminAllocateStorageRequest
import com.pqnas.mobile.api.AdminApi
import com.pqnas.mobile.api.AdminFingerprintRequest
import com.pqnas.mobile.api.AdminOkResponse
import com.pqnas.mobile.api.AdminStatusRequest
import com.pqnas.mobile.api.AdminUserDto
import com.pqnas.mobile.api.AdminUserStorageRequest
import kotlinx.coroutines.CancellationException

class AdminRepository(
    private val api: AdminApi
) {
    suspend fun users(): List<AdminUserDto> {
        val r = api.listUsers()
        if (!r.ok) throw IllegalStateException(r.message ?: r.error ?: "Failed to load admin users")
        return r.users
    }

    suspend fun enable(fingerprint: String) {
        expectOk("Enable user") {
            api.enableUser(AdminFingerprintRequest(fingerprint))
        }
    }

    suspend fun disable(fingerprint: String) {
        expectOk("Disable user") {
            api.setUserStatus(AdminStatusRequest(fingerprint, "disabled"))
        }
    }

    suspend fun revoke(fingerprint: String) {
        expectOk("Revoke user") {
            api.setUserStatus(AdminStatusRequest(fingerprint, "revoked"))
        }
    }

    suspend fun allocateStorageGb(
        fingerprint: String,
        gb: Long,
        poolId: String = "default"
    ) {
        val safeGb = gb.coerceAtLeast(0L)

        val r = api.allocateStorage(
            AdminUserStorageRequest(
                fingerprint = fingerprint,
                quota_gb = safeGb,
                force = true,
                pool_id = poolId.ifBlank { "default" }
            )
        )

        if (!r.ok) {
            throw IllegalStateException(r.message ?: r.error ?: "Allocate storage failed")
        }
    }

    private suspend fun expectOk(
        label: String,
        call: suspend () -> AdminOkResponse
    ) {
        val r = call()
        if (!r.ok) throw IllegalStateException(r.message ?: r.error ?: "$label failed")
    }
}
