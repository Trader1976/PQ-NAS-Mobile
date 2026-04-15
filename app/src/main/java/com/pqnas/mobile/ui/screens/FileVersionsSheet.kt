package com.pqnas.mobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.api.FileVersionItemDto
import com.pqnas.mobile.files.FileScope
import com.pqnas.mobile.files.FilesRepository
import com.pqnas.mobile.files.ScopedFilesOps
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileVersionsSheet(
    filesRepository: FilesRepository,
    fileScope: FileScope,
    relPath: String,
    displayName: String,
    onDismiss: () -> Unit,
    onRestored: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scopedOps = remember(filesRepository) { ScopedFilesOps(filesRepository) }

    var versions by remember(relPath, fileScope) { mutableStateOf<List<FileVersionItemDto>>(emptyList()) }
    var status by remember(relPath, fileScope) { mutableStateOf("Loading versions...") }
    var closeAfterRestore by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<FileVersionItemDto?>(null) }
    var restoringVersionId by remember { mutableStateOf<String?>(null) }

    val canRestore = scopedOps.canWrite(fileScope)
    val scopeLabel = when (fileScope) {
        FileScope.User -> "Personal files"
        is FileScope.Workspace -> "Workspace: ${fileScope.workspaceName.ifBlank { fileScope.workspaceId }}"
    }

    fun loadVersions() {
        scope.launch {
            status = "Loading versions..."
            try {
                val resp = scopedOps.listVersions(fileScope, relPath)
                versions = resp.entries.sortedByDescending { it.created_epoch ?: 0L }
                status = if (versions.isEmpty()) "No preserved versions found." else ""
            } catch (e: Exception) {
                status = friendlyVersionsMessage("Load versions", e)
            }
        }
    }

    LaunchedEffect(relPath, fileScope) {
        loadVersions()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Versions",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = scopeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = relPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { closeAfterRestore = !closeAfterRestore },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = closeAfterRestore,
                    onCheckedChange = { closeAfterRestore = it }
                )
                Text(
                    text = "Close after restore",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (status.isNotBlank()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.contains("failed", ignoreCase = true) ||
                        status.contains("denied", ignoreCase = true) ||
                        status.contains("not found", ignoreCase = true)
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (versions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = versions,
                        key = { item ->
                            item.version_id.ifBlank {
                                "${item.created_epoch ?: 0L}:${item.sha256_hex.orEmpty()}"
                            }
                        }
                    ) { item ->
                        VersionRow(
                            item = item,
                            canRestore = canRestore,
                            isRestoring = restoringVersionId == item.version_id,
                            onCopySha = { sha ->
                                if (copyToClipboard(context, "sha256", sha)) {
                                    status = "SHA copied."
                                } else {
                                    status = "Copy failed."
                                }
                            },
                            onRestore = {
                                pendingRestore = item
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }

    pendingRestore?.let { item ->
        AlertDialog(
            onDismissRequest = {
                if (restoringVersionId == null) pendingRestore = null
            },
            title = { Text("Restore version") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restore this preserved version and replace the current live file?")
                    Text(
                        text = versionKindLabel(item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.created_epoch?.let {
                        Text(
                            text = "Created: ${formatVersionTime(item)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = restoringVersionId == null && canRestore,
                    onClick = {
                        scope.launch {
                            restoringVersionId = item.version_id
                            try {
                                scopedOps.restoreVersion(
                                    scope = fileScope,
                                    path = relPath,
                                    versionId = item.version_id
                                )

                                val msg = "Version restored. Current file replaced successfully."
                                status = msg
                                onRestored(msg)

                                pendingRestore = null
                                restoringVersionId = null

                                if (closeAfterRestore) {
                                    onDismiss()
                                } else {
                                    loadVersions()
                                }
                            } catch (e: Exception) {
                                restoringVersionId = null
                                status = friendlyVersionsMessage("Restore version", e)
                            }
                        }
                    }
                ) {
                    Text(if (restoringVersionId == item.version_id) "Restoring..." else "Restore")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = restoringVersionId == null,
                    onClick = { pendingRestore = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VersionRow(
    item: FileVersionItemDto,
    canRestore: Boolean,
    isRestoring: Boolean,
    onCopySha: (String) -> Unit,
    onRestore: () -> Unit
) {
    val actor = item.actor_display
        ?.takeIf { it.isNotBlank() }
        ?: item.actor_name_snapshot?.takeIf { it.isNotBlank() }
        ?: item.actor_fp?.takeIf { it.isNotBlank() }
        ?: "-"

    val sha = item.sha256_hex.orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = versionKindLabel(item),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Date: ${formatVersionTime(item)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Actor: $actor",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Size: ${formatVersionBytes(item.bytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (sha.isNotBlank()) {
                Text(
                    text = "SHA-256: ${shortSha(sha)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!item.version_id.isNullOrBlank()) {
                Text(
                    text = "Version ID: ${item.version_id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (sha.isNotBlank()) {
                    TextButton(
                        onClick = { onCopySha(sha) }
                    ) {
                        Text("Copy SHA")
                    }
                }

                TextButton(
                    enabled = canRestore && !isRestoring,
                    onClick = onRestore
                ) {
                    Text(
                        when {
                            isRestoring -> "Restoring..."
                            !canRestore -> "Read only"
                            else -> "Restore"
                        }
                    )
                }
            }
        }
    }
}

private fun versionKindLabel(item: FileVersionItemDto): String {
    if (item.is_deleted_event == true) {
        return "Deleted file snapshot"
    }

    val raw = item.event_kind.orEmpty().lowercase(Locale.getDefault())
    return when {
        raw.contains("overwrite_preserve") -> "Before overwrite"
        raw.contains("delete_preserve") -> "Deleted file snapshot"
        raw.isBlank() -> "Preserved version"
        else -> raw
            .replace('_', ' ')
            .replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            }
    }
}

private fun formatVersionTime(item: FileVersionItemDto): String {
    item.created_at?.takeIf { it.isNotBlank() }?.let { return it }
    val epoch = item.created_epoch ?: return "-"
    val date = Date(epoch * 1000L)
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(date)
}

private fun formatVersionBytes(bytes: Long?): String {
    val v = bytes ?: return "-"
    if (v < 1024) return "$v B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    val digitGroups = (ln(v.toDouble()) / ln(1024.0)).toInt()
    val value = v / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups - 1])
}

private fun shortSha(sha: String): String {
    if (sha.length <= 20) return sha
    return sha.take(12) + "…" + sha.takeLast(8)
}

private fun copyToClipboard(context: Context, label: String, text: String): Boolean {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        true
    } catch (_: Exception) {
        false
    }
}

private fun friendlyVersionsMessage(
    action: String,
    error: Throwable
): String {
    val http = (error as? HttpException)?.code()
        ?: Regex("""\bHTTP\s+(\d{3})\b""")
            .find(error.message.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

    return when (http) {
        400 -> "$action failed: invalid request."
        401 -> "Session expired. Please pair again."
        403 -> "Access denied."
        404 -> "Item or version not found."
        409 -> "$action failed: conflicting file state."
        500 -> "$action failed: server error."
        else -> {
            val msg = error.message?.takeIf { it.isNotBlank() } ?: "unknown error"
            "$action failed: $msg"
        }
    }
}