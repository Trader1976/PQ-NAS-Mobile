package com.pqnas.mobile.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.api.FileItemDto
import com.pqnas.mobile.files.FileTypeIcons
import com.pqnas.mobile.files.FilesRepository
import com.pqnas.mobile.files.SvgIconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun FilesScreen(
    filesRepository: FilesRepository,
    onLogout: (() -> Unit)? = null
) {
    var currentPath by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var status by remember { mutableStateOf("Loading...") }
    var infoItem by remember { mutableStateOf<FileItemDto?>(null) }
    var pendingDownloadItem by remember { mutableStateOf<FileItemDto?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    fun load(path: String?) {
        scope.launch {
            status = "Loading..."
            try {
                val resp = filesRepository.list(path)
                items = resp.items.sortedWith(
                    compareBy<FileItemDto> { it.type != "dir" }
                        .thenBy { it.name.lowercase(Locale.getDefault()) }
                )
                currentPath = if (resp.path.isBlank()) null else resp.path
                status = "OK"
            } catch (e: Exception) {
                status = "Error: ${e.message}"
            }
        }
    }

    fun parentPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val parts = path.split("/").filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        return parts.dropLast(1).joinToString("/").ifBlank { null }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val item = pendingDownloadItem
        if (uri == null || item == null) {
            pendingDownloadItem = null
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            try {
                status = "Downloading ${item.name}..."
                val fullPath = buildItemPath(currentPath, item.name)
                val body = filesRepository.download(fullPath)
                val bytes = withContext(Dispatchers.IO) { body.bytes() }
                saveDownloadedFile(context, uri, bytes)
                status = "OK"
                snackbarHostState.showSnackbar("Saved to Download/${item.name}")
            } catch (e: Exception) {
                status = "Error: ${e.message}"
                snackbarHostState.showSnackbar("Download failed: ${e.message}")
            } finally {
                pendingDownloadItem = null
            }
        }
    }

    LaunchedEffect(Unit) {
        load(null)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "PQ-NAS Files",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Path: ${currentPath ?: "/"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    status.startsWith("Error:") -> MaterialTheme.colorScheme.error
                    status == "OK" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { load(parentPath(currentPath)) },
                    enabled = currentPath != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Up")
                }

                Button(
                    onClick = { load(currentPath) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Refresh")
                }

                if (onLogout != null) {
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Logout")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                if (items.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No files here",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "This folder is empty or could not be loaded.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items) { item ->
                            FileRow(
                                item = item,
                                onOpen = {
                                    if (item.type == "dir") {
                                        val next = listOfNotNull(currentPath, item.name)
                                            .joinToString("/")
                                        load(next)
                                    }
                                },
                                onMenuAction = { action, clickedItem ->
                                    when (action) {
                                        "Info" -> infoItem = clickedItem

                                        "Download" -> {
                                            if (clickedItem.type == "dir") {
                                                status = "Folder download not implemented yet: ${clickedItem.name}"
                                            } else {
                                                pendingDownloadItem = clickedItem
                                                createDocumentLauncher.launch(clickedItem.name)
                                            }
                                        }

                                        "Rename" -> status = "Rename not implemented yet: ${clickedItem.name}"
                                        "Delete" -> status = "Delete not implemented yet: ${clickedItem.name}"
                                        else -> status = "$action not implemented yet: ${clickedItem.name}"
                                    }
                                }
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            }
        }
    }

    infoItem?.let { item ->
        AlertDialog(
            onDismissRequest = { infoItem = null },
            confirmButton = {
                TextButton(onClick = { infoItem = null }) {
                    Text("OK")
                }
            },
            title = {
                Text("Item info")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Name: ${item.name}")
                    Text("Type: ${if (item.type == "dir") "Directory" else "File"}")
                    Text("Size: ${if (item.type == "dir") "-" else formatBytes(item.size_bytes ?: 0)}")
                    Text(
                        "Modified: ${
                            item.mtime_unix?.takeIf { it > 0 }?.let { formatUnixTime(it) } ?: "-"
                        }"
                    )
                }
            }
        )
    }
}

@Composable
private fun FileRow(
    item: FileItemDto,
    onOpen: () -> Unit,
    onMenuAction: (String, FileItemDto) -> Unit
) {
    val isDir = item.type == "dir"
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isDir, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FileIcon(
            item = item,
            modifier = Modifier.size(28.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isDir) FontWeight.SemiBold else FontWeight.Normal
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = buildMetaLine(item),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isDir) {
            Text(
                text = "Open",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More actions"
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Download") },
                    onClick = {
                        menuExpanded = false
                        onMenuAction("Download", item)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuExpanded = false
                        onMenuAction("Rename", item)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menuExpanded = false
                        onMenuAction("Delete", item)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Info") },
                    onClick = {
                        menuExpanded = false
                        onMenuAction("Info", item)
                    }
                )
            }
        }
    }
}

@Composable
private fun FileIcon(
    item: FileItemDto,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val assetPath = remember(item.type, item.name) {
        FileTypeIcons.assetPathFor(item)
    }

    val bitmap = remember(assetPath) {
        SvgIconLoader.loadBitmapFromAssets(
            context = context,
            assetPath = assetPath,
            sizePx = 64
        )
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = if (item.type == "dir") "Directory icon" else "File icon",
            modifier = modifier
        )
    } else {
        Text(
            text = if (item.type == "dir") "📁" else "📄",
            modifier = modifier,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private fun buildMetaLine(item: FileItemDto): String {
    val validTime = item.mtime_unix?.takeIf { it > 0 }

    if (item.type == "dir") {
        return validTime?.let {
            "Directory • ${formatUnixTime(it)}"
        } ?: "Directory"
    }

    val sizeText = formatBytes(item.size_bytes ?: 0)
    val timeText = validTime?.let { formatUnixTime(it) }

    return if (timeText != null) {
        "File • $sizeText • $timeText"
    } else {
        "File • $sizeText"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups - 1])
}

private fun formatUnixTime(unixSeconds: Long): String {
    val date = Date(unixSeconds * 1000L)
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(date)
}

private fun buildItemPath(currentPath: String?, itemName: String): String {
    return listOfNotNull(currentPath, itemName)
        .filter { it.isNotBlank() }
        .joinToString("/")
}

private suspend fun saveDownloadedFile(
    context: Context,
    uri: Uri,
    bytes: ByteArray
) {
    withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw IllegalStateException("Could not open output stream")
    }
}