package com.pqnas.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.pqnas.mobile.api.FileItemDto
import com.pqnas.mobile.files.FileScope
import com.pqnas.mobile.files.FilesRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import androidx.media3.common.MimeTypes

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    filesRepository: FilesRepository,
    fileScope: FileScope,
    currentPath: String?,
    videoFiles: List<FileItemDto>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    if (videoFiles.isEmpty()) return

    val context = LocalContext.current

    var currentIndex by remember(videoFiles, initialIndex) {
        mutableStateOf(initialIndex.coerceIn(0, videoFiles.lastIndex))
    }

    val currentVideo = videoFiles[currentIndex]
    val relPath = remember(currentPath, currentVideo.name) {
        buildVideoItemPath(currentPath, currentVideo.name)
    }

    val videoUrl = remember(filesRepository, fileScope, relPath) {
        buildVideoDownloadUrl(
            baseUrl = filesRepository.baseUrlForDisplay(),
            fileScope = fileScope,
            relPath = relPath
        )
    }

    var status by remember(videoUrl) { mutableStateOf("Preparing video...") }
    var errorText by remember(videoUrl) { mutableStateOf("") }

    BackHandler {
        onClose()
    }

    if (videoUrl.isNullOrBlank()) {
        VideoPlayerErrorShell(
            title = currentVideo.name,
            counter = "${currentIndex + 1} / ${videoFiles.size}",
            status = "Could not build video URL",
            errorText = "Invalid server URL or file path.",
            onClose = onClose
        )
        return
    }

    val player = remember(videoUrl) {
        ExoPlayer.Builder(context)
            .build()
    }

    LaunchedEffect(videoUrl) {
        try {
            status = "Preparing video..."
            errorText = ""

            val okHttpClient = filesRepository.createAuthedOkHttpClient()
            val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

            val mediaItem = MediaItem.Builder()
                .setUri(videoUrl)
                .setMimeType(guessVideoMimeType(currentVideo.name))
                .build()
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)

            player.stop()
            player.clearMediaItems()
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            status = "Video failed"
            errorText = friendlyVideoMessage(e)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                status = when (playbackState) {
                    Player.STATE_BUFFERING -> "Buffering..."
                    Player.STATE_READY -> "Ready"
                    Player.STATE_ENDED -> "Ended"
                    Player.STATE_IDLE -> "Idle"
                    else -> "Video"
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                status = "Video failed"
                errorText = buildString {
                    append(friendlyVideoMessage(error))
                    append("\n\n")
                    append("code=")
                    append(error.errorCodeName)
                    append("\n")
                    append("message=")
                    append(error.message ?: "")
                    error.cause?.let {
                        append("\n")
                        append("cause=")
                        append(it::class.java.simpleName)
                        append(": ")
                        append(it.message ?: "")
                    }
                }
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        this.player = player
                        keepScreenOn = true
                    }
                },
                update = { view ->
                    view.player = player
                },
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) {
                    Text("Close", color = Color.White)
                }

                TextButton(
                    onClick = {
                        if (currentIndex > 0) currentIndex -= 1
                    },
                    enabled = currentIndex > 0
                ) {
                    Text(
                        "Prev",
                        color = if (currentIndex > 0) Color.White else Color.Gray
                    )
                }

                TextButton(
                    onClick = {
                        if (currentIndex < videoFiles.lastIndex) currentIndex += 1
                    },
                    enabled = currentIndex < videoFiles.lastIndex
                ) {
                    Text(
                        "Next",
                        color = if (currentIndex < videoFiles.lastIndex) Color.White else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "${currentIndex + 1} / ${videoFiles.size}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = currentVideo.name,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )

            if (errorText.isNotBlank()) {
                Text(
                    text = "$status: $errorText",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerErrorShell(
    title: String,
    counter: String,
    status: String,
    errorText: String,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) {
                    Text("Close", color = Color.White)
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = counter,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "$title\n\n$status\n$errorText",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun buildVideoDownloadUrl(
    baseUrl: String,
    fileScope: FileScope,
    relPath: String
): String? {
    val endpointPath = when (fileScope) {
        FileScope.User -> "/api/v4/files/get"
        is FileScope.Workspace -> "/api/v4/workspaces/files/get"
    }

    val endpoint = "${baseUrl.trim().trimEnd('/')}$endpointPath"
        .toHttpUrlOrNull()
        ?: return null

    return endpoint.newBuilder()
        .apply {
            if (fileScope is FileScope.Workspace) {
                addQueryParameter("workspace_id", fileScope.workspaceId)
            }
        }
        .addQueryParameter("path", relPath)
        .build()
        .toString()
}

private fun buildVideoItemPath(currentPath: String?, itemName: String): String {
    return listOfNotNull(currentPath, itemName)
        .filter { it.isNotBlank() }
        .joinToString("/")
}
private fun guessVideoMimeType(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()

    return when (ext) {
        "mp4", "m4v" -> MimeTypes.VIDEO_MP4
        "webm" -> MimeTypes.VIDEO_WEBM
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "3gp", "3gpp" -> "video/3gpp"
        else -> "application/octet-stream"
    }
}
private fun friendlyVideoMessage(error: Throwable): String {
    val msg = error.message?.trim().orEmpty()

    return when {
        msg.contains("401", ignoreCase = true) ->
            "Session expired. Please pair again."

        msg.contains("403", ignoreCase = true) ->
            "Access denied."

        msg.contains("404", ignoreCase = true) ->
            "Video file not found."

        msg.contains("TLS", ignoreCase = true) ||
                msg.contains("certificate", ignoreCase = true) ->
            "Server TLS identity check failed."

        msg.isNotBlank() ->
            "Playback failed: $msg"

        else ->
            "Playback failed."
    }
}