package com.pqnas.mobile.echostack

import com.pqnas.mobile.api.EchoStackApi
import com.pqnas.mobile.api.EchoStackCreateRequest
import com.pqnas.mobile.api.EchoStackIdRequest
import com.pqnas.mobile.api.EchoStackItemDto
import com.pqnas.mobile.api.EchoStackPreviewRequest
import com.pqnas.mobile.api.EchoStackUpdateRequest
import retrofit2.HttpException
import java.util.Locale

class EchoStackRepository(
    private val api: EchoStackApi
) {
    suspend fun listItems(query: String = ""): List<EchoStackItemDto> {
        val trimmed = query.trim()
        val response = api.listItems(
            q = trimmed.ifBlank { null },
            limit = 200
        )

        if (!response.ok) {
            throw IllegalStateException(response.message ?: response.error ?: "Could not load Echo Stack")
        }

        return response.items
    }

    suspend fun createFromUrl(
        rawUrl: String,
        title: String = "",
        collection: String = "",
        tags: String = "",
        notes: String = ""
    ): EchoStackItemDto {
        val url = normalizeUrl(rawUrl)

        val preview = runCatching {
            api.preview(EchoStackPreviewRequest(url = url))
        }.getOrNull()

        val cleanTitle = title.trim()
        val response = api.createItem(
            EchoStackCreateRequest(
                url = url,
                final_url = preview?.final_url.orEmpty(),
                title = cleanTitle.ifBlank {
                    preview?.title?.takeIf { it.isNotBlank() } ?: url
                },
                description = preview?.description.orEmpty(),
                site_name = preview?.site_name.orEmpty(),
                favicon_url = preview?.favicon_url.orEmpty(),
                preview_image_url = preview?.preview_image_url.orEmpty(),
                tags_text = tags.trim(),
                collection = collection.trim(),
                notes = notes.trim(),
                read_state = "unread"
            )
        )

        if (!response.ok || response.item == null) {
            throw IllegalStateException(response.message ?: response.error ?: "Could not create Echo Stack item")
        }

        return response.item
    }

    suspend fun setFavorite(item: EchoStackItemDto, favorite: Boolean): EchoStackItemDto =
        update(item.copy(favorite = favorite))

    suspend fun setReadState(item: EchoStackItemDto, read: Boolean): EchoStackItemDto =
        update(item.copy(read_state = if (read) "read" else "unread"))

    suspend fun updateNotes(item: EchoStackItemDto, notes: String): EchoStackItemDto =
        update(item.copy(notes = notes))

    suspend fun archive(id: String): EchoStackItemDto {
        val response = api.archiveItem(EchoStackIdRequest(id = id))

        if (!response.ok || response.item == null) {
            throw IllegalStateException(
                response.archive_error
                    ?: response.message
                    ?: response.error
                    ?: "Could not archive Echo Stack item"
            )
        }

        return response.item
    }

    suspend fun delete(id: String) {
        val response = api.deleteItem(EchoStackIdRequest(id = id))

        if (!response.ok) {
            throw IllegalStateException(response.message ?: response.error ?: "Could not delete Echo Stack item")
        }
    }

    private suspend fun update(item: EchoStackItemDto): EchoStackItemDto {
        val response = api.updateItem(
            EchoStackUpdateRequest(
                id = item.id,
                url = item.url,
                final_url = item.final_url,
                title = item.title,
                description = item.description,
                site_name = item.site_name,
                favicon_url = item.favicon_url,
                preview_image_url = item.preview_image_url,
                tags_text = item.tags_text,
                collection = item.collection,
                notes = item.notes,
                read_state = item.read_state.lowercase(Locale.getDefault()).let {
                    if (it == "read") "read" else "unread"
                },
                favorite = item.favorite
            )
        )

        if (!response.ok || response.item == null) {
            throw IllegalStateException(response.message ?: response.error ?: "Could not update Echo Stack item")
        }

        return response.item
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }

        return "https://$trimmed"
    }
}

fun echoStackFriendlyMessage(action: String, error: Throwable): String {
    val http = (error as? HttpException)?.code()
    return when (http) {
        400 -> "$action failed: invalid request."
        401 -> "Session expired. Please pair again."
        403 -> "Access denied."
        404 -> "Echo Stack item not found."
        409 -> "$action failed: item is busy or already being processed."
        413 -> "$action failed: page is too large."
        502 -> "$action failed: could not fetch remote page."
        507 -> "$action failed: storage quota exceeded."
        else -> {
            val msg = error.message?.takeIf { it.isNotBlank() } ?: "unknown error"
            "$action failed: $msg"
        }
    }
}
