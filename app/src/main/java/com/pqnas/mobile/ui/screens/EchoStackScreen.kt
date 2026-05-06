package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.api.EchoStackItemDto
import com.pqnas.mobile.echostack.EchoStackRepository
import com.pqnas.mobile.echostack.echoStackFriendlyMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

private val EchoBg = Color(0xFF060910)
private val EchoPanel = Color(0xFF151820)
private val EchoPanelSoft = Color(0xFF20232C)
private val EchoPanelLine = Color(0xFF3A3F4C)
private val EchoOrange = Color(0xFFFFB02E)
private val EchoOrangeSoft = Color(0xFFFFC15A)
private val EchoText = Color(0xFFF5F7FA)
private val EchoMuted = Color(0xFFB8BDC8)
private val EchoBad = Color(0xFFFF6B6B)
private val EchoGood = Color(0xFF7DE38B)

@Composable
fun EchoStackScreen(
    repository: EchoStackRepository,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var items by remember { mutableStateOf<List<EchoStackItemDto>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("") }
    var newCollection by remember { mutableStateOf("") }
    var newTags by remember { mutableStateOf("") }
    var newNotes by remember { mutableStateOf("") }
    var showAddMetadata by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Loading Echo Stack...") }
    var workingStatusBase by remember { mutableStateOf<String?>(null) }
    var workingDots by remember { mutableStateOf(0) }
    var archivingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<EchoStackItemDto?>(null) }

    fun replaceItem(updated: EchoStackItemDto) {
        items = items.map { existing ->
            if (existing.id == updated.id) updated else existing
        }
    }

    fun loadItems() {
        scope.launch {
            loading = true
            status = "Loading Echo Stack..."

            runCatching {
                repository.listItems(query)
            }.onSuccess { loaded ->
                items = loaded
                status = if (loaded.isEmpty()) {
                    if (query.isBlank()) "No Echo Stack items yet." else "No matching Echo Stack items."
                } else {
                    "Ready."
                }
            }.onFailure { e ->
                status = echoStackFriendlyMessage("Load", e)
            }

            loading = false
        }
    }

    fun createItem() {
        val url = newUrl.trim()
        if (url.isBlank()) {
            status = "Paste a URL first."
            return
        }

        scope.launch {
            creating = true
            status = "Saving Echo Stack item..."

            runCatching {
                repository.createFromUrl(
                    rawUrl = url,
                    title = newTitle,
                    collection = newCollection,
                    tags = newTags,
                    notes = newNotes
                )
            }.onSuccess { created ->
                newUrl = ""
                newTitle = ""
                newCollection = ""
                newTags = ""
                newNotes = ""
                showAddMetadata = false
                items = listOf(created) + items
                status = "Saved."
            }.onFailure { e ->
                status = echoStackFriendlyMessage("Save", e)
            }

            creating = false
        }
    }

    fun setFavorite(item: EchoStackItemDto, favorite: Boolean) {
        scope.launch {
            status = "Updating favorite..."

            runCatching {
                repository.setFavorite(item, favorite)
            }.onSuccess { updated ->
                replaceItem(updated)
                status = "Updated."
            }.onFailure { e ->
                status = echoStackFriendlyMessage("Favorite", e)
            }
        }
    }

    fun setRead(item: EchoStackItemDto, read: Boolean) {
        scope.launch {
            status = "Updating read state..."

            runCatching {
                repository.setReadState(item, read)
            }.onSuccess { updated ->
                replaceItem(updated)
                status = "Updated."
            }.onFailure { e ->
                status = echoStackFriendlyMessage("Read state", e)
            }
        }
    }

    fun archive(item: EchoStackItemDto) {
        scope.launch {
            archivingIds = archivingIds + item.id
            workingStatusBase = "Archiving page snapshot"
            status = ""

            runCatching {
                repository.archive(item.id)
            }.onSuccess { updated ->
                replaceItem(updated)
                workingStatusBase = null
                status = if (updated.archive_status == "archived") "Archived." else "Archive updated."
            }.onFailure { e ->
                workingStatusBase = null
                status = echoStackFriendlyMessage("Archive", e)
                loadItems()
            }

            archivingIds = archivingIds - item.id
        }
    }

    fun deleteItem(item: EchoStackItemDto) {
        scope.launch {
            status = "Deleting..."

            runCatching {
                repository.delete(item.id)
            }.onSuccess {
                items = items.filterNot { it.id == item.id }
                deleteCandidate = null
                status = "Deleted."
            }.onFailure { e ->
                status = echoStackFriendlyMessage("Delete", e)
            }
        }
    }

    LaunchedEffect(workingStatusBase) {
        if (workingStatusBase == null) {
            workingDots = 0
            return@LaunchedEffect
        }

        while (true) {
            delay(360L)
            workingDots = (workingDots + 1) % 4
        }
    }

    LaunchedEffect(Unit) {
        loadItems()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = EchoBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(EchoBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = EchoText
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DNA-NEXUS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EchoOrange
                    )

                    Text(
                        text = "Echo Stack",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = EchoText
                    )

                    Text(
                        text = "Save links, notes, tags, and research trails on your own NAS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EchoMuted
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = EchoPanel
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, EchoPanelLine)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EchoTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = "URL",
                        placeholder = "https://example.com/article"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showAddMetadata = !showAddMetadata },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = EchoOrangeSoft
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (showAddMetadata) "Hide metadata" else "Add metadata")
                        }

                        Button(
                            onClick = { createItem() },
                            enabled = !creating,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EchoPanelSoft,
                                contentColor = EchoText,
                                disabledContainerColor = EchoPanelSoft.copy(alpha = 0.55f),
                                disabledContentColor = EchoMuted
                            )
                        ) {
                            Text(if (creating) "Saving..." else "Save link")
                        }
                    }

                    if (showAddMetadata) {
                        EchoTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = "Optional title",
                            placeholder = "Leave empty to use page title"
                        )

                        EchoTextField(
                            value = newCollection,
                            onValueChange = { newCollection = it },
                            label = "Collection",
                            placeholder = "NAS docs"
                        )

                        EchoTextField(
                            value = newTags,
                            onValueChange = { newTags = it },
                            label = "Tags",
                            placeholder = "nas, crypto, research"
                        )

                        EchoNotesField(
                            value = newNotes,
                            onValueChange = { newNotes = it }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = EchoPanel
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, EchoPanelLine)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EchoTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = "Search",
                        placeholder = "Search links, notes, tags, collections",
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = { loadItems() },
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EchoPanelSoft,
                            contentColor = EchoText,
                            disabledContainerColor = EchoPanelSoft.copy(alpha = 0.55f),
                            disabledContentColor = EchoMuted
                        )
                    ) {
                        Text("Refresh")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val visibleStatus = workingStatusBase?.let { base ->
                base + ".".repeat(workingDots)
            } ?: status

            Text(
                text = visibleStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    workingStatusBase != null ->
                        EchoOrangeSoft
                    visibleStatus == "Ready." ||
                            visibleStatus == "Saved." ||
                            visibleStatus == "Updated." ||
                            visibleStatus == "Archived." ||
                            visibleStatus == "Deleted." ->
                        EchoGood
                    visibleStatus.contains("failed", ignoreCase = true) ||
                            visibleStatus.contains("denied", ignoreCase = true) ||
                            visibleStatus.contains("expired", ignoreCase = true) ||
                            visibleStatus.contains("quota", ignoreCase = true) ->
                        EchoBad
                    else -> EchoMuted
                }
            )

            if (loading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = EchoOrange,
                    trackColor = EchoPanelSoft
                )
            }

            Spacer(Modifier.height(12.dp))

            if (items.isEmpty() && !loading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = EchoPanel.copy(alpha = 0.72f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EchoPanelLine.copy(alpha = 0.75f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (query.isBlank()) {
                                "No links saved yet. Paste a URL above to start your stack."
                            } else {
                                "No matching links."
                            },
                            color = EchoMuted,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        items = items,
                        key = { it.id }
                    ) { item ->
                        EchoStackItemCard(
                            item = item,
                            isArchiving = archivingIds.contains(item.id),
                            archiveDots = workingDots,
                            onOpenUrl = {
                                val url = item.final_url.ifBlank { item.url }
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    uriHandler.openUri(url)
                                }
                            },
                            onFavorite = {
                                setFavorite(item, !item.favorite)
                            },
                            onReadToggle = {
                                setRead(item, item.read_state != "read")
                            },
                            onArchive = {
                                archive(item)
                            },
                            onDelete = {
                                deleteCandidate = item
                            }
                        )

                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }

    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            containerColor = EchoPanel,
            titleContentColor = EchoText,
            textContentColor = EchoMuted,
            title = { Text("Delete Echo Stack item?") },
            text = {
                Text(item.title.ifBlank { item.url })
            },
            confirmButton = {
                TextButton(onClick = { deleteItem(item) }) {
                    Text("Delete", color = EchoBad)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel", color = EchoMuted)
                }
            }
        )
    }
}

@Composable
private fun EchoTextField(
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
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = EchoText,
            unfocusedTextColor = EchoText,
            focusedContainerColor = EchoBg.copy(alpha = 0.35f),
            unfocusedContainerColor = EchoBg.copy(alpha = 0.35f),
            focusedBorderColor = EchoOrange,
            unfocusedBorderColor = EchoPanelLine,
            focusedLabelColor = EchoOrange,
            unfocusedLabelColor = EchoMuted,
            cursorColor = EchoOrange,
            focusedPlaceholderColor = EchoMuted,
            unfocusedPlaceholderColor = EchoMuted
        )
    )
}

@Composable
private fun EchoNotesField(
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        singleLine = false,
        minLines = 3,
        label = { Text("Notes") },
        placeholder = { Text("Why this link matters, reminders, context...") },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = EchoText,
            unfocusedTextColor = EchoText,
            focusedContainerColor = EchoBg.copy(alpha = 0.35f),
            unfocusedContainerColor = EchoBg.copy(alpha = 0.35f),
            focusedBorderColor = EchoOrange,
            unfocusedBorderColor = EchoPanelLine,
            focusedLabelColor = EchoOrange,
            unfocusedLabelColor = EchoMuted,
            cursorColor = EchoOrange,
            focusedPlaceholderColor = EchoMuted,
            unfocusedPlaceholderColor = EchoMuted
        )
    )
}

@Composable
private fun EchoStackItemCard(
    item: EchoStackItemDto,
    isArchiving: Boolean,
    archiveDots: Int,
    onOpenUrl: () -> Unit,
    onFavorite: () -> Unit,
    onReadToggle: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val archiveStatus = item.archive_status.ifBlank { "none" }
    val archiveActive = isArchiving || archiveStatus == "archiving"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = EchoPanel
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, EchoPanelLine)
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
                        text = item.title.ifBlank { item.url.ifBlank { "Untitled" } },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = EchoText
                    )

                    if (item.site_name.isNotBlank()) {
                        Text(
                            text = item.site_name,
                            style = MaterialTheme.typography.bodySmall,
                            color = EchoOrange
                        )
                    }
                }

                Text(
                    text = if (item.favorite) "★" else "☆",
                    style = MaterialTheme.typography.titleLarge,
                    color = EchoOrange,
                    modifier = Modifier.clickable(onClick = onFavorite)
                )
            }

            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EchoMuted
                )
            }
            if (item.collection.isNotBlank() || item.tags_text.isNotBlank()) {
                Text(
                    text = buildString {
                        if (item.collection.isNotBlank()) {
                            append("Collection: ")
                            append(item.collection)
                        }
                        if (item.collection.isNotBlank() && item.tags_text.isNotBlank()) {
                            append(" • ")
                        }
                        if (item.tags_text.isNotBlank()) {
                            append("Tags: ")
                            append(item.tags_text)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = EchoOrangeSoft
                )
            }

            if (item.notes.isNotBlank()) {
                Text(
                    text = item.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EchoMuted
                )
            }
            Text(
                text = item.url,
                style = MaterialTheme.typography.bodySmall,
                color = EchoMuted
            )

            Text(
                text = buildString {
                    append(if (item.read_state == "read") "Read" else "Unread")
                    append(" • Archive: ")
                    append(if (archiveActive) "archiving" else archiveStatus)
                    if (item.archive_bytes > 0L) {
                        append(" • ")
                        append(formatEchoBytes(item.archive_bytes))
                    }
                    val t = formatEchoTime(item.created_epoch)
                    if (t.isNotBlank()) {
                        append(" • ")
                        append(t)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = EchoMuted
            )

            if (archiveStatus == "failed" && item.archive_error.isNotBlank()) {
                Text(
                    text = "Archive error: ${item.archive_error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = EchoBad
                )
            }

            HorizontalDivider(
                color = EchoPanelLine.copy(alpha = 0.8f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EchoActionButton("Open", onOpenUrl)

                EchoActionButton(
                    text = if (item.read_state == "read") "Unread" else "Read",
                    onClick = onReadToggle
                )

                EchoArchiveActionButton(
                    text = when {
                        archiveActive -> "Archiving" + ".".repeat(archiveDots)
                        archiveStatus == "archived" -> "Archived"
                        archiveStatus == "failed" -> "Retry"
                        else -> "Archive"
                    },
                    active = archiveActive,
                    archived = archiveStatus == "archived" && !archiveActive,
                    onClick = onArchive,
                    enabled = !archiveActive
                )

                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = EchoBad,
                        disabledContentColor = EchoMuted
                    )
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun EchoActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = EchoOrangeSoft,
            disabledContentColor = EchoMuted
        )
    ) {
        Text(text)
    }
}
@Composable
private fun EchoArchiveActionButton(
    text: String,
    active: Boolean,
    archived: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = when {
                active -> EchoOrangeSoft
                archived -> EchoGood
                else -> EchoOrangeSoft
            },
            disabledContentColor = if (active) EchoOrangeSoft else EchoMuted
        )
    ) {
        Text(
            text = text,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}
private fun formatEchoTime(unixSeconds: Long): String {
    if (unixSeconds <= 0L) return ""
    val date = Date(unixSeconds * 1000L)
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(date)
}

private fun formatEchoBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups - 1])
}
