package com.pqnas.mobile.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pqnas.mobile.api.FileItemDto
import com.pqnas.mobile.files.FilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun ImagePreviewScreen(
    filesRepository: FilesRepository,
    currentPath: String?,
    images: List<FileItemDto>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    if (images.isEmpty()) return

    var currentIndex by remember(images, initialIndex) {
        mutableStateOf(initialIndex.coerceIn(0, images.lastIndex))
    }

    var bitmap by remember(currentIndex, images) { mutableStateOf<Bitmap?>(null) }
    var status by remember(currentIndex, images) { mutableStateOf("Loading...") }
    var loading by remember(currentIndex, images) { mutableStateOf(true) }

    var scale by remember(currentIndex, images) { mutableStateOf(1f) }
    var offset by remember(currentIndex, images) { mutableStateOf(Offset.Zero) }

    val currentImage = images[currentIndex]
    val relPath = remember(currentPath, currentImage.name) {
        buildPreviewPath(currentPath, currentImage.name)
    }

    fun resetTransform() {
        scale = 1f
        offset = Offset.Zero
    }

    BackHandler {
        onClose()
    }

    LaunchedEffect(relPath) {
        loading = true
        bitmap = null
        status = "Loading..."
        resetTransform()

        try {
            val body = filesRepository.download(relPath)
            val bytes = withContext(Dispatchers.IO) { body.bytes() }

            val bmp = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }

            if (bmp == null) {
                status = "Failed to decode image"
                loading = false
            } else {
                bitmap = bmp
                status = "${bmp.width} × ${bmp.height}"
                loading = false
            }
        } catch (e: Exception) {
            loading = false
            status = friendlyPreviewMessage(e)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) {
                    Text("Close", color = Color.White)
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "${currentIndex + 1} / ${images.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }

            Text(
                text = currentImage.name,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )

            Text(
                text = status,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.80f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .pointerInput(relPath) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.02f) {
                                    resetTransform()
                                } else {
                                    scale = 2f
                                    offset = Offset.Zero
                                }
                            }
                        )
                    }
                    .pointerInput(relPath) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val newScale = (scale * gestureZoom).coerceIn(1f, 6f)
                            scale = newScale

                            offset = if (newScale <= 1.02f) {
                                Offset.Zero
                            } else {
                                offset + pan
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    bitmap != null -> {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = currentImage.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    }

                    loading -> {
                        CircularProgressIndicator(color = Color.White)
                    }

                    else -> {
                        Text(
                            text = status,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (currentIndex > 0) currentIndex -= 1
                    },
                    enabled = currentIndex > 0
                ) {
                    Text("Prev", color = if (currentIndex > 0) Color.White else Color.Gray)
                }

                TextButton(
                    onClick = { resetTransform() },
                    enabled = !loading && bitmap != null
                ) {
                    Text(
                        "Reset zoom",
                        color = if (!loading && bitmap != null) Color.White else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        if (currentIndex < images.lastIndex) currentIndex += 1
                    },
                    enabled = currentIndex < images.lastIndex
                ) {
                    Text(
                        "Next",
                        color = if (currentIndex < images.lastIndex) Color.White else Color.Gray
                    )
                }
            }
        }
    }
}

private fun buildPreviewPath(currentPath: String?, itemName: String): String {
    return listOfNotNull(currentPath, itemName)
        .filter { it.isNotBlank() }
        .joinToString("/")
}

private fun friendlyPreviewMessage(error: Throwable): String {
    val msg = error.message?.trim().orEmpty()

    return when {
        msg.contains("HTTP 401", ignoreCase = true) ->
            "Session expired. Please pair again."

        msg.contains("HTTP 403", ignoreCase = true) ->
            "Access denied."

        msg.contains("HTTP 404", ignoreCase = true) ->
            "Image not found."

        msg.contains("HTTP 500", ignoreCase = true) ->
            "Preview failed: server error."

        msg.isNotBlank() ->
            "Preview failed: $msg"

        else ->
            "Preview failed: unknown error"
    }
}