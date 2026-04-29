package com.pqnas.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import android.graphics.Typeface
import android.graphics.Rect
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.KeyListener
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.height


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
    val context = LocalContext.current
    val scopedOps = remember(filesRepository, context) {
        ScopedFilesOps(filesRepository, context.applicationContext)
    }

    var editorValue by remember(relPath) { mutableStateOf(TextFieldValue("")) }
    var originalText by remember(relPath) { mutableStateOf("") }
    var editorDirty by remember(relPath) { mutableStateOf(false) }
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
    var editMode by remember(relPath) { mutableStateOf(false) }
    var leaseHeartbeatJob by remember(relPath) { mutableStateOf<Job?>(null) }

    val editorBridge = remember(relPath) {
        object {
            var suppressCallbacks = false
            var latestText = ""
        }
    }
    val editorPaddingPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    var editorScrollY by remember(relPath) { mutableIntStateOf(0) }
    var editorScrollRange by remember(relPath) { mutableIntStateOf(0) }
    var editorViewportHeightPx by remember(relPath) { mutableIntStateOf(0) }
    var editorHasFocus by remember(relPath) { mutableStateOf(false) }
    var editorViewRef by remember(relPath) { mutableStateOf<ScrollAwareEditText?>(null) }
    var editorSelectionStart by remember(relPath) { mutableIntStateOf(0) }

    val dirty = editorDirty
    val matches = remember(editorValue.text, findQuery, matchCase) {
        findMatches(
            fullText = editorValue.text,
            query = findQuery,
            matchCase = matchCase
        )
    }
    val findStatus = remember(matches, findQuery, editorSelectionStart) {
        computeFindStatus(
            matches = matches,
            query = findQuery,
            selectedStart = editorSelectionStart
        )
    }

    val editorByteCount = remember(editorValue.text) {
        editorValue.text.toByteArray(Charsets.UTF_8).size.toLong()
    }
    fun currentEditorText(): String {
        return editorBridge.latestText
    }

    fun enterEditMode() {
        if (loading || saving || readOnly) return

        editMode = true

        editorViewRef?.post {
            val view = editorViewRef ?: return@post
            view.requestFocus()
            view.isCursorVisible = true

            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun exitEditMode() {
        editMode = false

        val view = editorViewRef
        view?.clearFocus()

        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
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
            editorBridge.latestText = text
            editorDirty = false
            editorSelectionStart = 0
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

                val textToSave = currentEditorText()

                val resp = scopedOps.writeText(
                    scope = fileScope,
                    path = relPath,
                    text = textToSave,
                    expectedMtimeEpoch = mtimeEpoch,
                    expectedSha256 = sha256
                )

                if (!resp.ok) {
                    throw IllegalStateException(composeApiMessage(resp.error, resp.message, "Write text failed"))
                }

                originalText = textToSave
                editorBridge.latestText = textToSave
                editorValue = TextFieldValue(text = textToSave)
                editorDirty = false
                mtimeEpoch = resp.mtime_epoch ?: mtimeEpoch
                sha256 = resp.sha256 ?: sha256
                saving = false
                editMode = false
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
        val end = (start + findQuery.length).coerceAtMost(currentEditorText().length)
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
        if (editMode || editorHasFocus) {
            exitEditMode()
        } else {
            requestClose()
        }
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
                            onClick = {
                                if (editMode) exitEditMode() else enterEditMode()
                            },
                            enabled = !loading && !saving && !readOnly
                        ) {
                            Text(if (editMode) "Done" else "Edit")
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
                            text = "Encoding: $encoding • ${formatBytes(editorByteCount)}",
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
                val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
                val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
                val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged { editorViewportHeightPx = it.height }
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            ScrollAwareEditText(context).apply {
                                editorViewRef = this
                                onFocusChangedCallback = { focused ->
                                    editorHasFocus = focused
                                }

                                onSelectionChangedCallback = { selStart, _ ->
                                    if (!editorBridge.suppressCallbacks) {
                                        val textLen = text?.length ?: 0
                                        editorSelectionStart = selStart.coerceIn(0, textLen)
                                    }
                                }

                                onScrollMetricsChanged = { newScrollY, newScrollRange ->
                                    editorScrollY = newScrollY
                                    editorScrollRange = newScrollRange
                                }

                                typeface = Typeface.MONOSPACE
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)

                                gravity = Gravity.TOP or Gravity.START
                                inputType = InputType.TYPE_CLASS_TEXT or
                                        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

                                isSingleLine = false
                                maxLines = Int.MAX_VALUE
                                setHorizontallyScrolling(false)
                                showSoftInputOnFocus = true

                                isVerticalScrollBarEnabled = false
                                isHorizontalScrollBarEnabled = false
                                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

                                setPadding(
                                    editorPaddingPx,
                                    editorPaddingPx,
                                    editorPaddingPx,
                                    editorPaddingPx
                                )

                                setBackgroundColor(surfaceColor)
                                setTextColor(onSurfaceColor)
                                setHintTextColor(onSurfaceVariantColor)

                                val editableKeyListener = keyListener
                                setTag(editableKeyListener)

                                setText(editorValue.text)
                                val start = editorValue.selection.start.coerceIn(0, editorValue.text.length)
                                val end = editorValue.selection.end.coerceIn(0, editorValue.text.length)
                                setSelection(start, end)

                                post { publishScrollMetrics() }

                                addTextChangedListener(object : TextWatcher {
                                    override fun beforeTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        count: Int,
                                        after: Int
                                    ) = Unit

                                    override fun onTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        before: Int,
                                        count: Int
                                    ) = Unit

                                    override fun afterTextChanged(s: Editable?) {
                                        if (editorBridge.suppressCallbacks) return

                                        val newText = s?.toString().orEmpty()
                                        editorBridge.latestText = newText
                                        val selStart = selectionStart.coerceIn(0, newText.length)

                                        editorSelectionStart = selStart

                                        val nowDirty = newText != originalText
                                        if (editorDirty != nowDirty) {
                                            editorDirty = nowDirty
                                        }

                                        post { publishScrollMetrics() }
                                    }
                                })
                            }
                        },
                        update = { view ->
                            editorViewRef = view as? ScrollAwareEditText

                            val targetText = editorValue.text
                            val start = editorValue.selection.start.coerceIn(0, targetText.length)
                            val end = editorValue.selection.end.coerceIn(0, targetText.length)

                            view.setBackgroundColor(surfaceColor)
                            view.setTextColor(onSurfaceColor)
                            view.setHintTextColor(onSurfaceVariantColor)

                            val editableKeyListener = view.getTag() as? KeyListener
                            val editableNow = editMode && !loading && !saving && !readOnly
                            val desiredKeyListener = if (editableNow) editableKeyListener else null

                            if (view.keyListener !== desiredKeyListener) {
                                view.keyListener = desiredKeyListener
                            }

                            view.showSoftInputOnFocus = editableNow
                            view.isFocusable = editableNow
                            view.isFocusableInTouchMode = editableNow
                            view.isCursorVisible = editableNow

                            // Do not push stale Compose text back into the native EditText
                            // while there are unsaved native edits. Tapping Save/toolbar can
                            // clear focus before saveFile() reads the current native text.
                            if (!editorDirty && !view.hasFocus() && view.text?.toString() != targetText) {
                                editorBridge.suppressCallbacks = true
                                view.setText(targetText)
                                view.setSelection(start, end)
                                editorSelectionStart = start
                                editorBridge.suppressCallbacks = false
                            } else if (!editorDirty && !view.hasFocus() && (view.selectionStart != start || view.selectionEnd != end)) {
                                editorBridge.suppressCallbacks = true
                                view.setSelection(start, end)
                                editorSelectionStart = start
                                editorBridge.suppressCallbacks = false
                            }

                            (view as? ScrollAwareEditText)?.post {
                                view.publishScrollMetrics()
                            }
                        }
                    )

                    EditorPositionThumb(
                        scrollY = editorScrollY,
                        scrollRange = editorScrollRange,
                        viewportHeightPx = editorViewportHeightPx,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
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
                        onClick = {
                            if (editMode) exitEditMode() else enterEditMode()
                        },
                        enabled = !loading && !saving && !readOnly
                    ) {
                        Text(if (editMode) "Done" else "Edit")
                    }

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
private class ScrollAwareEditText(
    context: android.content.Context
) : android.widget.EditText(context) {

    var onSelectionChangedCallback: ((Int, Int) -> Unit)? = null
    var onScrollMetricsChanged: ((scrollY: Int, scrollRange: Int) -> Unit)? = null
    var onFocusChangedCallback: ((Boolean) -> Unit)? = null

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            clearFocus()
        }
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onFocusChanged(
        focused: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?
    ) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        onFocusChangedCallback?.invoke(focused)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedCallback?.invoke(selStart, selEnd)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        publishScrollMetrics()
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        super.onLayout(changed, left, top, right, bottom)
        publishScrollMetrics()
    }

    private fun maxVerticalScroll(): Int {
        return (computeVerticalScrollRange() - height).coerceAtLeast(0)
    }

    fun publishScrollMetrics() {
        onScrollMetricsChanged?.invoke(scrollY, maxVerticalScroll())
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
@Composable
private fun EditorPositionThumb(
    scrollY: Int,
    scrollRange: Int,
    viewportHeightPx: Int,
    modifier: Modifier = Modifier
) {
    if (viewportHeightPx <= 0 || scrollRange <= 0) return

    val density = LocalDensity.current
    val viewport = viewportHeightPx.toFloat()
    val contentHeight = viewport + scrollRange.toFloat()

    val minThumbHeightPx = with(density) { 36.dp.toPx() }
    val thumbHeightPx = ((viewport * viewport) / contentHeight)
        .coerceAtLeast(minThumbHeightPx)
        .coerceAtMost(viewport)

    val travelPx = (viewport - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx =
        if (scrollRange <= 0) 0f
        else (scrollY.toFloat() / scrollRange.toFloat()) * travelPx

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(10.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = with(density) { thumbOffsetPx.toDp() })
                .width(6.dp)
                .height(with(density) { thumbHeightPx.toDp() })
                .clip(RoundedCornerShape(999.dp))
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                )
        )
    }
}
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups - 1])
}
