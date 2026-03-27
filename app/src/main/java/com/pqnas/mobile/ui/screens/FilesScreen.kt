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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
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
import retrofit2.HttpException
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import androidx.compose.material3.LinearProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
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
    var renameItem by remember { mutableStateOf<FileItemDto?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteItem by remember { mutableStateOf<FileItemDto?>(null) }
    var previewItem by remember { mutableStateOf<FileItemDto?>(null) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var previewStatus by remember { mutableStateOf("") }
    var previewIndex by remember { mutableStateOf(-1) }

    var textEditItem by remember { mutableStateOf<FileItemDto?>(null) }
    var textEditText by remember { mutableStateOf("") }
    var textEditOriginalText by remember { mutableStateOf("") }
    var textEditEncoding by remember { mutableStateOf("utf-8") }
    var textEditMtimeEpoch by remember { mutableStateOf<Long?>(null) }
    var textEditSha256 by remember { mutableStateOf<String?>(null) }
    var textEditLoading by remember { mutableStateOf(false) }
    var textEditSaving by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingUploadUri by remember { mutableStateOf<Uri?>(null) }
    var pendingUploadName by remember { mutableStateOf<String?>(null) }
    var overwriteUploadTargetPath by remember { mutableStateOf<String?>(null) }
    var overwriteUploadUri by remember { mutableStateOf<Uri?>(null) }

    var showCreateMenu by remember { mutableStateOf(false) }
    var uploadInProgress by remember { mutableStateOf(false) }
    var uploadFileName by remember { mutableStateOf<String?>(null) }
    var uploadBytesSent by remember { mutableStateOf(0L) }
    var uploadBytesTotal by remember { mutableStateOf(0L) }

    var newFolderDialogOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var newTextFileDialogOpen by remember { mutableStateOf(false) }
    var newTextFileName by remember { mutableStateOf("") }



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
                status = friendlyHttpMessage("Load", e)
            }
        }
    }

    fun openImagePreview(item: FileItemDto) {
        if (item.type != "file") return
        if (!isProbablyImageFile(item.name)) return

        val visibleImages = items.filter { it.type == "file" && isProbablyImageFile(it.name) }
        val idx = visibleImages.indexOfFirst { it.name == item.name }

        previewItem = item
        previewBitmap = null
        previewStatus = "Loading..."
        previewIndex = idx

        scope.launch {
            try {
                val fullPath = buildItemPath(currentPath, item.name)
                val body = filesRepository.download(fullPath)
                val bytes = withContext(Dispatchers.IO) { body.bytes() }

                val bmp = withContext(Dispatchers.Default) {
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }

                if (bmp == null) {
                    previewStatus = "Failed to decode image"
                } else {
                    previewBitmap = bmp
                    previewStatus = "${bmp.width} × ${bmp.height}"
                }
            } catch (e: Exception) {
                previewStatus = friendlyHttpMessage("Preview", e)
            }
        }
    }
    fun openTextEditor(item: FileItemDto) {
        if (item.type != "file") return
        if (!isProbablyTextFile(item.name)) return

        textEditItem = item
        textEditText = ""
        textEditOriginalText = ""
        textEditEncoding = "utf-8"
        textEditMtimeEpoch = null
        textEditSha256 = null
        textEditLoading = true
        textEditSaving = false
        status = "Loading text file..."

        scope.launch {
            try {
                val fullPath = buildItemPath(currentPath, item.name)
                val resp = filesRepository.readText(fullPath)

                textEditText = resp.text ?: ""
                textEditOriginalText = resp.text ?: ""
                textEditEncoding = resp.encoding ?: "utf-8"
                textEditMtimeEpoch = resp.mtime_epoch
                textEditSha256 = resp.sha256
                textEditLoading = false
                status = "OK"
            } catch (e: Exception) {
                textEditLoading = false
                val msg = friendlyHttpMessage("Read text", e)
                status = msg
                snackbarHostState.showSnackbar(msg)
                textEditItem = null
            }
        }
    }
    fun saveTextEditor() {
        val item = textEditItem ?: return
        val newText = textEditText

        textEditSaving = true
        status = "Saving ${item.name}..."

        scope.launch {
            try {
                val fullPath = buildItemPath(currentPath, item.name)
                val resp = filesRepository.writeText(
                    path = fullPath,
                    text = newText,
                    expectedMtimeEpoch = textEditMtimeEpoch,
                    expectedSha256 = textEditSha256
                )

                textEditOriginalText = newText
                textEditMtimeEpoch = resp.mtime_epoch
                textEditSha256 = resp.sha256
                textEditSaving = false
                status = "OK"
                snackbarHostState.showSnackbar("Saved ${item.name}")
                load(currentPath)
            } catch (e: Exception) {
                textEditSaving = false
                val msg = friendlyHttpMessage("Write text", e)
                status = msg
                snackbarHostState.showSnackbar(msg)
            }
        }
    }
    fun openAdjacentImage(step: Int) {
        val visibleImages = items.filter { it.type == "file" && isProbablyImageFile(it.name) }
        if (visibleImages.isEmpty()) return

        val current = previewItem ?: return
        val curIdx = visibleImages.indexOfFirst { it.name == current.name }
        if (curIdx < 0) return

        val nextIdx = curIdx + step
        if (nextIdx !in visibleImages.indices) return

        openImagePreview(visibleImages[nextIdx])
    }
    fun parentPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val parts = path.split("/").filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        return parts.dropLast(1).joinToString("/").ifBlank { null }
    }
    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            status = "Folder name cannot be empty"
            return
        }
        if (trimmed.contains("/")) {
            status = "Folder name cannot contain /"
            return
        }

        scope.launch {
            try {
                val path = buildItemPath(currentPath, trimmed)
                filesRepository.mkdir(path)
                newFolderDialogOpen = false
                newFolderName = ""
                status = "OK"
                snackbarHostState.showSnackbar("Created folder $trimmed")
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Create folder", e)
                status = msg
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    fun createTextFile(name: String) {
        var trimmed = name.trim()
        if (trimmed.isBlank()) {
            status = "File name cannot be empty"
            return
        }
        if (trimmed.contains("/")) {
            status = "File name cannot contain /"
            return
        }
        if (!trimmed.contains(".")) {
            trimmed += ".txt"
        }

        scope.launch {
            try {
                val path = buildItemPath(currentPath, trimmed)
                filesRepository.createTextFile(path = path, text = "", overwrite = false)
                newTextFileDialogOpen = false
                newTextFileName = ""
                status = "OK"
                snackbarHostState.showSnackbar("Created file $trimmed")
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Create text file", e)
                status = msg
                snackbarHostState.showSnackbar(msg)
            }
        }
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
                val msg = friendlyHttpMessage("Download", e)
                status = msg
                snackbarHostState.showSnackbar(msg)
            } finally {
                pendingDownloadItem = null
            }
        }
    }

    fun uploadUri(uri: Uri, overwrite: Boolean) {
        var fileName: String? = null

        scope.launch {
            try {
                fileName = queryDisplayName(context, uri)?.trim()
                if (fileName.isNullOrBlank()) {
                    uploadInProgress = false
                    uploadFileName = null
                    uploadBytesSent = 0L
                    uploadBytesTotal = 0L

                    val msg = "Upload failed: could not determine file name."
                    status = msg
                    snackbarHostState.showSnackbar(msg)
                    return@launch
                }

                val size = queryFileSize(context, uri)
                if (size == null || size < 0L) {
                    uploadInProgress = false
                    uploadFileName = null
                    uploadBytesSent = 0L
                    uploadBytesTotal = 0L

                    val msg = "Upload failed: file size is unknown. Server requires Content-Length."
                    status = msg
                    snackbarHostState.showSnackbar(msg)
                    return@launch
                }

                val safeFileName = fileName!!
                val targetPath = buildItemPath(currentPath, safeFileName)

                uploadInProgress = true
                uploadFileName = safeFileName
                uploadBytesSent = 0L
                uploadBytesTotal = size

                val body = uriRequestBody(
                    context = context,
                    uri = uri,
                    contentLength = size,
                    onProgress = { sent, total ->
                        uploadBytesSent = sent
                        uploadBytesTotal = total
                    }
                )

                status = "Uploading $safeFileName..."
                filesRepository.upload(path = targetPath, body = body, overwrite = overwrite)
                status = "OK"

                uploadInProgress = false
                uploadFileName = null
                uploadBytesSent = 0L
                uploadBytesTotal = 0L

                overwriteUploadTargetPath = null
                overwriteUploadUri = null
                pendingUploadUri = null
                pendingUploadName = null

                snackbarHostState.showSnackbar(
                    if (overwrite) "Replaced $safeFileName" else "Uploaded $safeFileName"
                )
                load(currentPath)
            } catch (e: Exception) {
                uploadInProgress = false
                uploadFileName = null
                uploadBytesSent = 0L
                uploadBytesTotal = 0L

                val http = (e as? HttpException)?.code()
                if (!overwrite && http == 409 && !fileName.isNullOrBlank()) {
                    overwriteUploadTargetPath = buildItemPath(currentPath, fileName!!)
                    overwriteUploadUri = uri
                    pendingUploadUri = uri
                    pendingUploadName = fileName
                    status = "File already exists: $fileName"
                } else {
                    val msg = friendlyHttpMessage("Upload", e)
                    status = msg
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }
    val uploadDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploadUri(uri, overwrite = false)
    }
    LaunchedEffect(Unit) {
        load(null)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!uploadInProgress) showCreateMenu = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "DNA-Nexus Files",
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
                    status == "OK" -> MaterialTheme.colorScheme.tertiary
                    status.contains("failed", ignoreCase = true) ||
                            status.contains("denied", ignoreCase = true) ||
                            status.contains("expired", ignoreCase = true) ||
                            status.contains("not found", ignoreCase = true) ||
                            status.contains("cannot", ignoreCase = true) -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (uploadInProgress && uploadBytesTotal > 0L) {
                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = uploadFileName?.let { "Uploading $it" } ?: "Uploading...",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = {
                                if (uploadBytesTotal <= 0L) 0f
                                else (uploadBytesSent.toFloat() / uploadBytesTotal.toFloat()).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "${((uploadBytesSent * 100) / uploadBytesTotal).coerceIn(0, 100)}% • ${
                                formatBytes(uploadBytesSent)
                            } / ${formatBytes(uploadBytesTotal)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
                                    } else if (isProbablyImageFile(item.name)) {
                                        openImagePreview(item)
                                    } else if (isProbablyTextFile(item.name)) {
                                        openTextEditor(item)
                                    }
                                },
                                onMenuAction = { action, clickedItem ->
                                    when (action) {

                                        "EditText" -> {
                                            openTextEditor(clickedItem)
                                        }

                                        "Info" -> infoItem = clickedItem

                                        "Download" -> {
                                            if (clickedItem.type == "dir") {
                                                status = "Folder download not implemented yet: ${clickedItem.name}"
                                            } else {
                                                pendingDownloadItem = clickedItem
                                                createDocumentLauncher.launch(clickedItem.name)
                                            }
                                        }

                                        "Rename" -> {
                                            renameItem = clickedItem
                                            renameText = clickedItem.name
                                        }

                                        "Delete" -> {
                                            deleteItem = clickedItem
                                        }

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
    if (showCreateMenu) {
        ModalBottomSheet(
            onDismissRequest = { showCreateMenu = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = "Add to DNA-Nexus",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                ListItem(
                    headlineContent = { Text("Upload file") },
                    supportingContent = { Text("Choose a file from this phone") },
                    leadingContent = {
                        Text(
                            text = "↑",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        showCreateMenu = false
                        uploadDocumentLauncher.launch(arrayOf("*/*"))
                    }
                )

                ListItem(
                    headlineContent = { Text("New folder") },
                    leadingContent = {
                        Text(
                            text = "📁",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    modifier = Modifier.clickable {
                        showCreateMenu = false
                        newFolderDialogOpen = true
                    }
                )

                ListItem(
                    headlineContent = { Text("New text file") },
                    leadingContent = {
                        Text(
                            text = "TXT",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        showCreateMenu = false
                        newTextFileDialogOpen = true
                    }
                )
            }
        }
    }
    if (newFolderDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                newFolderDialogOpen = false
                newFolderName = ""
            },
            title = {
                Text("New folder")
            },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text("Folder name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        createFolder(newFolderName)
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        newFolderDialogOpen = false
                        newFolderName = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    if (newTextFileDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                newTextFileDialogOpen = false
                newTextFileName = ""
            },
            title = {
                Text("New text file")
            },
            text = {
                OutlinedTextField(
                    value = newTextFileName,
                    onValueChange = { newTextFileName = it },
                    singleLine = true,
                    label = { Text("File name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        createTextFile(newTextFileName)
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        newTextFileDialogOpen = false
                        newTextFileName = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
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
    textEditItem?.let { item ->
        AlertDialog(
            onDismissRequest = {
                if (!textEditSaving) {
                    textEditItem = null
                    textEditText = ""
                    textEditOriginalText = ""
                    textEditEncoding = "utf-8"
                    textEditMtimeEpoch = null
                    textEditSha256 = null
                }
            },
            title = {
                Column {
                    Text("Edit text file")
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (textEditLoading) {
                            "Loading..."
                        } else {
                            "Encoding: $textEditEncoding"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = textEditText,
                        onValueChange = { textEditText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        enabled = !textEditLoading && !textEditSaving,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { saveTextEditor() },
                    enabled = !textEditLoading && !textEditSaving && textEditText != textEditOriginalText
                ) {
                    Text(if (textEditSaving) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        textEditItem = null
                        textEditText = ""
                        textEditOriginalText = ""
                        textEditEncoding = "utf-8"
                        textEditMtimeEpoch = null
                        textEditSha256 = null
                    },
                    enabled = !textEditSaving
                ) {
                    Text("Close")
                }
            }
        )
    }
    renameItem?.let { item ->
        AlertDialog(
            onDismissRequest = {
                renameItem = null
                renameText = ""
            },
            title = {
                Text("Rename")
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("New name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameText.trim()
                        if (newName.isBlank()) {
                            status = "Name cannot be empty"
                            return@TextButton
                        }

                        if (newName == item.name) {
                            renameItem = null
                            renameText = ""
                            return@TextButton
                        }

                        scope.launch {
                            try {
                                val fromPath = buildItemPath(currentPath, item.name)
                                val toPath = buildItemPath(currentPath, newName)
                                filesRepository.move(fromPath, toPath)
                                renameItem = null
                                renameText = ""
                                status = "OK"
                                snackbarHostState.showSnackbar("Renamed ${item.name} to $newName")
                                load(currentPath)
                            } catch (e: Exception) {
                                val msg = friendlyHttpMessage("Rename", e)
                                status = msg
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        renameItem = null
                        renameText = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = {
                Text("Delete")
            },
            text = {
                Text("Delete ${if (item.type == "dir") "folder" else "file"} \"${item.name}\"?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val path = buildItemPath(currentPath, item.name)
                                filesRepository.delete(path)
                                deleteItem = null
                                status = "OK"
                                snackbarHostState.showSnackbar("Deleted ${item.name}")
                                load(currentPath)
                            } catch (e: Exception) {
                                val msg = friendlyHttpMessage("Delete", e)
                                status = msg
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    previewItem?.let { item ->
        AlertDialog(
            onDismissRequest = {
                previewItem = null
                previewBitmap = null
                previewStatus = ""
                previewIndex = -1
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { openAdjacentImage(-1) },
                        enabled = run {
                            val visibleImages = items.filter { it.type == "file" && isProbablyImageFile(it.name) }
                            val idx = visibleImages.indexOfFirst { it.name == item.name }
                            idx > 0
                        }
                    ) {
                        Text("Prev")
                    }

                    TextButton(
                        onClick = { openAdjacentImage(1) },
                        enabled = run {
                            val visibleImages = items.filter { it.type == "file" && isProbablyImageFile(it.name) }
                            val idx = visibleImages.indexOfFirst { it.name == item.name }
                            idx >= 0 && idx < visibleImages.lastIndex
                        }
                    ) {
                        Text("Next")
                    }

                    TextButton(
                        onClick = {
                            previewItem = null
                            previewBitmap = null
                            previewStatus = ""
                            previewIndex = -1
                        }
                    ) {
                        Text("Close")
                    }
                }
            },
            title = {
                Text(item.name)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(previewStatus)

                    previewBitmap?.let { bmp ->
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        )
    }
    if (overwriteUploadTargetPath != null && overwriteUploadUri != null) {
        AlertDialog(
            onDismissRequest = {
                overwriteUploadTargetPath = null
                overwriteUploadUri = null
                pendingUploadUri = null
                pendingUploadName = null
            },
            title = {
                Text("Replace file?")
            },
            text = {
                Text("A file with this name already exists. Do you want to replace it?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = overwriteUploadUri
                        if (uri != null) {
                            uploadUri(uri, overwrite = true)
                        } else {
                            overwriteUploadTargetPath = null
                            overwriteUploadUri = null
                            pendingUploadUri = null
                            pendingUploadName = null
                        }
                    }
                ) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        overwriteUploadTargetPath = null
                        overwriteUploadUri = null
                        pendingUploadUri = null
                        pendingUploadName = null
                    }
                ) {
                    Text("Cancel")
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
            .clickable(onClick = onOpen)
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
                if (!isDir && isProbablyImageFile(item.name)) {
                    DropdownMenuItem(
                        text = { Text("Open preview") },
                        onClick = {
                            menuExpanded = false
                            onMenuAction("Preview", item)
                        }
                    )
                }

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

private fun isProbablyTextFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return ext in setOf(
        "txt", "md", "json", "js", "ts", "jsx", "tsx",
        "html", "htm", "css", "xml", "yml", "yaml",
        "toml", "ini", "conf", "log",
        "c", "cc", "cpp", "cxx", "h", "hh", "hpp", "hxx",
        "py", "sh", "bash", "zsh", "sql", "csv", "tsv",
        "java", "go", "rs", "rb", "php", "lua", "swift", "kt"
    )
}

private fun isProbablyImageFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico")
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

private fun friendlyHttpMessage(
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
        404 -> "Item not found."
        409 -> when (action) {
            "Rename" -> "Cannot rename: a file or folder with that name already exists."
            "Move" -> "Cannot move: destination already exists."
            "Delete" -> "Cannot delete: item is in a conflicting state."
            "Upload" -> "Upload failed: a file or folder with that name already exists."
            "Create text file" -> "Cannot create file: a file or folder with that name already exists."
            "Create folder" -> "Cannot create folder: path conflicts with an existing item."
            "Write text" -> "File changed on server. Reload and review before saving again."
            else -> "$action failed: destination already exists."
        }
        411 -> "Upload failed: server requires a known file size."
        413 -> "Upload failed: file is too large."
        500 -> "$action failed: server error."
        else -> {
            val msg = error.message?.takeIf { it.isNotBlank() } ?: "unknown error"
            "$action failed: $msg"
        }
    }
}
private fun queryDisplayName(context: Context, uri: Uri): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return null
}

private fun queryFileSize(context: Context, uri: Uri): Long? {
    val projection = arrayOf(OpenableColumns.SIZE)
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (sizeIndex >= 0 && cursor.moveToFirst()) {
            if (!cursor.isNull(sizeIndex)) {
                return cursor.getLong(sizeIndex)
            }
        }
    }

    return try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            if (afd.length >= 0L) afd.length else null
        }
    } catch (_: Exception) {
        null
    }
}

private fun uriRequestBody(
    context: Context,
    uri: Uri,
    contentLength: Long,
    mimeType: String? = null,
    onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
): RequestBody {
    return object : RequestBody() {
        override fun contentType() = mimeType?.toMediaTypeOrNull()

        override fun contentLength(): Long = contentLength

        override fun writeTo(sink: BufferedSink) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var uploaded = 0L

                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break

                    sink.write(buffer, 0, read)
                    uploaded += read
                    onProgress(uploaded, contentLength)
                }

                sink.flush()
            } ?: throw IllegalStateException("Could not open input stream")
        }
    }
}