package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.admin.AdminRepository
import com.pqnas.mobile.api.AdminUserDto
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun AdminScreen(
    repository: AdminRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var users by remember { mutableStateOf<List<AdminUserDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Loading admin data...") }
    var approvalsOnly by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("") }
    var reloadKey by remember { mutableIntStateOf(0) }
    var allocateUser by remember { mutableStateOf<AdminUserDto?>(null) }
    var revokeUser by remember { mutableStateOf<AdminUserDto?>(null) }

    fun reload() {
        reloadKey += 1
    }

    LaunchedEffect(reloadKey) {
        loading = true
        status = "Loading admin data..."
        try {
            users = repository.users().sortedBy { it.fingerprint.lowercase(Locale.ROOT) }
            status = "Loaded ${users.size} users"
        } catch (e: Throwable) {
            status = e.message ?: "Failed to load admin data"
        } finally {
            loading = false
        }
    }

    fun runAction(label: String, block: suspend () -> Unit) {
        scope.launch {
            status = "$label..."
            try {
                block()
                status = "$label OK"
                reload()
            } catch (e: Throwable) {
                status = e.message ?: "$label failed"
            }
        }
    }

    val visibleUsers = users.filter { user ->
        val st = (user.status ?: "disabled").lowercase(Locale.ROOT)
        val approvalMatch = !approvalsOnly || st != "enabled"

        val q = filter.trim().lowercase(Locale.ROOT)
        val hay = listOf(
            user.fingerprint,
            user.name,
            user.email,
            user.notes,
            user.role,
            user.status,
            user.storage_state,
            user.pool_id,
            user.pool,
            user.storage_pool_id
        ).joinToString(" ").lowercase(Locale.ROOT)

        approvalMatch && (q.isBlank() || hay.contains(q))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onBack) {
                Text("Back")
            }

            OutlinedButton(onClick = { reload() }) {
                Text("Refresh")
            }
        }

        Text(
            text = "Admin tools",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Approvals, enable/disable, revoke, and user storage allocation.",
            style = MaterialTheme.typography.bodyMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (approvalsOnly) {
                Button(onClick = { approvalsOnly = true }) {
                    Text("Approvals")
                }
            } else {
                OutlinedButton(onClick = { approvalsOnly = true }) {
                    Text("Approvals")
                }
            }

            if (!approvalsOnly) {
                Button(onClick = { approvalsOnly = false }) {
                    Text("All users")
                }
            } else {
                OutlinedButton(onClick = { approvalsOnly = false }) {
                    Text("All users")
                }
            }
        }

        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Filter users") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = status,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (loading) {
            CircularProgressIndicator()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = visibleUsers,
                    key = { it.fingerprint }
                ) { user ->
                    AdminUserCard(
                        user = user,
                        onEnable = {
                            runAction("Enable user") {
                                repository.enable(user.fingerprint)
                            }
                        },
                        onDisable = {
                            runAction("Disable user") {
                                repository.disable(user.fingerprint)
                            }
                        },
                        onRevoke = {
                            revokeUser = user
                        },
                        onAllocate = {
                            allocateUser = user
                        }
                    )
                }
            }
        }
    }

    allocateUser?.let { user ->
        AllocateStorageDialog(
            user = user,
            onCancel = { allocateUser = null },
            onAllocate = { gb ->
                allocateUser = null
                runAction("Allocate storage") {
                    repository.allocateStorageGb(user.fingerprint, gb)
                }
            }
        )
    }

    revokeUser?.let { user ->
        AlertDialog(
            onDismissRequest = { revokeUser = null },
            title = { Text("Revoke user?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(userDisplayName(user))
                    Text(
                        text = user.fingerprint,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Revoked users are hard-blocked from logging in.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        revokeUser = null
                        runAction("Revoke user") {
                            repository.revoke(user.fingerprint)
                        }
                    }
                ) {
                    Text("Revoke")
                }
            },
            dismissButton = {
                TextButton(onClick = { revokeUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminUserCard(
    user: AdminUserDto,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onRevoke: () -> Unit,
    onAllocate: () -> Unit
) {
    val status = user.status ?: "disabled"
    val role = user.role ?: "user"
    val storageState = user.storage_state ?: "unallocated"
    val quota = user.quota_bytes ?: 0L
    val used = user.used_bytes ?: 0L
    val pool = user.pool_id ?: user.pool ?: user.storage_pool_id ?: "default"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = userDisplayName(user),
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Role: $role  •  Status: $status",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Storage: $storageState  •  Pool: $pool",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Used: ${fmtBytes(used)} / Quota: ${if (quota > 0) fmtBytes(quota) else "not allocated"}",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = user.fingerprint,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )

            if (!user.notes.isNullOrBlank()) {
                Text(
                    text = user.notes,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEnable) {
                    Text("Enable")
                }

                OutlinedButton(onClick = onDisable) {
                    Text("Disable")
                }

                OutlinedButton(onClick = onAllocate) {
                    Text("Space")
                }

                OutlinedButton(onClick = onRevoke) {
                    Text("Revoke")
                }
            }
        }
    }
}

@Composable
private fun AllocateStorageDialog(
    user: AdminUserDto,
    onCancel: () -> Unit,
    onAllocate: (Long) -> Unit
) {
    val initialGb = remember(user.fingerprint, user.quota_bytes) {
        bytesToGb(user.quota_bytes ?: 10L * 1024L * 1024L * 1024L).toString()
    }

    var gbText by remember { mutableStateOf(initialGb) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Allocate storage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(userDisplayName(user))
                Text(
                    text = user.fingerprint,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = gbText,
                    onValueChange = { raw ->
                        gbText = raw.filter { it.isDigit() }.take(6)
                    },
                    label = { Text("Quota in GB") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "This mobile v1 uses the default storage pool.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val gb = gbText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    onAllocate(gb)
                }
            ) {
                Text("Allocate")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

private fun userDisplayName(user: AdminUserDto): String {
    return user.name
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: user.email
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        ?: "Unnamed user"
}

private fun bytesToGb(bytes: Long): Long {
    if (bytes <= 0L) return 10L
    return (bytes.toDouble() / 1024.0 / 1024.0 / 1024.0)
        .roundToLong()
        .coerceAtLeast(1L)
}

private fun fmtBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"

    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var idx = 0

    while (value >= 1024.0 && idx < units.lastIndex) {
        value /= 1024.0
        idx++
    }

    val digits = if (idx == 0) 0 else 1
    return "%.${digits}f %s".format(Locale.US, value, units[idx])
}
