package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.api.DropZoneInfo
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

private val DzBg = Color(0xFF070A10)
private val DzPanel = Color(0xFF15161D)
private val DzPanelSoft = Color(0xFF1E2028)
private val DzPanelLine = Color(0xFF3B3F4A)
private val DzOrange = Color(0xFFFF9F1A)
private val DzOrangeSoft = Color(0xFFFFC15A)
private val DzText = Color(0xFFF4F4F6)
private val DzMuted = Color(0xFFB5B7C3)
private val DzBad = Color(0xFFFF6B6B)
private val DzGood = Color(0xFF7DE38B)

@Composable
fun DropZoneScreen(
    zones: List<DropZoneInfo>,
    loading: Boolean,
    creating: Boolean,
    status: String,
    latestUrl: String,
    name: String,
    destination: String,
    password: String,
    onNameChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onCopyLatest: () -> Unit,
    onDisable: (String) -> Unit,
    onClose: () -> Unit
) {
    var disableCandidate by remember { mutableStateOf<DropZoneInfo?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DzBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DzBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            DropZoneHeader(onClose = onClose)

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    DropZoneCreateCard(
                        name = name,
                        destination = destination,
                        password = password,
                        creating = creating,
                        onNameChange = onNameChange,
                        onDestinationChange = onDestinationChange,
                        onPasswordChange = onPasswordChange,
                        onCreate = onCreate
                    )
                }

                if (latestUrl.isNotBlank()) {
                    item {
                        DropZoneLatestLinkCard(
                            latestUrl = latestUrl,
                            onCopyLatest = onCopyLatest
                        )
                    }
                }

                if (status.isNotBlank()) {
                    item {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                status.contains("created", ignoreCase = true) ||
                                        status.contains("copied", ignoreCase = true) ||
                                        status.contains("disabled", ignoreCase = true) ->
                                    DzGood
                                status.contains("failed", ignoreCase = true) ||
                                        status.contains("could not", ignoreCase = true) ||
                                        status.contains("denied", ignoreCase = true) ->
                                    DzBad
                                else -> DzMuted
                            }
                        )
                    }
                }

                item {
                    DropZoneExistingHeader(
                        loading = loading,
                        onRefresh = onRefresh
                    )
                }

                if (loading) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = DzOrange,
                            trackColor = DzPanelSoft
                        )
                    }
                }

                items(
                    items = zones,
                    key = { it.id }
                ) { zone ->
                    DropZoneExistingCard(
                        zone = zone,
                        onDisable = {
                            disableCandidate = zone
                        }
                    )
                }

                if (!loading && zones.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = DzPanel.copy(alpha = 0.72f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                DzPanelLine.copy(alpha = 0.75f)
                            )
                        ) {
                            Text(
                                text = "No Drop Zones yet. Create one above to receive files from outsiders.",
                                modifier = Modifier.padding(18.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = DzMuted
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
    }

    disableCandidate?.let { zone ->
        AlertDialog(
            onDismissRequest = { disableCandidate = null },
            containerColor = DzPanel,
            titleContentColor = DzText,
            textContentColor = DzMuted,
            title = { Text("Disable Drop Zone?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(zone.name.ifBlank { "Drop Zone" })
                    Text("This will stop the public upload link from accepting more files.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        disableCandidate = null
                        onDisable(zone.id)
                    }
                ) {
                    Text("Disable", color = DzBad)
                }
            },
            dismissButton = {
                TextButton(onClick = { disableCandidate = null }) {
                    Text("Cancel", color = DzMuted)
                }
            }
        )
    }
}

@Composable
private fun DropZoneHeader(
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = DzText
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "DNA-NEXUS SERVER",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = DzOrange
            )

            Text(
                text = "Drop Zone",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = DzText
            )

            Text(
                text = "Secure one-way upload links for outsiders.",
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )
        }
    }
}

@Composable
private fun DropZoneCreateCard(
    name: String,
    destination: String,
    password: String,
    creating: Boolean,
    onNameChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCreate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DzPanel
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, DzPanelLine)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Create upload link",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DzText
            )

            Text(
                text = "Uploaders can send files only. They cannot browse, download, rename, or delete anything.",
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )

            DzTextField(
                value = name,
                onValueChange = onNameChange,
                label = "Name",
                placeholder = "Drop Zone"
            )

            DzTextField(
                value = destination,
                onValueChange = onDestinationChange,
                label = "Destination folder",
                placeholder = "Incoming/Drop Zones/Drop Zone"
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Password, optional") },
                placeholder = { Text("Leave empty for no password") },
                visualTransformation = PasswordVisualTransformation(),
                colors = dzTextFieldColors()
            )

            Button(
                onClick = onCreate,
                enabled = !creating,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DzOrange,
                    contentColor = Color.Black,
                    disabledContainerColor = DzPanelSoft,
                    disabledContentColor = DzMuted
                )
            ) {
                Text(if (creating) "Creating..." else "Create Drop Zone")
            }
        }
    }
}

@Composable
private fun DropZoneLatestLinkCard(
    latestUrl: String,
    onCopyLatest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DzPanelSoft
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, DzOrange.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "New public upload link",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = DzOrangeSoft
            )

            Text(
                text = latestUrl,
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )

            Button(
                onClick = onCopyLatest,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DzPanel,
                    contentColor = DzText
                )
            ) {
                Text("Copy link")
            }
        }
    }
}

@Composable
private fun DropZoneExistingHeader(
    loading: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DzPanel.copy(alpha = 0.55f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, DzPanelLine.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Existing Drop Zones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DzText
                )

                TextButton(
                    onClick = onRefresh,
                    enabled = !loading,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = DzOrangeSoft,
                        disabledContentColor = DzMuted
                    )
                ) {
                    Text(if (loading) "Loading..." else "Refresh")
                }
            }

            Text(
                text = "For security, public URLs are shown only immediately after creation.",
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )
        }
    }
}

@Composable
private fun DropZoneExistingCard(
    zone: DropZoneInfo,
    onDisable: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DzPanel
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, DzPanelLine)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = zone.name.ifBlank { "Drop Zone" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DzText
                    )

                    Text(
                        text = zone.destination_path.ifBlank { "No destination" },
                        style = MaterialTheme.typography.bodySmall,
                        color = DzMuted
                    )
                }

                Text(
                    text = if (zone.disabled) "Disabled" else "Active",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (zone.disabled) DzMuted else DzGood
                )
            }

            Text(
                text = buildString {
                    append(zone.upload_count)
                    append(" uploads")
                    if (zone.bytes_uploaded > 0L) {
                        append(" • ")
                        append(formatDzBytes(zone.bytes_uploaded))
                    }
                    if (zone.password_required) {
                        append(" • password")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = DzMuted
            )

            HorizontalDivider(color = DzPanelLine.copy(alpha = 0.8f))

            if (!zone.disabled) {
                TextButton(
                    onClick = onDisable,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = DzOrangeSoft
                    )
                ) {
                    Text("Disable")
                }
            }
        }
    }
}

@Composable
private fun DzTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        colors = dzTextFieldColors()
    )
}

@Composable
private fun dzTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = DzText,
        unfocusedTextColor = DzText,
        focusedContainerColor = DzBg.copy(alpha = 0.35f),
        unfocusedContainerColor = DzBg.copy(alpha = 0.35f),
        focusedBorderColor = DzOrange,
        unfocusedBorderColor = DzPanelLine,
        focusedLabelColor = DzOrange,
        unfocusedLabelColor = DzMuted,
        cursorColor = DzOrange,
        focusedPlaceholderColor = DzMuted,
        unfocusedPlaceholderColor = DzMuted
    )

private fun formatDzBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups - 1])
}
