package com.pqnas.mobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.api.FileItemDto
import com.pqnas.mobile.R
import com.pqnas.mobile.BuildConfig
import com.pqnas.mobile.api.MeStorageResponse
import com.pqnas.mobile.api.DropZoneInfo
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.pqnas.mobile.files.FileTypeIcons
import com.pqnas.mobile.files.FilesRepository
import com.pqnas.mobile.files.SvgIconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import androidx.compose.material3.RadioButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.pqnas.mobile.api.WorkspaceListItemDto
import com.pqnas.mobile.files.FileScope
import com.pqnas.mobile.files.FileListCache
import com.pqnas.mobile.files.ScopedFilesOps
import com.pqnas.mobile.files.listWorkspaces
import okhttp3.RequestBody.Companion.toRequestBody
import com.pqnas.mobile.files.stageUriToTempFile
import java.io.File
import org.json.JSONObject
import com.pqnas.mobile.echostack.EchoStackRepository
import androidx.compose.material.icons.filled.Lock


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    filesRepository: FilesRepository,
    onLogout: (() -> Unit)? = null,
    onBeforeExternalPicker: () -> Unit = {}
) {
    val context = LocalContext.current
    val initialFileListCache = remember(context) {
        FileListCache(context.applicationContext)
    }
    val initialCachedUserRoot = remember(filesRepository, initialFileListCache) {
        initialFileListCache.load(
            namespace = filesRepository.baseUrlForDisplay(),
            scope = FileScope.User,
            path = null
        )
    }

    var currentPath by remember { mutableStateOf<String?>(initialCachedUserRoot?.path) }
    var items by remember {
        mutableStateOf<List<FileItemDto>>(initialCachedUserRoot?.items ?: emptyList())
    }
    var status by remember {
        mutableStateOf(
            if (initialCachedUserRoot != null) {
                "Cached files — refreshing..."
            } else {
                "Loading..."
            }
        )
    }
    var listLoading by remember { mutableStateOf(initialCachedUserRoot == null) }
    var startupEmptyStateGrace by remember { mutableStateOf(initialCachedUserRoot == null) }
    var myStorage by remember { mutableStateOf<MeStorageResponse?>(null) }
    var storageStatus by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var commentedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    var shareDialogItem by remember { mutableStateOf<FileItemDto?>(null) }
    var shareDialogUrl by remember { mutableStateOf("") }
    var shareDialogStatus by remember { mutableStateOf("") }
    var shareDialogExistingToken by remember { mutableStateOf<String?>(null) }
    var shareDialogExpiry by remember { mutableStateOf(defaultShareExpiryOption()) }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAppsSheet by remember { mutableStateOf(false) }
    var showSharesManager by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var dropZoneAvailable by remember { mutableStateOf(false) }
    var echoStackAvailable by remember { mutableStateOf(false) }
    var appsChecked by remember { mutableStateOf(false) }
    var showDropZoneSheet by remember { mutableStateOf(false) }
    var dropZones by remember { mutableStateOf<List<DropZoneInfo>>(emptyList()) }
    var dropZoneLoading by remember { mutableStateOf(false) }
    var dropZoneCreating by remember { mutableStateOf(false) }
    var dropZoneStatus by remember { mutableStateOf("") }
    var dropZoneLatestUrl by remember { mutableStateOf("") }
    var showEchoStackScreen by remember { mutableStateOf(false) }

    var dropZoneName by remember { mutableStateOf("Drop Zone") }
    var dropZoneDestination by remember { mutableStateOf("") }
    var dropZonePassword by remember { mutableStateOf("") }

    var infoItem by remember { mutableStateOf<FileItemDto?>(null) }
    var infoNoteText by remember { mutableStateOf("") }
    var infoNoteOriginalText by remember { mutableStateOf("") }
    var infoNoteLoading by remember { mutableStateOf(false) }
    var infoNoteSaving by remember { mutableStateOf(false) }
    var infoNoteStatus by remember { mutableStateOf("") }
    var versionsItem by remember { mutableStateOf<FileItemDto?>(null) }
    var pendingDownloadItem by remember { mutableStateOf<FileItemDto?>(null) }
    var renameItem by remember { mutableStateOf<FileItemDto?>(null) }
    var pendingUploadUri by remember { mutableStateOf<Uri?>(null) }
    var pendingUploadName by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var moveCopyItem by remember { mutableStateOf<FileItemDto?>(null) }
    var moveCopyMode by remember { mutableStateOf("Move") }
    var moveCopyDestination by remember { mutableStateOf("") }
    var moveCopyPickerPath by remember { mutableStateOf<String?>(null) }
    var moveCopyPickerFolders by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var moveCopyPickerLoading by remember { mutableStateOf(false) }
    var moveCopyPickerStatus by remember { mutableStateOf("") }
    var deleteItem by remember { mutableStateOf<FileItemDto?>(null) }
    var imagePreviewItems by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var imagePreviewStartIndex by remember { mutableStateOf<Int?>(null) }
    var audioPlayerItems by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var audioPlayerStartIndex by remember { mutableStateOf<Int?>(null) }
    var videoPlayerItems by remember { mutableStateOf<List<FileItemDto>>(emptyList()) }
    var videoPlayerStartIndex by remember { mutableStateOf<Int?>(null) }
    var textEditorName by remember { mutableStateOf<String?>(null) }
    var textEditorPath by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val mainThreadHandler = remember { Handler(Looper.getMainLooper()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scopedOps = remember(filesRepository, context) {
        ScopedFilesOps(filesRepository, context.applicationContext)
    }
    val fileListCache = initialFileListCache
    val thumbnailImageLoader = rememberFileThumbnailImageLoader(filesRepository)

    var currentScope by remember { mutableStateOf<FileScope>(FileScope.User) }
    var workspaces by remember { mutableStateOf<List<WorkspaceListItemDto>>(emptyList()) }
    var loadGeneration by remember { mutableStateOf(0) }

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

    fun isInternalPqnasFolder(item: FileItemDto): Boolean {
        if (item.type != "dir") return false

        val n = item.name.trim().lowercase(Locale.getDefault())

        return n == ".pqnas_activity" ||
                n == ".pqnas_echostack" ||
                n == ".pqnas-echostack" ||
                n == ".pqnas" ||
                n.startsWith(".pqnas_") ||
                n.startsWith(".pqnas-")
    }

    fun visibleFileItems(source: List<FileItemDto>): List<FileItemDto> =
        source.filterNot { isInternalPqnasFolder(it) }

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
        loadGeneration += 1
        val requestGeneration = loadGeneration
        val scopeSnapshot = currentScope

        val cacheNamespace = filesRepository.baseUrlForDisplay()
        val cached = fileListCache.load(
            namespace = cacheNamespace,
            scope = scopeSnapshot,
            path = path
        )

        if (cached != null) {
            currentPath = cached.path
            val cachedVisibleItems = visibleFileItems(cached.items)

            items = if (favoritesOnly) {
                cachedVisibleItems.filter { it.isFavorite }
            } else {
                cachedVisibleItems
            }
            listLoading = false
            startupEmptyStateGrace = false
            status = "Cached files — refreshing..."
        } else {
            listLoading = true
            status = "Loading..."
        }

        commentedPaths = emptySet()

        scope.launch {
            try {
                val resp = scopedOps.list(scopeSnapshot, path)

                if (requestGeneration != loadGeneration) return@launch

                val baseItems = visibleFileItems(resp.items)
                    .sortedWith(
                        compareBy<FileItemDto> { it.type != "dir" }
                            .thenBy { it.name.lowercase(Locale.getDefault()) }
                    )

                currentPath = if (resp.path.isBlank()) null else resp.path

                fileListCache.save(
                    namespace = cacheNamespace,
                    scope = scopeSnapshot,
                    path = currentPath,
                    items = baseItems
                )

                if (!favoritesOnly) {
                    items = baseItems
                    status = "OK"
                } else {
                    items = emptyList()
                    status = "Loading favorites..."
                }

                listLoading = false

                val favsDeferred = async {
                    runCatching { filesRepository.getFavorites() }.getOrNull()
                }

                val sharesDeferred = async {
                    runCatching { scopedOps.getShares(scopeSnapshot) }.getOrNull()
                }

                val locksDeferred = async {
                    val lockPaths = baseItems.map { item ->
                        normalizeRelPath(buildItemPath(resp.path.ifBlank { null }, item.name))
                    }

                    runCatching {
                        filesRepository.getFileLockStatusBatch(scopeSnapshot, lockPaths)
                    }.getOrNull()
                }

                val notesDeferred = async {
                    val notePaths = baseItems.map { item ->
                        normalizeRelPath(buildItemPath(resp.path.ifBlank { null }, item.name))
                    }

                    runCatching {
                        scopedOps.resolveFileNotes(scopeSnapshot, notePaths)
                    }.getOrNull()
                }

                val storageDeferred = async {
                    runCatching { filesRepository.getMyStorage() }
                }

                val favs = favsDeferred.await()
                val shares = sharesDeferred.await()
                val lockResp = locksDeferred.await()
                val notesResp = notesDeferred.await()

                if (requestGeneration != loadGeneration) return@launch

                commentedPaths = notesResp
                    ?.notes
                    ?.filterValues { note ->
                        note.has_description || note.description.isNotBlank()
                    }
                    ?.keys
                    ?.map { normalizeRelPath(it) }
                    ?.toSet()
                    ?: emptySet()

                val favoriteKeys = favs?.items?.map {
                    favoriteKey(it.type, it.path)
                }?.toSet() ?: emptySet()

                val shareKeys = shares?.shares?.map {
                    shareKey(it.type, it.path)
                }?.toSet() ?: emptySet()
                val lockMap = lockResp?.locks ?: emptyMap()
                val mergedItems = baseItems.map { item ->
                    val fullItemPath = normalizeRelPath(
                        buildItemPath(resp.path.ifBlank { null }, item.name)
                    )
                    val lock = lockMap[fullItemPath]

                    item.copy(
                        isFavorite = favoriteKeys.contains(
                            favoriteKey(item.type, fullItemPath)
                        ),
                        isShared = shareKeys.contains(
                            shareKey(item.type, fullItemPath)
                        ),
                        is_locked = lock != null,
                        locked = lock != null,
                        lock_note = lock?.note,
                        locked_by_fp = lock?.locked_by_fp_short,
                        locked_by_display = lock?.locked_by_label ?: lock?.locked_by_fp_short,
                        lock_expires_at_epoch = lock?.expires_at_epoch
                    )
                }

                items = if (favoritesOnly) {
                    mergedItems.filter { it.isFavorite }
                } else {
                    mergedItems
                }

                fileListCache.save(
                    namespace = cacheNamespace,
                    scope = scopeSnapshot,
                    path = currentPath,
                    items = mergedItems
                )

                status = "OK"

                val storageResult = storageDeferred.await()

                if (requestGeneration != loadGeneration) return@launch

                storageResult.fold(
                    onSuccess = {
                        myStorage = it
                        storageStatus = ""
                    },
                    onFailure = { e ->
                        myStorage = null
                        storageStatus = friendlyHttpMessage("Storage", e)
                    }
                )
            } catch (e: Exception) {
                if (requestGeneration != loadGeneration) return@launch
                listLoading = false
                status = friendlyHttpMessage("Load", e)
            }
        }
    }
    fun refreshCurrent() {
        load(currentPath)
    }
    fun switchToUserScope() {
        currentScope = FileScope.User
        currentPath = null
        load(null)
    }

    fun switchToWorkspaceScope(ws: WorkspaceListItemDto) {
        currentScope = FileScope.Workspace(
            workspaceId = ws.workspace_id,
            workspaceName = ws.name,
            workspaceRole = ws.role
        )
        currentPath = null
        load(null)
    }
    fun refreshWorkspaces() {
        scope.launch {
            try {
                val resp = filesRepository.listWorkspaces()
                workspaces = if (resp.ok) resp.workspaces else emptyList()

                val activeWorkspaceId = (currentScope as? FileScope.Workspace)?.workspaceId
                if (activeWorkspaceId != null) {
                    val stillExists = workspaces.any { it.workspace_id == activeWorkspaceId }
                    if (!stillExists) {
                        currentScope = FileScope.User
                        currentPath = null
                    }
                }
            } catch (_: Exception) {
                workspaces = emptyList()
            }
        }
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
                val shares = scopedOps.getShares(currentScope)
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
                val resp = scopedOps.createShare(currentScope,
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
    fun openAudioPlayer(item: FileItemDto) {
        if (item.type != "file") return
        if (!isProbablyAudioFile(item.name)) return

        val visibleAudioFiles = items.filter { it.type == "file" && isProbablyAudioFile(it.name) }
        val idx = visibleAudioFiles.indexOfFirst { it.name == item.name }
        if (idx < 0) return

        audioPlayerItems = visibleAudioFiles
        audioPlayerStartIndex = idx
    }

    fun openVideoPlayer(item: FileItemDto) {
        if (item.type != "file") return
        if (!isProbablyVideoFile(item.name)) return

        val visibleVideoFiles = items.filter { it.type == "file" && isProbablyVideoFile(it.name) }
        val idx = visibleVideoFiles.indexOfFirst { it.name == item.name }
        if (idx < 0) return

        videoPlayerItems = visibleVideoFiles
        videoPlayerStartIndex = idx
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

    fun openInfoDialog(item: FileItemDto) {
        infoItem = item
        infoNoteText = ""
        infoNoteOriginalText = ""
        infoNoteStatus = ""
        infoNoteLoading = true

        val path = itemFullPath(item)
        val scopeSnapshot = currentScope

        scope.launch {
            try {
                val resp = scopedOps.getFileNote(scopeSnapshot, path)
                val desc = resp.note?.description.orEmpty()
                infoNoteText = desc
                infoNoteOriginalText = desc
                infoNoteStatus = if (desc.isBlank()) "No comment yet." else ""
            } catch (e: Exception) {
                infoNoteStatus = friendlyHttpMessage("Load comment", e)
            } finally {
                infoNoteLoading = false
            }
        }
    }

    fun closeInfoDialog() {
        infoItem = null
        infoNoteText = ""
        infoNoteOriginalText = ""
        infoNoteStatus = ""
        infoNoteLoading = false
        infoNoteSaving = false
    }

    fun saveInfoComment(item: FileItemDto, description: String) {
        if (!scopedOps.canWrite(currentScope)) {
            val msg = "You do not have write access here."
            infoNoteStatus = msg
            status = msg
            scope.launch { snackbarHostState.showSnackbar(msg) }
            return
        }

        val path = itemFullPath(item)
        val scopeSnapshot = currentScope
        val cleanDescription = description.trim()

        scope.launch {
            try {
                infoNoteSaving = true
                infoNoteStatus = "Saving comment..."

                scopedOps.saveFileNote(
                    scope = scopeSnapshot,
                    path = path,
                    itemKind = item.type,
                    description = cleanDescription
                )

                infoNoteText = cleanDescription
                infoNoteOriginalText = cleanDescription
                infoNoteStatus = if (cleanDescription.isBlank()) {
                    "Comment cleared."
                } else {
                    "Comment saved."
                }

                status = "OK"
                snackbarHostState.showSnackbar(infoNoteStatus)
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage("Save comment", e)
                infoNoteStatus = msg
                status = msg
                snackbarHostState.showSnackbar(msg)
            } finally {
                infoNoteSaving = false
            }
        }
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
                scopedOps.mkdir(currentScope, path)
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
                val emptyBody = ByteArray(0).toRequestBody(null)

                scopedOps.upload(
                    scope = currentScope,
                    path = path,
                    body = emptyBody,
                    overwrite = false
                )

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

    fun loadMoveCopyPicker(path: String?) {
        val cleanPath = normalizeRelPath(path)
        val pathArg = cleanPath.ifBlank { null }

        moveCopyPickerPath = pathArg
        moveCopyDestination = cleanPath
        moveCopyPickerLoading = true
        moveCopyPickerStatus = ""

        scope.launch {
            try {
                val resp = scopedOps.list(currentScope, pathArg)

                moveCopyPickerFolders = visibleFileItems(resp.items)
                    .filter { it.type == "dir" }
                    .sortedBy { it.name.lowercase(Locale.getDefault()) }

                moveCopyPickerPath = if (resp.path.isBlank()) null else normalizeRelPath(resp.path)
                moveCopyDestination = normalizeRelPath(moveCopyPickerPath)
            } catch (e: Exception) {
                moveCopyPickerFolders = emptyList()
                moveCopyPickerStatus = friendlyHttpMessage("Folders", e)
            } finally {
                moveCopyPickerLoading = false
            }
        }
    }

    fun openMoveCopyDialog(mode: String, item: FileItemDto) {
        if (!scopedOps.canWrite(currentScope)) {
            val msg = "You do not have write access here."
            status = msg
            scope.launch { snackbarHostState.showSnackbar(msg) }
            return
        }

        if (mode == "Move" && item.isLocked) {
            val msg = "${item.name} is locked. Unlock it before moving."
            status = msg
            scope.launch { snackbarHostState.showSnackbar(msg) }
            return
        }

        moveCopyMode = mode
        moveCopyItem = item
        moveCopyPickerFolders = emptyList()
        moveCopyPickerStatus = ""
        loadMoveCopyPicker(currentPath)
    }

    fun runMoveCopy(item: FileItemDto, mode: String, destinationDirRaw: String) {
        val destinationDir = normalizeRelPath(destinationDirRaw)

        val fromPath = itemFullPath(item)
        val toPath = normalizeRelPath(
            buildItemPath(
                if (destinationDir.isBlank()) null else destinationDir,
                item.name
            )
        )

        if (toPath.isBlank()) {
            val msg = "Destination cannot be empty."
            status = msg
            scope.launch { snackbarHostState.showSnackbar(msg) }
            return
        }

        if (mode == "Move" && fromPath == toPath) {
            moveCopyItem = null
            moveCopyDestination = ""
            status = "Move cancelled: source and destination are the same."
            return
        }

        scope.launch {
            try {
                status = if (mode == "Copy") "Copying ${item.name}..." else "Moving ${item.name}..."

                if (mode == "Copy") {
                    scopedOps.copy(currentScope, fromPath, toPath)
                } else {
                    scopedOps.move(currentScope, fromPath, toPath)
                }

                moveCopyItem = null
                moveCopyDestination = ""
                status = "OK"
                snackbarHostState.showSnackbar(
                    if (mode == "Copy") "Copied ${item.name}" else "Moved ${item.name}"
                )
                load(currentPath)
            } catch (e: Exception) {
                val msg = friendlyHttpMessage(mode, e)
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
                val body = scopedOps.download(currentScope, fullPath)

                withContext(Dispatchers.IO) {
                    body.use { responseBody ->
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("Could not open destination file.")

                        output.use { out ->
                            responseBody.byteStream().use { input ->
                                input.copyTo(out)
                            }
                            out.flush()
                        }
                    }
                }

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
        var lastProgressUiUpdateAtMs = 0L
        var lastProgressUiBytes = -1L
        var stagedFile: File? = null

        uploadJob = scope.launch {
            try {
                fileName = queryDisplayName(context, uri)?.trim()
                if (fileName.isNullOrBlank()) {
                    val msg = "Upload failed: could not determine file name."
                    status = msg
                    snackbarHostState.showSnackbar(msg)
                    return@launch
                }

                val safeFileName = fileName!!
                val targetPath = buildItemPath(currentPath, safeFileName)

                val existingItem = items.firstOrNull { it.name == safeFileName }
                if (!overwrite && existingItem != null) {
                    overwriteUploadTargetPath = targetPath
                    overwriteUploadUri = uri
                    pendingUploadUri = uri
                    pendingUploadName = safeFileName
                    status = "File already exists: $safeFileName"
                    return@launch
                }

                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                // Large Android document/content URIs may take a while to stage into
                // our cache before chunked upload can begin. Show visible feedback
                // immediately so the app does not feel frozen.
                uploadInProgress = true
                uploadCancelRequested = false
                uploadFileName = safeFileName
                uploadBytesSent = 0L
                uploadBytesTotal = 0L
                status = "Preparing upload: $safeFileName..."

                stagedFile = withContext(Dispatchers.IO) {
                    stageUriToTempFile(
                        context = context,
                        uri = uri,
                        fileNameHint = safeFileName
                    )
                }

                val size = stagedFile!!.length()
                uploadBytesTotal = size

                val onUploadProgress: (Long, Long) -> Unit = { sent, total ->
                    val nowMs = System.currentTimeMillis()
                    val bytesDelta = sent - lastProgressUiBytes
                    val shouldUpdate =
                        sent == total ||
                                lastProgressUiBytes < 0L ||
                                bytesDelta >= 256 * 1024L ||
                                (nowMs - lastProgressUiUpdateAtMs) >= 100L

                    if (shouldUpdate) {
                        lastProgressUiBytes = sent
                        lastProgressUiUpdateAtMs = nowMs
                        mainThreadHandler.post {
                            uploadBytesSent = sent
                            uploadBytesTotal = total
                        }
                    }
                }

                status = "Uploading $safeFileName..."

                scopedOps.uploadTempFile(
                    scope = currentScope,
                    path = targetPath,
                    file = stagedFile!!,
                    mimeType = mimeType,
                    overwrite = overwrite,
                    onProgress = onUploadProgress,
                    isCancelled = { uploadCancelRequested }
                )

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
                val msgText = e.message.orEmpty().lowercase(Locale.getDefault())
                val looksLikeEarlyConflictTransportError =
                    msgText.contains("unexpected end of stream") ||
                            msgText.contains("end of stream") ||
                            msgText.contains("unexpected eof") ||
                            msgText.contains("stream was reset") ||
                            msgText.contains("socket closed")

                val existingItemConflict =
                    !overwrite &&
                            !fileName.isNullOrBlank() &&
                            items.any { it.name == fileName }

                if (!overwrite &&
                    !fileName.isNullOrBlank() &&
                    (http == 409 || (looksLikeEarlyConflictTransportError && existingItemConflict))
                ) {
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
                stagedFile?.delete()
                stagedFile = null
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
        delay(1_200L)
        startupEmptyStateGrace = false
    }

    LaunchedEffect(showAppsSheet) {
        if (showAppsSheet) {
            dropZoneAvailable = filesRepository.isServerAppAvailable("dropzone")
            echoStackAvailable = filesRepository.isServerAppAvailable("echostack")
            appsChecked = true
        }
    }

    fun refreshDropZones() {
        scope.launch {
            dropZoneLoading = true
            dropZoneStatus = ""

            runCatching {
                filesRepository.listDropZones()
            }.onSuccess { r ->
                if (r.ok) {
                    dropZones = r.drop_zones
                    if (dropZones.isEmpty()) {
                        dropZoneStatus = "No Drop Zones yet."
                    }
                } else {
                    dropZoneStatus = r.message ?: r.error ?: "Could not load Drop Zones."
                }
            }.onFailure { e ->
                dropZoneStatus = friendlyHttpMessage("Drop Zone", e)
            }

            dropZoneLoading = false
        }
    }

    fun createDropZoneFromSheet() {
        scope.launch{
            dropZoneCreating = true
            dropZoneStatus = ""
            dropZoneLatestUrl = ""

            runCatching {
                filesRepository.createDropZone(
                    name = dropZoneName,
                    destinationPath = dropZoneDestination,
                    password = dropZonePassword,
                    expiresInSeconds = 7L * 24L * 60L * 60L
                )
            }.onSuccess { r ->
                if (r.ok) {
                    dropZoneLatestUrl = r.full_url.ifBlank { r.url }
                    dropZoneStatus = "Drop Zone created. Link is ready to copy."
                    refreshDropZones()
                } else {
                    dropZoneStatus = r.message ?: r.error ?: "Could not create Drop Zone."
                }
            }.onFailure { e ->
                dropZoneStatus = friendlyHttpMessage("Create Drop Zone", e)
            }

            dropZoneCreating = false
        }
    }

    fun disableDropZoneFromSheet(id: String) {
        scope.launch {
            dropZoneStatus = ""

            runCatching {
                filesRepository.disableDropZone(id, disabled = true)
            }.onSuccess { r ->
                if (r.ok) {
                    dropZoneStatus = "Drop Zone disabled."
                    refreshDropZones()
                } else {
                    dropZoneStatus = r.message ?: r.error ?: "Could not disable Drop Zone."
                }
            }.onFailure { e ->
                dropZoneStatus = friendlyHttpMessage("Disable Drop Zone", e)
            }
        }
    }

    fun copyLatestDropZoneLink() {
        if (dropZoneLatestUrl.isBlank()) return

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("Drop Zone link", dropZoneLatestUrl)
        )

        dropZoneStatus = "Drop Zone link copied."
    }
    LaunchedEffect(Unit) {
        refreshWorkspaces()
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
                    onClick = { showAppsSheet = true }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_apps_24),
                        contentDescription = "Apps"
                    )
                }

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

            FilesScopeSection(
                currentScope = currentScope,
                workspaces = workspaces.map { ws ->
                    WorkspaceScopeOption(
                        workspaceId = ws.workspace_id,
                        label = ws.name.ifBlank { ws.workspace_id },
                        role = ws.role
                    )
                },
                onSelectUserScope = {
                    switchToUserScope()
                },
                onSelectWorkspaceScope = { ws ->
                    currentScope = FileScope.Workspace(
                        workspaceId = ws.workspaceId,
                        workspaceName = ws.label,
                        workspaceRole = ws.role
                    )
                    currentPath = null
                    load(null)
                }
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

            if (uploadInProgress) {
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
                            text = uploadFileName?.let {
                                when {
                                    uploadBytesTotal <= 0L -> "Preparing $it..."
                                    uploadBytesSent >= uploadBytesTotal -> "Finalizing $it..."
                                    else -> "Uploading $it"
                                }
                            } ?: when {
                                uploadBytesTotal <= 0L -> "Preparing upload..."
                                uploadBytesSent >= uploadBytesTotal -> "Finalizing upload..."
                                else -> "Uploading..."
                            },
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
                            text = if (uploadBytesTotal <= 0L) {
                                "Gathering file... please wait"
                            } else if (uploadBytesSent >= uploadBytesTotal) {
                                "Processing on server... please wait"
                            } else {
                                "${((uploadBytesSent * 100) / uploadBytesTotal).coerceIn(0, 100)}% • ${
                                    formatBytes(uploadBytesSent)
                                } / ${formatBytes(uploadBytesTotal)}"
                            },
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
                            text = when {
                                (listLoading || startupEmptyStateGrace) -> "Loading files..."
                                favoritesOnly -> "No favorites here"
                                else -> "No files here"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = when {
                                (listLoading || startupEmptyStateGrace) ->
                                    "Contacting DNA-Nexus server..."
                                favoritesOnly ->
                                    "This folder has no favorite items."
                                else ->
                                    "This folder is empty or could not be loaded."
                            },
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
                                leadingVisual = {
                                    val fullItemPath = normalizeRelPath(
                                        buildItemPath(currentPath, item.name)
                                    )
                                    val hasComment = commentedPaths.contains(fullItemPath)

                                    Box(
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        FileLeadingVisual(
                                            filesRepository = filesRepository,
                                            imageLoader = thumbnailImageLoader,
                                            fileScope = currentScope,
                                            currentPath = currentPath,
                                            item = item,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        if (hasComment) {
                                            Text(
                                                text = "✎",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = MaterialTheme.shapes.small
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                },
                                onOpen = {
                                    if (item.type == "dir") {
                                        val next = listOfNotNull(currentPath, item.name)
                                            .joinToString("/")
                                        load(next)
                                    } else if (isProbablyImageFile(item.name)) {
                                        openImagePreview(item)
                                    } else if (isProbablyAudioFile(item.name)) {
                                        openAudioPlayer(item)
                                    } else if (isProbablyVideoFile(item.name)) {
                                        openVideoPlayer(item)
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
                                        "PlayAudio" -> openAudioPlayer(clickedItem)
                                        "PlayVideo" -> openVideoPlayer(clickedItem)
                                        "EditText" -> openTextEditor(clickedItem)
                                        "ToggleFavorite" -> toggleFavorite(clickedItem)
                                        "Share" -> openShareDialog(clickedItem)
                                        "Info" -> openInfoDialog(clickedItem)
                                        "Versions" -> versionsItem = clickedItem
                                        "Download" -> {
                                            if (clickedItem.type == "dir") {
                                                status = "Folder download not implemented yet: ${clickedItem.name}"
                                            } else {
                                                pendingDownloadItem = clickedItem
                                                onBeforeExternalPicker()
                                                createDocumentLauncher.launch(clickedItem.name)
                                            }
                                        }
                                        "Move" -> openMoveCopyDialog("Move", item)
            "Copy" -> openMoveCopyDialog("Copy", item)
            "Rename" -> {
                                            if (clickedItem.isLocked) {
                                                val msg = "${clickedItem.name} is locked. Unlock it before renaming."
                                                status = msg
                                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                            } else {
                                                renameItem = clickedItem
                                                renameText = clickedItem.name
                                            }
                                        }
                                        "Delete" -> {
                                            if (clickedItem.isLocked) {
                                                val msg = "${clickedItem.name} is locked. Unlock it before deleting."
                                                status = msg
                                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                            } else {
                                                deleteItem = clickedItem
                                            }
                                        }
                                        else -> status = "$action not implemented yet: ${clickedItem.name}"
                                    }
                                }
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        }

                        item {
                            Spacer(Modifier.height(104.dp))
                        }
                    }
                }
            }
        }
        }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                TextButton(
                    onClick = { showAboutDialog = false }
                ) {
                    Text("Close")
                }
            },
            title = {
                Text("About DNA-Nexus Files")
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsAboutSection()
                }
            }
        )
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
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
                    onClick = { showAboutDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("About DNA-Nexus Files")
                }

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
        if (showAppsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAppsSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Apps",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Available DNA-Nexus mobile tools on this server.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (echoStackAvailable) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAppsSheet = false
                                    showEchoStackScreen = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Echo Stack",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = "Save bookmarks and archived web pages.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (dropZoneAvailable) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAppsSheet = false
                                    showDropZoneSheet = true
                                    refreshDropZones()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Drop Zone",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = "Create secure upload links for outsiders.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (appsChecked && !echoStackAvailable && !dropZoneAvailable) {
                        Text(
                            text = "No mobile apps are available on this server yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(
                        onClick = { showAppsSheet = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
        if (showDropZoneSheet) {
            DropZoneScreen(
                zones = dropZones,
                loading = dropZoneLoading,
                creating = dropZoneCreating,
                status = dropZoneStatus,
                latestUrl = dropZoneLatestUrl,
                name = dropZoneName,
                destination = dropZoneDestination,
                password = dropZonePassword,
                onNameChange = { dropZoneName = it },
                onDestinationChange = { dropZoneDestination = it },
                onPasswordChange = { dropZonePassword = it },
                onRefresh = { refreshDropZones() },
                onCreate = { createDropZoneFromSheet() },
                onCopyLatest = { copyLatestDropZoneLink() },
                onDisable = { id -> disableDropZoneFromSheet(id) },
                onClose = { showDropZoneSheet = false }
            )
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
                        onBeforeExternalPicker()
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
            onDismissRequest = { closeInfoDialog() },
            title = { Text("Info") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    val fullPath = itemFullPath(item)
                    val canEditComment = scopedOps.canWrite(currentScope)

                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text("Type: ${if (item.type == "dir") "Folder" else "File"}")
                    Text("Path: /$fullPath")

                    if (item.isFavorite) {
                        Text("Favorite: yes")
                    }

                    if (item.isShared) {
                        Text("Shared: yes")
                    }

                    if (item.isLocked) {
                        Text(
                            text = "Locked" + (item.locked_by_display?.let { " by $it" } ?: ""),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = "Comment",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    when {
                        infoNoteLoading -> {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Loading comment...")
                        }

                        canEditComment -> {
                            OutlinedTextField(
                                value = infoNoteText,
                                onValueChange = { infoNoteText = it },
                                minLines = 3,
                                maxLines = 8,
                                label = { Text("File comment") },
                                placeholder = { Text("Add a note about this file...") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = "Stored on DNA-Nexus server and visible from desktop/mobile.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        infoNoteText.isBlank() -> {
                            Text(
                                text = "No comment yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        else -> {
                            Text(infoNoteText)
                        }
                    }

                    if (infoNoteStatus.isNotBlank()) {
                        Text(
                            text = infoNoteStatus,
                            color = if (infoNoteStatus.contains("failed", ignoreCase = true) ||
                                infoNoteStatus.contains("error", ignoreCase = true)
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            },
            confirmButton = {
                if (scopedOps.canWrite(currentScope)) {
                    TextButton(
                        enabled = !infoNoteLoading &&
                                !infoNoteSaving &&
                                infoNoteText.trim() != infoNoteOriginalText.trim(),
                        onClick = { saveInfoComment(item, infoNoteText) }
                    ) {
                        Text(if (infoNoteSaving) "Saving..." else "Save")
                    }
                }
            },
            dismissButton = {
                Row {
                    if (scopedOps.canWrite(currentScope) && infoNoteOriginalText.isNotBlank()) {
                        TextButton(
                            enabled = !infoNoteLoading && !infoNoteSaving,
                            onClick = { saveInfoComment(item, "") }
                        ) {
                            Text("Clear")
                        }
                    }

                    TextButton(onClick = { closeInfoDialog() }) {
                        Text("Close")
                    }
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
                        if (item.isLocked) {
                            val msg = "${item.name} is locked. Unlock it before renaming."
                            renameItem = null
                            renameText = ""
                            status = msg
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                            return@TextButton
                        }
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
                                scopedOps.move(currentScope, fromPath, toPath)
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

    moveCopyItem?.let { item ->
        AlertDialog(
            onDismissRequest = {
                moveCopyItem = null
                moveCopyDestination = ""
            },
            title = { Text("$moveCopyMode ${item.name}") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Choose destination folder.")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            enabled = !moveCopyPickerLoading,
                            onClick = { loadMoveCopyPicker(null) }
                        ) {
                            Text("Root")
                        }

                        TextButton(
                            enabled = !moveCopyPickerLoading && !moveCopyPickerPath.isNullOrBlank(),
                            onClick = { loadMoveCopyPicker(parentPath(moveCopyPickerPath)) }
                        ) {
                            Text("Up")
                        }

                        TextButton(
                            enabled = !moveCopyPickerLoading,
                            onClick = { loadMoveCopyPicker(moveCopyPickerPath) }
                        ) {
                            Text("Refresh")
                        }
                    }

                    Text(
                        text = "Current: /" + normalizeRelPath(moveCopyPickerPath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Target: /" + normalizeRelPath(
                            buildItemPath(
                                if (moveCopyDestination.isBlank()) null else moveCopyDestination,
                                item.name
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    if (moveCopyPickerLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Loading folders...")
                    } else if (moveCopyPickerFolders.isEmpty()) {
                        Text(
                            text = if (moveCopyPickerStatus.isNotBlank()) {
                                moveCopyPickerStatus
                            } else {
                                "No subfolders here."
                            },
                            color = if (moveCopyPickerStatus.isNotBlank()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            items(
                                moveCopyPickerFolders,
                                key = { folder -> folder.name }
                            ) { folder ->
                                val folderPath = normalizeRelPath(
                                    buildItemPath(moveCopyPickerPath, folder.name)
                                )

                                ListItem(
                                    headlineContent = {
                                        Text("📁 ${folder.name}")
                                    },
                                    supportingContent = {
                                        Text("/$folderPath")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            loadMoveCopyPicker(folderPath)
                                        }
                                )

                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !moveCopyPickerLoading,
                    onClick = {
                        runMoveCopy(item, moveCopyMode, moveCopyDestination)
                    }
                ) {
                    Text(if (moveCopyMode == "Copy") "Copy here" else "Move here")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        moveCopyItem = null
                        moveCopyDestination = ""
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
            title = { Text("Move to trash") },
            text = {
                Text("Move ${if (item.type == "dir") "folder" else "file"} \"${item.name}\" to trash?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val path = buildItemPath(currentPath, item.name)
                                scopedOps.delete(currentScope, path)
                                deleteItem = null
                                status = "OK"
                                snackbarHostState.showSnackbar("Moved ${item.name} to trash")
                                load(currentPath)
                            } catch (e: Exception) {
                                val msg = friendlyHttpMessage("Delete", e)
                                status = msg
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    }
                ) {
                    Text("Move to trash")
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
                            overwriteUploadTargetPath = null
                            overwriteUploadUri = null
                            pendingUploadUri = null
                            status = "Replacing ${pendingUploadName ?: "file"}..."
                            pendingUploadName = null
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

        versionsItem?.let { item ->
            FileVersionsSheet(
                filesRepository = filesRepository,
                fileScope = currentScope,
                relPath = buildItemPath(currentPath, item.name),
                displayName = item.name,
                onDismiss = {
                    versionsItem = null
                },
                onRestored = { message ->
                    status = message
                    load(currentPath)
                }
            )
        }

        if (imagePreviewStartIndex != null && imagePreviewItems.isNotEmpty()) {
            ImagePreviewScreen(
                filesRepository = filesRepository,
                fileScope = currentScope,
                currentPath = currentPath,
                images = imagePreviewItems,
                initialIndex = imagePreviewStartIndex!!,
                onClose = {
                    imagePreviewStartIndex = null
                    imagePreviewItems = emptyList()
                }
            )
        }
        audioPlayerStartIndex?.let { startIndex ->
            AudioPlayerScreen(
                filesRepository = filesRepository,
                fileScope = currentScope,
                currentPath = currentPath,
                audioFiles = audioPlayerItems,
                initialIndex = startIndex,
                onClose = {
                    audioPlayerStartIndex = null
                    audioPlayerItems = emptyList()
                }
            )
        }

        videoPlayerStartIndex?.let { startIndex ->
            VideoPlayerScreen(
                filesRepository = filesRepository,
                fileScope = currentScope,
                currentPath = currentPath,
                videoFiles = videoPlayerItems,
                initialIndex = startIndex,
                onClose = {
                    videoPlayerStartIndex = null
                    videoPlayerItems = emptyList()
                }
            )
        }
        if (showSharesManager) {
            SharesManagerScreen(
                filesRepository = filesRepository,
                fileScope = currentScope,
                onClose = {
                    showSharesManager = false
                    refreshCurrent()
                }
            )
        }
        if (showEchoStackScreen) {
            val echoStackRepository = remember(filesRepository) {
                EchoStackRepository(filesRepository.createEchoStackApiInternal())
            }

            EchoStackScreen(
                repository = echoStackRepository,
                onClose = {
                    showEchoStackScreen = false
                }
            )
        }
        if (textEditorPath != null && textEditorName != null) {
            TextEditorScreen(
                filesRepository = filesRepository,
                fileScope = currentScope,
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
    leadingVisual: @Composable () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMenuAction: (String, FileItemDto) -> Unit
) {
    val isDir = item.type == "dir"
    var menuExpanded by remember { mutableStateOf(false) }
    val rowBackground = if (item.isLocked) {
        Color(0x66FFC107)
    } else {
        Color.Transparent
    }

    val typeAndSize = if (isDir) {
        "Directory"
    } else {
        "File • ${formatBytes(item.size_bytes ?: 0)}"
    }

    val dateText = item.mtime_unix?.takeIf { it > 0 }?.let { formatUnixTime(it) } ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isDir || item.isLocked) FontWeight.SemiBold else FontWeight.Normal
                )

                if (item.isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFFFC107)
                    )
                }
            }

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

                leadingVisual()

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
                    if (item.isLocked) {
                        Text(
                            text = item.lockSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFC107),
                            fontWeight = FontWeight.SemiBold
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

                        if (!isDir && isProbablyAudioFile(item.name)) {
                            DropdownMenuItem(
                                text = { Text("Play audio") },
                                onClick = {
                                    menuExpanded = false
                                    onMenuAction("PlayAudio", item)
                                }
                            )
                        }

                        if (!isDir && isProbablyVideoFile(item.name)) {
                            DropdownMenuItem(
                                text = { Text("Play video") },
                                onClick = {
                                    menuExpanded = false
                                    onMenuAction("PlayVideo", item)
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
                        if (!isDir) {
                            DropdownMenuItem(
                                text = { Text("Versions") },
                                onClick = {
                                    menuExpanded = false
                                    onMenuAction("Versions", item)
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
                            text = { Text("Move…") },
                            onClick = {
                                menuExpanded = false
                                onMenuAction("Move", item)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Copy…") },
                            onClick = {
                                menuExpanded = false
                                onMenuAction("Copy", item)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Move to trash") },
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
private fun isProbablyVideoFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return ext in setOf(
        "mp4",
        "m4v",
        "mov",
        "mkv",
        "webm",
        "avi",
        "3gp",
        "3gpp"
    )
}
private fun isProbablyImageFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico")
}

private fun isProbablyAudioFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return ext in setOf(
        "mp3",
        "m4a",
        "aac",
        "wav",
        "ogg",
        "oga",
        "opus",
        "flac",
        "webm"
    )
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
private fun readHttpErrorToken(error: Throwable): String {
    val e = error as? HttpException ?: return ""

    val raw = try {
        e.response()?.errorBody()?.string().orEmpty()
    } catch (_: Exception) {
        ""
    }

    if (raw.isBlank()) return ""

    val json = try {
        JSONObject(raw)
    } catch (_: Exception) {
        return raw.lowercase(Locale.getDefault())
    }

    return listOf(
        json.optString("error").orEmpty(),
        json.optString("code").orEmpty(),
        json.optString("message").orEmpty()
    )
        .joinToString(" ")
        .lowercase(Locale.getDefault())
}
private fun friendlyHttpMessage(
    action: String,
    error: Throwable
): String {
    val serverToken = readHttpErrorToken(error)
    val lowMessage = error.message.orEmpty().lowercase(Locale.getDefault())

    if (
        serverToken.contains("storage_unallocated") ||
        serverToken.contains("storage not allocated") ||
        serverToken.contains("no file storage assigned") ||
        lowMessage.contains("storage_unallocated") ||
        lowMessage.contains("storage not allocated") ||
        lowMessage.contains("no file storage assigned")
    ) {
        return "Storage not allocated yet. Your device is paired, but this account has no file storage assigned. Ask an administrator to allocate storage in DNA-Nexus Server → Admin → User profiles."
    }

    val http = (error as? HttpException)?.code()
        ?: Regex("""\bHTTP\s+(\d{3})\b""")
            .find(error.message.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    if (
        http == 423 ||
        serverToken.contains("locked") ||
        serverToken.contains("file_locked") ||
        lowMessage.contains("locked") ||
        lowMessage.contains("file_locked")
    ) {
        return "$action failed: file is locked. Unlock it before changing or deleting it."
    }
    return when (http) {
        400 -> "$action failed: invalid request."
        401 -> "Session expired. Please pair again."
        403 -> "Access denied."
        404 -> "Item not found."
        409 -> when (action) {
            "Rename" -> "Cannot rename: a file or folder with that name already exists."
            "Move" -> "Cannot move: destination already exists."
            "Delete" -> "Cannot move to trash: item is in a conflicting state."
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
            val type = error::class.java.simpleName
            val msg = error.message?.takeIf { it.isNotBlank() } ?: "unknown error"
            "$action failed: [$type] $msg"
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


@Composable
private fun SettingsAboutSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.cpunk_about),
                contentDescription = "CPUNK DNA-Nexus mascot",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Text(
            text = "About DNA-Nexus Files",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "DNA-Nexus Files was created by and for the CPUNK community. Digital freedom, privacy and safety are what we eat and breathe.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )

        Text(
            text = "Security stack",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "DNA-Nexus ecosystem: ML-KEM-768 • CRYSTALS-Dilithium 5 • AES-256-GCM\nAndroid app: HTTPS + TLS pinning • Android Keystore • AES-GCM encrypted local auth/cache",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

