package com.pqnas.mobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.api.ShareItemDto
import com.pqnas.mobile.files.FilesRepository
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.Instant
import java.util.Locale

private enum class ShareFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    EXPIRED("Expired"),
    NO_EXPIRY("No expiry")
}

private val SHARE_FILTERS = listOf(
    ShareFilter.ALL,
    ShareFilter.ACTIVE,
    ShareFilter.EXPIRED,
    ShareFilter.NO_EXPIRY
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharesManagerScreen(
    filesRepository: FilesRepository,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var shares by remember { mutableStateOf<List<ShareItemDto>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ShareFilter.ALL) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Loading shares...") }
    var revokeItem by remember { mutableStateOf<ShareItemDto?>(null) }

    val baseUrl = remember(filesRepository) {
        filesRepository.baseUrlForDisplay()
    }

    fun loadShares() {
        scope.launch {
            loading = true
            status = "Loading shares..."
            try {
                val resp = filesRepository.getShares()
                if (!resp.ok) {
                    throw IllegalStateException("Share list failed")
                }
                shares = resp.shares
                status = "OK"
            } catch (e: Exception) {
                status = friendlySharesManagerMessage("Load shares", e)
            } finally {
                loading = false
            }
        }
    }

    val filteredShares = filterShares(
        shares = shares,
        query = searchQuery,
        filter = selectedFilter
    )

    BackHandler {
        onClose()
    }

    LaunchedEffect(Unit) {
        loadShares()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Share manager",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { loadShares() },
                            enabled = !loading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search shares") },
                    enabled = !loading
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SHARE_FILTERS) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            enabled = !loading
                        )
                    }
                }

                Text(
                    text = "Showing ${filteredShares.size} / ${shares.size} shares",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (status != "OK") {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (
                            status.contains("failed", ignoreCase = true) ||
                            status.contains("denied", ignoreCase = true) ||
                            status.contains("expired", ignoreCase = true) ||
                            status.contains("not found", ignoreCase = true) ||
                            status.contains("cannot", ignoreCase = true)
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                when {
                    loading && shares.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    filteredShares.isEmpty() -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (shares.isEmpty()) "No shares yet" else "No shares match the current filter",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (shares.isEmpty()) {
                                        "Create a share from the files screen and it will appear here."
                                    } else {
                                        "Try another search or filter."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = filteredShares,
                                key = { share -> shareStableKey(share) }
                            ) { share ->
                                ShareCard(
                                    share = share,
                                    baseUrl = baseUrl,
                                    onCopy = { fullUrl ->
                                        val ok = copyShareText(context, fullUrl)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (ok) "Copied share link" else "Copy failed"
                                            )
                                        }
                                    },
                                    onRevoke = {
                                        revokeItem = share
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    revokeItem?.let { share ->
        AlertDialog(
            onDismissRequest = { revokeItem = null },
            title = { Text("Revoke share?") },
            text = {
                Text("Revoke share for \"${share.path}\"?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val token = share.token
                        if (token.isNullOrBlank()) {
                            revokeItem = null
                            scope.launch {
                                snackbarHostState.showSnackbar("Cannot revoke: share token is missing")
                            }
                            return@TextButton
                        }

                        scope.launch {
                            try {
                                filesRepository.revokeShare(token)
                                revokeItem = null
                                snackbarHostState.showSnackbar("Share revoked")
                                loadShares()
                            } catch (e: Exception) {
                                revokeItem = null
                                snackbarHostState.showSnackbar(
                                    friendlySharesManagerMessage("Revoke share", e)
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Revoke")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { revokeItem = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ShareCard(
    share: ShareItemDto,
    baseUrl: String,
    onCopy: (String) -> Unit,
    onRevoke: () -> Unit
) {
    val fullUrl = fullShareUrl(
        baseUrl = baseUrl,
        url = share.url,
        token = share.token
    )
    val expired = isExpiredIso(share.expires_at)

    val stateText = when {
        expired -> "Expired"
        share.expires_at.isNullOrBlank() -> "No expiry"
        else -> "Active"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = share.path,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${if (share.type == "dir") "Directory" else "File"} • $stateText",
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    expired -> MaterialTheme.colorScheme.error
                    share.expires_at.isNullOrBlank() -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.tertiary
                }
            )

            Text(
                text = "Expires: ${share.expires_at ?: "Never"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Downloads: ${share.downloads ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (fullUrl.isNotBlank()) {
                Text(
                    text = fullUrl,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "Share link not available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = { onCopy(fullUrl) },
                    enabled = fullUrl.isNotBlank()
                ) {
                    Text("Copy link")
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = onRevoke,
                    enabled = !share.token.isNullOrBlank(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Revoke")
                }
            }
        }
    }
}

private fun filterShares(
    shares: List<ShareItemDto>,
    query: String,
    filter: ShareFilter
): List<ShareItemDto> {
    val q = query.trim().lowercase(Locale.getDefault())

    return shares
        .filter { share ->
            when (filter) {
                ShareFilter.ALL -> true
                ShareFilter.ACTIVE -> !isExpiredIso(share.expires_at)
                ShareFilter.EXPIRED -> isExpiredIso(share.expires_at)
                ShareFilter.NO_EXPIRY -> share.expires_at.isNullOrBlank()
            }
        }
        .filter { share ->
            if (q.isBlank()) return@filter true

            val haystack = buildString {
                append(share.path)
                append('\n')
                append(share.type)
                append('\n')
                append(share.url.orEmpty())
                append('\n')
                append(share.token.orEmpty())
                append('\n')
                append(share.expires_at.orEmpty())
            }.lowercase(Locale.getDefault())

            haystack.contains(q)
        }
        .sortedBy { share ->
            share.path.lowercase(Locale.getDefault())
        }
}

private fun shareStableKey(share: ShareItemDto): String {
    return share.token
        ?: "${share.type}:${share.path}:${share.url.orEmpty()}"
}

private fun fullShareUrl(
    baseUrl: String,
    url: String?,
    token: String?
): String {
    val cleanBase = baseUrl.trim().trimEnd('/')

    if (!url.isNullOrBlank()) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        val rel = if (url.startsWith("/")) url else "/$url"
        return cleanBase + rel
    }

    if (!token.isNullOrBlank()) {
        return "$cleanBase/s/$token"
    }

    return ""
}

private fun isExpiredIso(expiresAt: String?): Boolean {
    if (expiresAt.isNullOrBlank()) return false

    return try {
        Instant.parse(expiresAt).toEpochMilli() <= System.currentTimeMillis()
    } catch (_: Exception) {
        false
    }
}

private fun copyShareText(
    context: Context,
    text: String
): Boolean {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("share link", text)
        clipboard.setPrimaryClip(clip)
        true
    } catch (_: Exception) {
        false
    }
}

private fun friendlySharesManagerMessage(
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
        404 -> "Share not found."
        500 -> "$action failed: server error."
        else -> {
            val msg = error.message?.takeIf { it.isNotBlank() } ?: "unknown error"
            "$action failed: $msg"
        }
    }
}