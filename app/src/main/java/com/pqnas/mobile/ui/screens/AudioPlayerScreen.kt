package com.pqnas.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.pqnas.mobile.api.FileItemDto
import com.pqnas.mobile.files.FileScope
import com.pqnas.mobile.files.FilesRepository
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import kotlin.math.max

@OptIn(UnstableApi::class)
@Composable
fun AudioPlayerScreen(
    filesRepository: FilesRepository,
    fileScope: FileScope,
    currentPath: String?,
    audioFiles: List<FileItemDto>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    if (audioFiles.isEmpty()) return

    var currentIndex by remember(audioFiles, initialIndex) {
        mutableStateOf(initialIndex.coerceIn(0, audioFiles.lastIndex))
    }

    val currentAudio = audioFiles[currentIndex]
    val relPath = remember(currentPath, currentAudio.name) {
        buildAudioItemPath(currentPath, currentAudio.name)
    }

    val audioUrl = remember(filesRepository, fileScope, relPath) {
        buildAudioDownloadUrl(
            baseUrl = filesRepository.baseUrlForDisplay(),
            fileScope = fileScope,
            relPath = relPath
        )
    }

    var status by remember(audioUrl) { mutableStateOf("Preparing audio...") }
    var errorText by remember(audioUrl) { mutableStateOf("") }
    var isPlaying by remember(audioUrl) { mutableStateOf(false) }
    var durationMs by remember(audioUrl) { mutableLongStateOf(0L) }
    var positionMs by remember(audioUrl) { mutableLongStateOf(0L) }

    BackHandler {
        onClose()
    }

    if (audioUrl.isNullOrBlank()) {
        AudioPlayerShell(
            title = currentAudio.name,
            counter = "${currentIndex + 1} / ${audioFiles.size}",
            status = "Could not build audio URL",
            errorText = "Invalid server URL or file path.",
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            onClose = onClose,
            onPlayPause = {},
            onSeek = {},
            onPrev = { if (currentIndex > 0) currentIndex -= 1 },
            onNext = { if (currentIndex < audioFiles.lastIndex) currentIndex += 1 },
            canPrev = currentIndex > 0,
            canNext = currentIndex < audioFiles.lastIndex
        )
        return
    }

    val context = LocalContext.current

    val player = remember(audioUrl) {
        ExoPlayer.Builder(context)
            .build()
    }

    /*
     * We create the MediaSource with DNA-Nexus' authenticated, pinned-TLS OkHttp client.
     * This is important: normal media players would not know about our Bearer token,
     * token refresh, or QR-pinned server certificate.
     */
    LaunchedEffect(audioUrl) {
        try {
            status = "Preparing audio..."
            errorText = ""

            val okHttpClient = filesRepository.createAuthedOkHttpClient()
            val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

            val mediaItem = MediaItem.fromUri(audioUrl)
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)

            player.stop()
            player.clearMediaItems()
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            status = "Audio failed"
            errorText = friendlyAudioMessage(e)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                status = when (playbackState) {
                    Player.STATE_BUFFERING -> "Buffering..."
                    Player.STATE_READY -> "Ready"
                    Player.STATE_ENDED -> "Ended"
                    Player.STATE_IDLE -> "Idle"
                    else -> "Audio"
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                status = "Audio failed"
                errorText = friendlyAudioMessage(error)
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, audioUrl) {
        while (true) {
            positionMs = max(0L, player.currentPosition)

            val d = player.duration
            durationMs = if (d == C.TIME_UNSET || d < 0L) 0L else d

            delay(400L)
        }
    }

    AudioPlayerShell(
        title = currentAudio.name,
        counter = "${currentIndex + 1} / ${audioFiles.size}",
        status = status,
        errorText = errorText,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        onClose = onClose,
        onPlayPause = {
            if (player.isPlaying) player.pause() else player.play()
        },
        onSeek = { seekMs ->
            player.seekTo(seekMs.coerceIn(0L, max(durationMs, 0L)))
        },
        onPrev = {
            if (currentIndex > 0) currentIndex -= 1
        },
        onNext = {
            if (currentIndex < audioFiles.lastIndex) currentIndex += 1
        },
        canPrev = currentIndex > 0,
        canNext = currentIndex < audioFiles.lastIndex
    )
}

@Composable
private fun AudioPlayerShell(
    title: String,
    counter: String,
    status: String,
    errorText: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    canPrev: Boolean,
    canNext: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "♪",
                        color = Color.White,
                        style = MaterialTheme.typography.displayLarge
                    )

                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = status,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (status == "Buffering...") {
                        CircularProgressIndicator(color = Color.White)
                    }

                    if (errorText.isNotBlank()) {
                        Text(
                            text = errorText,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Slider(
                    value = if (durationMs > 0L) {
                        positionMs.toFloat().coerceIn(0f, durationMs.toFloat())
                    } else {
                        0f
                    },
                    onValueChange = { v ->
                        onSeek(v.toLong())
                    },
                    valueRange = 0f..max(durationMs, 1L).toFloat(),
                    enabled = durationMs > 0L
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatAudioTime(positionMs),
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = if (durationMs > 0L) formatAudioTime(durationMs) else "--:--",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPrev,
                        enabled = canPrev,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Prev")
                    }

                    Button(
                        onClick = onPlayPause,
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Text(if (isPlaying) "Pause" else "Play")
                    }

                    Button(
                        onClick = onNext,
                        enabled = canNext,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    }
}

private fun buildAudioDownloadUrl(
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

private fun buildAudioItemPath(currentPath: String?, itemName: String): String {
    return listOfNotNull(currentPath, itemName)
        .filter { it.isNotBlank() }
        .joinToString("/")
}

private fun formatAudioTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun friendlyAudioMessage(error: Throwable): String {
    val msg = error.message?.trim().orEmpty()

    return when {
        msg.contains("401", ignoreCase = true) ->
            "Session expired. Please pair again."

        msg.contains("403", ignoreCase = true) ->
            "Access denied."

        msg.contains("404", ignoreCase = true) ->
            "Audio file not found."

        msg.contains("TLS", ignoreCase = true) ||
                msg.contains("certificate", ignoreCase = true) ->
            "Server TLS identity check failed."

        msg.isNotBlank() ->
            "Playback failed: $msg"

        else ->
            "Playback failed."
    }
}