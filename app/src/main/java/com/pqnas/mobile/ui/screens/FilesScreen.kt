package com.pqnas.mobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.pqnas.mobile.api.MeStorageResponse
import com.pqnas.mobile.files.FileTypeIcons
import com.pqnas.mobile.files.FilesRepository
import com.pqnas.mobile.files.SvgIconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import androidx.compose.material3.RadioButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    filesRepository: FilesRepository,
    onLogout: (() -> Unit)? = null
) {
    var currentPath by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var status by remember { mutableStateOf("Loading...") }
    var myStorage by remember { mutableStateOf<MeStorageResponse?>(null) }
    var storageStatus by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }

    var shareDialogItem by remember { mutableStateOf<FileItemDto?>(null) }
    var shareDialogUrl by remember { mutableStateOf("") }
    var shareDialogStatus by remember { mutableStateOf("") }
    var shareDialogExistingToken by remember { mutableStateOf<String?>(null) }
    var shareDialogExpiry by remember { mutableStateOf(defaultShareExpiryOption()) }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSharesManager by remember { mutableStateOf(false) }

    var infoItem by remember { mutableStateOf<FileItemDto?>(null) }
    var pendingDownloadItem by remember { mutableStateOf<FileItemDto?>(null) }
    var renameItem by remember { mutableStateOf<FileItemDto?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteItem by remember { mutableStateOf<FileItemDto?>(null) }
    var imagePreviewItems by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var imagePreviewStartIndex by remember { mutableStateOf<Int?>(null) }
    var textEditorPath by remember { mutableStateOf<String?>(null) }
    var textEditorName by remember { mutableStateOf<String?>(null) }

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

    var uploadJob by remember { mutableStateOf<Job?>(null) }
    var uploadCancelRequested by remember { mutableStateOf(false) }

    var newFolderDialogOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var newTextFileDialogOpen by remember { mutableStateOf(false) }
    var newTextFileName by remember { mutableStateOf("") }

    fun normalizeRelPath(rel: String?): String {
        return rel.orEmpty()
            .replace("\\", "/")
            .trim('/')
            .split("/")
            .filter { it.isNotBlank() }
            .joinToString("/")
    }

    fun itemFullPath(item: FileItemDto): String {
        return normalizeRelPath(buildItemPath(currentPath, item.name))
    }

    fun favoriteKey(type: String, path: String): String {
        val t = if (type == "dir") "dir" else "file"
        return "$t:${normalizeRelPath(path)}"
    }

    fun shareKey(type: String, path: String): String {
        val t = if (type == "dir") "dir" else "file"
        return "$t:${normalizeRelPath(path)}"
    }

    fun fullShareUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""

        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }

        val base = filesRepository.baseUrlForDisplay().trim().trimEnd('/')
        val rel = if (url.startsWith("/")) url else "/$url"
        return "$base$rel"
    }

    fun load(path: String?) {
        scope.launch {
            status = "Loading..."
            try {
                val resp = filesRepository.list(path)

                val favs = try {
                    filesRepository.getFavorites()
                } catch (_: Exception) {
                    null
                }

                val shares = try {
                    filesRepository.getShares()
                } catch (_: Exception) {
                    null
                }

                val favoriteKeys = favs?.items?.map {
                    favoriteKey(it.type, it.path)
                }?.toSet() ?: emptySet()

                val shareKeys = shares?.shares?.map {
                    shareKey(it.type, it.path)
                }?.toSet() ?: emptySet()

                val mergedItems = resp.items
                    .sortedWith(
                        compareBy<FileItemDto> { it.type != "dir" }
                            .thenBy { it.name.lowercase(Locale.getDefault()) }
                    )
                    .map { item ->
                        val fullItemPath = buildItemPath(resp.path.ifBlank { null }, item.name)
                        item.copy(
                            isFavorite = favoriteKeys.contains(
                                favoriteKey(item.type, fullItemPath)
                            ),
                            isShared = shareKeys.contains(
                                shareKey(item.type, fullItemPath)
                            )
                        )
                    }

                items = if (favoritesOnly) {
                    mergedItems.filter { it.isFavorite }
                } else {
                    mergedItems
                }

                currentPath = if (resp.path.isBlank()) null else resp.path

                try {
                    myStorage = filesRepository.getMyStorage()
                    storageStatus = ""
                } catch (e: Exception) {
                    myStorage = null
                    storageStatus = friendlyHttpMessage("Storage", e)
                }

                status = "OK"
            } catch (e: Exception) {
                status = friendlyHttpMessage("Load", e)
            }
        }
    }

    fun refreshCurrent() {
        load(currentPath)
    }

    fun clearUploadProgressState() {
        uploadInProgress = false
        uploadFileName = null
        uploadBytesSent = 0L
        uploadBytesTotal = 0L
        uploadCancelRequested = false
        uploadJob = null
    }

    fun openShareDialog(item: FileItemDto) {
        shareDialogItem = item
        shareDialogUrl = ""
        shareDialogStatus = ""
        shareDialogExistingToken = null
        shareDialogExpiry = defaultShareExpiryOption()

        scope.launch {
            try {
                val fullPath = itemFullPath(item)
                val shares = filesRepository.getShares()
                val existing = shares.shares.firstOrNull {
                    shareKey(it.type, it.path) == shareKey(item.type, fullPath)
                }

                if (existing != null) {
                    shareDialogUrl = fullShareUrl(existing.url)
                    shareDialogExistingToken = existing.token
                    shareDialogStatus = "Already shared"
                }
            } catch (_: Exception) {
            }
        }
    }

    fun createShareFor(item: FileItemDto, expiresSec: Long?) {
        scope.launch {
            try {
                val fullPath = itemFullPath(item)
                shareDialogStatus = "Creating share..."
                val resp = filesRepository.createShare(
                    path = fullPath,
                    type = item.type,
                    expiresSec = expiresSec
                )
                shareDialogUrl = fullShareUrl(resp.url)
                shareDialogExistingToken = resp.token
                shareDialogStatus = "Share link created (${shareExpiryLabel(expiresSec)})"
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Share", e)
                shareDialogStatus = msg
                status = msg
            }
        }
    }

    fun revokeShareForCurrentDialog() {
        val token = shareDialogExistingToken ?: return
        scope.launch {
            try {
                shareDialogStatus = "Revoking share..."
                filesRepository.revokeShare(token)
                shareDialogUrl = ""
                shareDialogExistingToken = null
                shareDialogStatus = "Share revoked"
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Revoke share", e)
                shareDialogStatus = msg
                status = msg
            }
        }
    }

    fun toggleFavorite(item: FileItemDto) {
        scope.launch {
            try {
                val fullPath = itemFullPath(item)
                if (item.isFavorite) {
                    filesRepository.removeFavorite(fullPath, item.type)
                    status = "Removed from favorites: ${item.name}"
                    snackbarHostState.showSnackbar("Removed from favorites: ${item.name}")
                } else {
                    filesRepository.addFavorite(fullPath, item.type)
                    status = "Added to favorites: ${item.name}"
                    snackbarHostState.showSnackbar("Added to favorites: ${item.name}")
                }
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Favorites", e)
                status = msg
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    fun openImagePreview(item: FileItemDto) {
        if (item.type != "file") return
        if (!isProbablyImageFile(item.name)) return

        val visibleImages = items.filter { it.type == "file" && isProbablyImageFile(it.name) }
        val idx = visibleImages.indexOfFirst { it.name == item.name }
        if (idx < 0) return

        imagePreviewItems = visibleImages
        imagePreviewStartIndex = idx
    }

    fun openTextEditor(item: FileItemDto) {
        if (item.type != "file") return
        if (!isProbablyTextFile(item.name)) return

        textEditorPath = buildItemPath(currentPath, item.name)
        textEditorName = item.name
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

        uploadJob = scope.launch {
            try {
                fileName = queryDisplayName(context, uri)?.trim()
                if (fileName.isNullOrBlank()) {
                    val msg = "Upload failed: could not determine file name."
                    status = msg
                    snackbarHostState.showSnackbar(msg)
                    return@launch
                }

                val size = queryFileSize(context, uri)
                if (size == null || size < 0L) {
                    val msg = "Upload failed: file size is unknown. Server requires Content-Length."
                    status = msg
                    snackbarHostState.showSnackbar(msg)
                    return@launch
                }

                val safeFileName = fileName!!
                val targetPath = buildItemPath(currentPath, safeFileName)

                uploadInProgress = true
                uploadCancelRequested = false
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

                overwriteUploadTargetPath = null
                overwriteUploadUri = null
                pendingUploadUri = null
                pendingUploadName = null

                status = "OK"
                snackbarHostState.showSnackbar(
                    if (overwrite) "Replaced $safeFileName" else "Uploaded $safeFileName"
                )
                load(currentPath)
            } catch (e: CancellationException) {
                overwriteUploadTargetPath = null
                overwriteUploadUri = null
                pendingUploadUri = null
                pendingUploadName = null

                status = "Upload cancelled."
                snackbarHostState.showSnackbar("Upload cancelled")
            } catch (e: Exception) {
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
            } finally {
                clearUploadProgressState()
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
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DNA-Nexus Files",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { showSettingsSheet = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings and info"
                    )
                }
            }

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

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                enabled = !uploadCancelRequested,
                                onClick = {
                                    uploadCancelRequested = true
                                    status = "Cancelling upload..."
                                    uploadJob?.cancel(CancellationException("User cancelled upload"))
                                }
                            ) {
                                Text(if (uploadCancelRequested) "Cancelling..." else "Cancel upload")
                            }
                        }
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Up")
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
                            text = if (favoritesOnly) "No favorites here" else "No files here",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (favoritesOnly)
                                "This folder has no favorite items."
                            else
                                "This folder is empty or could not be loaded.",
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
                                onToggleFavorite = {
                                    toggleFavorite(item)
                                },
                                onMenuAction = { action, clickedItem ->
                                    when (action) {
                                        "Preview" -> openImagePreview(clickedItem)
                                        "EditText" -> openTextEditor(clickedItem)
                                        "ToggleFavorite" -> toggleFavorite(clickedItem)
                                        "Share" -> openShareDialog(clickedItem)
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

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Settings & info",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                SettingsStorageSection(
                    storage = myStorage,
                    storageStatus = storageStatus
                )

                Button(
                    onClick = {
                        favoritesOnly = !favoritesOnly
                        showSettingsSheet = false
                        load(currentPath)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (favoritesOnly) "Show all items" else "Show favorites only")
                }

                Button(
                    onClick = {
                        showSettingsSheet = false
                        refreshCurrent()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh")
                }
                Button(
                    onClick = {
                        showSettingsSheet = false
                        showSharesManager = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share manager")
                }
                if (onLogout != null) {
                    Button(
                        onClick = {
                            showSettingsSheet = false
                            onLogout()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Logout")
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
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text("Folder name") }
                )
            },
            confirmButton = {
                TextButton(onClick = { createFolder(newFolderName) }) {
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
            title = { Text("New text file") },
            text = {
                OutlinedTextField(
                    value = newTextFileName,
                    onValueChange = { newTextFileName = it },
                    singleLine = true,
                    label = { Text("File name") }
                )
            },
            confirmButton = {
                TextButton(onClick = { createTextFile(newTextFileName) }) {
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
            title = { Text("Item info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Name: ${item.name}")
                    Text("Type: ${if (item.type == "dir") "Directory" else "File"}")
                    Text("Favorite: ${if (item.isFavorite) "Yes" else "No"}")
                    Text("Shared: ${if (item.isShared) "Yes" else "No"}")
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

    renameItem?.let { item ->
        AlertDialog(
            onDismissRequest = {
                renameItem = null
                renameText = ""
            },
            title = { Text("Rename") },
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
            title = { Text("Delete") },
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

    if (overwriteUploadTargetPath != null && overwriteUploadUri != null) {
        AlertDialog(
            onDismissRequest = {
                overwriteUploadTargetPath = null
                overwriteUploadUri = null
                pendingUploadUri = null
                pendingUploadName = null
            },
            title = { Text("Replace file?") },
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

        shareDialogItem?.let { item ->
            AlertDialog(
                onDismissRequest = {
                    shareDialogItem = null
                    shareDialogUrl = ""
                    shareDialogStatus = ""
                    shareDialogExistingToken = null
                    shareDialogExpiry = defaultShareExpiryOption()
                },
                title = { Text("Share") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(item.name)

                        if (shareDialogUrl.isBlank()) {
                            Text(
                                text = "Valid for",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            SHARE_EXPIRY_OPTIONS.forEach { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { shareDialogExpiry = option }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = shareDialogExpiry == option,
                                        onClick = { shareDialogExpiry = option }
                                    )
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (shareDialogUrl.isNotBlank()) {
                            OutlinedTextField(
                                value = shareDialogUrl,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Share link") }
                            )

                            Text(
                                text = "To change link validity, revoke this link and create a new one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (shareDialogStatus.isNotBlank()) {
                            Text(
                                text = shareDialogStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (shareDialogUrl.isBlank()) {
                            TextButton(
                                onClick = { createShareFor(item, shareDialogExpiry.expiresSec) }
                            ) {
                                Text("Create")
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val ok = copyText(context, shareDialogUrl)
                                        shareDialogStatus = if (ok) "Copied" else "Copy failed"
                                    }
                                }
                            ) {
                                Text("Copy")
                            }

                            TextButton(
                                onClick = { revokeShareForCurrentDialog() }
                            ) {
                                Text("Revoke")
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            shareDialogItem = null
                            shareDialogUrl = ""
                            shareDialogStatus = ""
                            shareDialogExistingToken = null
                            shareDialogExpiry = defaultShareExpiryOption()
                        }
                    ) {
                        Text("Close")
                    }
                }
            )
        }
        if (imagePreviewStartIndex != null && imagePreviewItems.isNotEmpty()) {
            ImagePreviewScreen(
                filesRepository = filesRepository,
                currentPath = currentPath,
                images = imagePreviewItems,
                initialIndex = imagePreviewStartIndex!!,
                onClose = {
                    imagePreviewStartIndex = null
                    imagePreviewItems = emptyList()
                }
            )
        }
        if (showSharesManager) {
            SharesManagerScreen(
                filesRepository = filesRepository,
                onClose = {
                    showSharesManager = false
                    refreshCurrent()
                }
            )
        }
        if (textEditorPath != null && textEditorName != null) {
            TextEditorScreen(
                filesRepository = filesRepository,
                relPath = textEditorPath!!,
                displayName = textEditorName!!,
                onClose = {
                    textEditorPath = null
                    textEditorName = null
                },
                onSaved = {
                    load(currentPath)
                }
            )
        }
    }
}
private data class ShareExpiryOption(
    val label: String,
    val expiresSec: Long?
)

private val SHARE_EXPIRY_OPTIONS = listOf(
    ShareExpiryOption("1 hour", 3600L),
    ShareExpiryOption("1 day", 86400L),
    ShareExpiryOption("7 days", 7L * 86400L),
    ShareExpiryOption("Never", null)
)

private fun defaultShareExpiryOption(): ShareExpiryOption {
    return SHARE_EXPIRY_OPTIONS.first { it.expiresSec == 86400L }
}

private fun shareExpiryLabel(expiresSec: Long?): String {
    return when (expiresSec) {
        3600L -> "1 hour"
        86400L -> "1 day"
        7L * 86400L -> "7 days"
        null -> "never"
        else -> "${expiresSec}s"
    }
}
@Composable
private fun SettingsStorageSection(
    storage: MeStorageResponse?,
    storageStatus: String
) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Storage",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                storage != null -> {
                    val allocated = storage.storage_state == "allocated"
                    val progress = if (storage.quota_bytes > 0L) {
                        (storage.used_bytes.toFloat() / storage.quota_bytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    val accentColor = when (storage.warn_level?.lowercase(Locale.getDefault())) {
                        "crit" -> MaterialTheme.colorScheme.error
                        "warn" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }

                    if (!allocated) {
                        Text(
                            text = "Storage not allocated yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "${formatBytes(storage.used_bytes)} / ${formatBytes(storage.quota_bytes)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(4.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "${storage.used_percent.toInt()}% used",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accentColor
                        )

                        if (storage.partial) {
                            Text(
                                text = "Usage is approximate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                storageStatus.isNotBlank() -> {
                    Text(
                        text = storageStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                else -> {
                    Text(
                        text = "Storage info not loaded",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    item: FileItemDto,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMenuAction: (String, FileItemDto) -> Unit
) {
    val isDir = item.type == "dir"
    var menuExpanded by remember { mutableStateOf(false) }

    val typeAndSize = if (isDir) {
        "Directory"
    } else {
        "File • ${formatBytes(item.size_bytes ?: 0)}"
    }

    val dateText = item.mtime_unix?.takeIf { it > 0 }?.let { formatUnixTime(it) } ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isDir) FontWeight.SemiBold else FontWeight.Normal
            )

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.size(width = 20.dp, height = 40.dp)
                ) {
                    if (item.isFavorite) {
                        Text(
                            text = "★",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    if (item.isShared) {
                        Text(
                            text = "🔗",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                FileIcon(
                    item = item,
                    modifier = Modifier.size(26.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = typeAndSize,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (dateText.isNotBlank()) {
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (isDir) {
                        Text(
                            text = "Open",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

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
                            text = { Text(if (item.isFavorite) "Remove from favorites" else "Add to favorites") },
                            onClick = {
                                menuExpanded = false
                                onMenuAction("ToggleFavorite", item)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(if (item.isShared) "Shared…" else "Share") },
                            onClick = {
                                menuExpanded = false
                                onMenuAction("Share", item)
                            }
                        )

                        if (!isDir && isProbablyImageFile(item.name)) {
                            DropdownMenuItem(
                                text = { Text("Open preview") },
                                onClick = {
                                    menuExpanded = false
                                    onMenuAction("Preview", item)
                                }
                            )
                        }

                        if (!isDir && isProbablyTextFile(item.name)) {
                            DropdownMenuItem(
                                text = { Text("Edit text") },
                                onClick = {
                                    menuExpanded = false
                                    onMenuAction("EditText", item)
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
        Image(
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

private suspend fun copyText(context: Context, text: String): Boolean {
    return try {
        withContext(Dispatchers.Main) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("share link", text)
            clipboard.setPrimaryClip(clip)
        }
        true
    } catch (_: Exception) {
        false
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