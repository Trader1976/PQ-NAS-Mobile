package com.pqnas.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.files.FilesRepository
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import com.pqnas.mobile.files.FileScope
import com.pqnas.mobile.files.ScopedFilesOps
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    filesRepository: FilesRepository,
    fileScope: FileScope = FileScope.User,
    relPath: String,
    displayName: String,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val uiScope = rememberCoroutineScope()
    val scopedOps = remember(filesRepository) { ScopedFilesOps(filesRepository) }

    var editorValue by remember(relPath) { mutableStateOf(TextFieldValue("")) }
    var originalText by remember(relPath) { mutableStateOf("") }
    var encoding by remember(relPath) { mutableStateOf("utf-8") }
    var mtimeEpoch by remember(relPath) { mutableStateOf<Long?>(null) }
    var sha256 by remember(relPath) { mutableStateOf<String?>(null) }

    var loading by remember(relPath) { mutableStateOf(true) }
    var saving by remember(relPath) { mutableStateOf(false) }
    var status by remember(relPath) { mutableStateOf("Loading...") }

    var showFindBar by remember(relPath) { mutableStateOf(false) }
    var findQuery by remember(relPath) { mutableStateOf("") }
    var matchCase by remember(relPath) { mutableStateOf(false) }

    var showDiscardDialog by remember(relPath) { mutableStateOf(false) }
    var showReloadDialog by remember(relPath) { mutableStateOf(false) }
    var readOnly by remember(relPath) { mutableStateOf(false) }
    var leaseHeartbeatJob by remember(relPath) { mutableStateOf<Job?>(null) }

    val dirty = editorValue.text != originalText
    val matches = remember(editorValue.text, findQuery, matchCase) {
        findMatches(
            fullText = editorValue.text,
            query = findQuery,
            matchCase = matchCase
        )
    }
    val findStatus = remember(matches, findQuery, editorValue.selection) {
        computeFindStatus(
            matches = matches,
            query = findQuery,
            selectedStart = editorValue.selection.start
        )
    }
    fun stopLeaseHeartbeat() {
        leaseHeartbeatJob?.cancel()
        leaseHeartbeatJob = null
    }

    fun leaseLockedMessage(raw: String): String {
        val lower = raw.lowercase(Locale.getDefault())
        return when {
            "edit_locked" in lower ->
                "This file is currently being edited by another session. Opened in read-only mode."
            "edit_lock_missing" in lower ->
                "Edit lease was lost. The file is now read-only. Reload to try again."
            else ->
                "This file is currently read-only."
        }
    }

    fun startLeaseHeartbeat() {
        stopLeaseHeartbeat()

        if (fileScope !is FileScope.Workspace || !fileScope.canWrite) return

        leaseHeartbeatJob = uiScope.launch {
            while (isActive) {
                delay(20_000L)
                try {
                    scopedOps.refreshEditLease(fileScope, relPath)
                } catch (e: Exception) {
                    stopLeaseHeartbeat()
                    readOnly = true
                    status = leaseLockedMessage(e.message.orEmpty())
                }
            }
        }
    }
    suspend fun loadFile() {
        loading = true
        status = "Loading..."
        stopLeaseHeartbeat()

        try {
            val resp = scopedOps.readText(fileScope, relPath)
            if (!resp.ok) {
                throw IllegalStateException(composeApiMessage(resp.error, resp.message, "Read text failed"))
            }

            val text = resp.text ?: ""
            editorValue = TextFieldValue(text = text)
            originalText = text
            encoding = resp.encoding ?: "utf-8"
            mtimeEpoch = resp.mtime_epoch
            sha256 = resp.sha256

            readOnly = false

            if (fileScope is FileScope.Workspace) {
                if (!fileScope.canWrite) {
                    readOnly = true
                    status = "Read-only: your workspace role does not allow editing."
                } else {
                    try {
                        scopedOps.acquireEditLease(fileScope, relPath)
                        readOnly = false
                        startLeaseHeartbeat()
                        status = "OK"
                    } catch (e: Exception) {
                        readOnly = true
                        status = leaseLockedMessage(e.message.orEmpty())
                    }
                }
            } else {
                status = "OK"
            }

            loading = false
        } catch (e: Exception) {
            loading = false
            readOnly = true
            status = friendlyTextEditorMessage("Read text", e)
        }
    }

    fun saveFile() {
        if (loading || saving || !dirty || readOnly) return

        uiScope.launch {
            saving = true
            status = "Saving..."

            try {
                if (fileScope is FileScope.Workspace && fileScope.canWrite) {
                    scopedOps.refreshEditLease(fileScope, relPath)
                }

                val resp = scopedOps.writeText(
                    scope = fileScope,
                    path = relPath,
                    text = editorValue.text,
                    expectedMtimeEpoch = mtimeEpoch,
                    expectedSha256 = sha256
                )

                if (!resp.ok) {
                    throw IllegalStateException(composeApiMessage(resp.error, resp.message, "Write text failed"))
                }

                originalText = editorValue.text
                mtimeEpoch = resp.mtime_epoch ?: mtimeEpoch
                sha256 = resp.sha256 ?: sha256
                saving = false
                status = "Saved."
                onSaved()
            } catch (e: Exception) {
                saving = false

                val raw = e.message.orEmpty().lowercase(Locale.getDefault())
                status = when {
                    "changed_on_server" in raw ->
                        "File changed on server. Reload and review before saving again."
                    "edit_locked" in raw || "edit_lock_missing" in raw -> {
                        readOnly = true
                        leaseLockedMessage(e.message.orEmpty())
                    }
                    else ->
                        friendlyTextEditorMessage("Write text", e)
                }
            }
        }
    }

    fun jumpToMatch(start: Int) {
        if (findQuery.isBlank()) return
        val end = (start + findQuery.length).coerceAtMost(editorValue.text.length)
        editorValue = editorValue.copy(
            selection = TextRange(start, end)
        )
    }

    fun findNext() {
        if (findQuery.isBlank()) return
        if (matches.isEmpty()) return

        val startPos = editorValue.selection.end.coerceAtLeast(0)
        val next = matches.firstOrNull { it >= startPos } ?: matches.first()
        jumpToMatch(next)
    }

    fun findPrev() {
        if (findQuery.isBlank()) return
        if (matches.isEmpty()) return

        val startPos = (editorValue.selection.start - 1).coerceAtLeast(0)
        val prev = matches.lastOrNull { it <= startPos } ?: matches.last()
        jumpToMatch(prev)
    }

    fun requestClose() {
        if (saving) return
        if (dirty) {
            showDiscardDialog = true
        } else {
            uiScope.launch {
                stopLeaseHeartbeat()
                runCatching { scopedOps.releaseEditLease(fileScope, relPath) }
                onClose()
            }
        }
    }

    fun requestReload() {
        if (saving) return
        if (dirty) {
            showReloadDialog = true
        } else {
            uiScope.launch { loadFile() }
        }
    }

    BackHandler {
        requestClose()
    }

    LaunchedEffect(relPath) {
        loadFile()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Edit text file",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { requestClose() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showFindBar = !showFindBar },
                            enabled = !loading && !saving
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Find"
                            )
                        }

                        IconButton(
                            onClick = { requestReload() },
                            enabled = !loading && !saving
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload"
                            )
                        }

                        TextButton(
                            onClick = { saveFile() },
                            enabled = !loading && !saving && dirty
                        ) {
                            Text(if (saving) "Saving..." else "Save")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "/$relPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Encoding: $encoding • ${formatBytes(editorValue.text.toByteArray(Charsets.UTF_8).size.toLong())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = if (dirty) "Unsaved changes" else "No local changes",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dirty) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = if (readOnly) "Mode: read-only" else "Mode: editable",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (readOnly) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                status == "OK" || status == "Saved." ->
                                    MaterialTheme.colorScheme.tertiary

                                status.contains("failed", ignoreCase = true) ||
                                        status.contains("denied", ignoreCase = true) ||
                                        status.contains("expired", ignoreCase = true) ||
                                        status.contains("not found", ignoreCase = true) ||
                                        status.contains("cannot", ignoreCase = true) ||
                                        status.contains("changed on server", ignoreCase = true) ->
                                    MaterialTheme.colorScheme.error

                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                if (showFindBar) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = findQuery,
                                onValueChange = { findQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Search") },
                                enabled = !loading && !saving
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = matchCase,
                                    onClick = { matchCase = !matchCase },
                                    label = { Text("Match case") },
                                    enabled = !loading && !saving
                                )

                                TextButton(
                                    onClick = { findPrev() },
                                    enabled = findQuery.isNotBlank() && matches.isNotEmpty()
                                ) {
                                    Text("Prev")
                                }

                                TextButton(
                                    onClick = { findNext() },
                                    enabled = findQuery.isNotBlank() && matches.isNotEmpty()
                                ) {
                                    Text("Next")
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Text(
                                    text = findStatus,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (findStatus == "Not found") {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = editorValue,
                    onValueChange = { editorValue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    enabled = !loading && !saving && !readOnly,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    singleLine = false
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = { requestReload() },
                        enabled = !loading && !saving
                    ) {
                        Text("Reload")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = { requestClose() },
                        enabled = !saving,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Close")
                    }

                    TextButton(
                        onClick = { saveFile() },
                        enabled = !loading && !saving && dirty
                    ) {
                        Text(if (saving) "Saving..." else "Save")
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Close the editor and discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        uiScope.launch {
                            stopLeaseHeartbeat()
                            runCatching { scopedOps.releaseEditLease(fileScope, relPath) }
                            onClose()
                        }
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showReloadDialog) {
        AlertDialog(
            onDismissRequest = { showReloadDialog = false },
            title = { Text("Reload from server?") },
            text = { Text("Discard unsaved changes and reload the latest version from the server?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReloadDialog = false
                        uiScope.launch { loadFile() }
                    }
                ) {
                    Text("Reload")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReloadDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun findMatches(
    fullText: String,
    query: String,
    matchCase: Boolean
): List<Int> {
    if (query.isBlank()) return emptyList()

    val haystack = if (matchCase) fullText else fullText.lowercase(Locale.getDefault())
    val needle = if (matchCase) query else query.lowercase(Locale.getDefault())
    val out = mutableListOf<Int>()

    var pos = 0
    while (true) {
        val idx = haystack.indexOf(needle, pos)
        if (idx < 0) break
        out += idx
        pos = idx + maxOf(1, needle.length)
    }

    return out
}

private fun computeFindStatus(
    matches: List<Int>,
    query: String,
    selectedStart: Int
): String {
    if (query.isBlank()) return ""
    if (matches.isEmpty()) return "Not found"

    val exact = matches.indexOf(selectedStart)
    val current = when {
        exact >= 0 -> exact
        else -> matches.indexOfFirst { it >= selectedStart }.takeIf { it >= 0 } ?: 0
    }

    return "${current + 1} / ${matches.size}"
}

private fun composeApiMessage(
    error: String?,
    message: String?,
    fallback: String
): String {
    val left = error?.trim().orEmpty()
    val right = message?.trim().orEmpty()

    return when {
        left.isNotBlank() && right.isNotBlank() -> "$left: $right"
        left.isNotBlank() -> left
        right.isNotBlank() -> right
        else -> fallback
    }
}

private fun friendlyTextEditorMessage(
    action: String,
    error: Throwable
): String {
    val msg = error.message?.trim().orEmpty()
    val lower = msg.lowercase(Locale.getDefault())

    return when {
        "changed_on_server" in lower || "changed on server" in lower ->
            "File changed on server. Reload and review before saving again."

        "edit_locked" in lower ->
            "This file is currently being edited by another session. Opened in read-only mode."

        "edit_lock_missing" in lower ->
            "Edit lease was lost. The file is now read-only. Reload to try again."

        msg.contains("HTTP 400", ignoreCase = true) ->
            "$action failed: invalid request."

        msg.contains("HTTP 401", ignoreCase = true) ->
            "Session expired. Please pair again."

        msg.contains("HTTP 403", ignoreCase = true) ->
            "Access denied."

        msg.contains("HTTP 404", ignoreCase = true) ->
            "Item not found."

        msg.contains("HTTP 409", ignoreCase = true) ->
            "$action failed: conflict."

        msg.contains("HTTP 413", ignoreCase = true) ->
            "$action failed: file is too large."

        msg.contains("HTTP 500", ignoreCase = true) ->
            "$action failed: server error."

        msg.isNotBlank() ->
            "$action failed: $msg"

        else ->
            "$action failed: unknown error"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups - 1])
}