package com.pqnas.mobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.pqnas.mobile.api.FileItemDto
import com.pqnas.mobile.files.FileScope
import com.pqnas.mobile.files.FileTypeIcons
import com.pqnas.mobile.files.FilesRepository
import com.pqnas.mobile.files.SvgIconLoader
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

@Composable
internal fun rememberFileThumbnailImageLoader(
    filesRepository: FilesRepository
): ImageLoader? {
    val context = LocalContext.current.applicationContext

    return remember(filesRepository, context) {
        runCatching {
            ImageLoader.Builder(context)
                .okHttpClient(filesRepository.createAuthedOkHttpClient())
                .crossfade(true)
                .build()
        }.getOrNull()
    }
}

@Composable
internal fun FileLeadingVisual(
    filesRepository: FilesRepository,
    imageLoader: ImageLoader?,
    fileScope: FileScope,
    currentPath: String?,
    item: FileItemDto,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val relPath = remember(currentPath, item.name) {
        buildThumbItemPath(currentPath, item.name)
    }

    val thumbUrl = remember(
        filesRepository,
        fileScope,
        relPath,
        item.type,
        item.name,
        item.mtime_unix
    ) {
        if (
            item.type == "file" &&
            fileScope is FileScope.User &&
            isThumbnailSupportedImageFile(item.name)
        ) {
            buildUserGalleryThumbUrl(
                baseUrl = filesRepository.baseUrlForDisplay(),
                relPath = relPath,
                size = 192,
                version = item.mtime_unix
            )
        } else {
            null
        }
    }

    if (thumbUrl.isNullOrBlank() || imageLoader == null) {
        FallbackFileIcon(
            item = item,
            modifier = modifier
        )
        return
    }

    val request = remember(thumbUrl) {
        ImageRequest.Builder(context)
            .data(thumbUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        imageLoader = imageLoader,
        contentDescription = item.name,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Success -> {
                SubcomposeAsyncImageContent()
            }

            else -> {
                FallbackFileIcon(
                    item = item,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(7.dp)
                )
            }
        }
    }
}

@Composable
private fun FallbackFileIcon(
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
            sizePx = 96
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = if (item.type == "dir") "Directory icon" else "File icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = if (item.type == "dir") "📁" else "📄",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun buildUserGalleryThumbUrl(
    baseUrl: String,
    relPath: String,
    size: Int,
    version: Long?
): String? {
    val endpoint = "${baseUrl.trim().trimEnd('/')}/api/v4/gallery/thumb"
        .toHttpUrlOrNull()
        ?: return null

    return endpoint.newBuilder()
        .addQueryParameter("path", relPath)
        .addQueryParameter("size", size.coerceIn(64, 1024).toString())
        .apply {
            if (version != null && version > 0L) {
                addQueryParameter("v", version.toString())
            }
        }
        .build()
        .toString()
}

private fun buildThumbItemPath(currentPath: String?, itemName: String): String {
    return listOfNotNull(currentPath, itemName)
        .filter { it.isNotBlank() }
        .joinToString("/")
}

private fun isThumbnailSupportedImageFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return ext in setOf(
        "jpg", "jpeg",
        "png",
        "webp",
        "bmp",
        "gif",
        "tif", "tiff",
        "heic", "heif"
    )
}