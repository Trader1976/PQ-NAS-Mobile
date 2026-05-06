package com.pqnas.mobile.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

data class EchoStackItemDto(
    val id: String = "",
    val url: String = "",
    val final_url: String = "",
    val title: String = "",
    val description: String = "",
    val site_name: String = "",
    val favicon_url: String = "",
    val preview_image_url: String = "",
    val tags_text: String = "",
    val collection: String = "",
    val notes: String = "",
    val read_state: String = "unread",
    val favorite: Boolean = false,
    val archive_status: String = "none",
    val archive_error: String = "",
    val archive_rel_dir: String = "",
    val archive_bytes: Long = 0L,
    val created_epoch: Long = 0L,
    val updated_epoch: Long = 0L,
    val archived_epoch: Long = 0L
)

data class EchoStackItemsResponse(
    val ok: Boolean = false,
    val items: List<EchoStackItemDto> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

data class EchoStackItemResponse(
    val ok: Boolean = false,
    val item: EchoStackItemDto? = null,
    val archive_view_url: String = "",
    val already_archived: Boolean = false,
    val archive_error: String? = null,
    val error: String? = null,
    val message: String? = null
)

data class EchoStackBasicResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

data class EchoStackPreviewRequest(
    val url: String
)

data class EchoStackPreviewResponse(
    val ok: Boolean = false,
    val url: String = "",
    val final_url: String = "",
    val title: String = "",
    val description: String = "",
    val site_name: String = "",
    val favicon_url: String = "",
    val preview_image_url: String = "",
    val error: String? = null,
    val message: String? = null
)

data class EchoStackCreateRequest(
    val url: String,
    val final_url: String = "",
    val title: String = "",
    val description: String = "",
    val site_name: String = "",
    val favicon_url: String = "",
    val preview_image_url: String = "",
    val tags_text: String = "",
    val collection: String = "",
    val notes: String = "",
    val read_state: String = "unread",
    val favorite: Boolean = false
)

data class EchoStackUpdateRequest(
    val id: String,
    val url: String,
    val final_url: String = "",
    val title: String = "",
    val description: String = "",
    val site_name: String = "",
    val favicon_url: String = "",
    val preview_image_url: String = "",
    val tags_text: String = "",
    val collection: String = "",
    val notes: String = "",
    val read_state: String = "unread",
    val favorite: Boolean = false
)

data class EchoStackIdRequest(
    val id: String
)

interface EchoStackApi {
    @GET("/api/v4/echostack/items")
    suspend fun listItems(
        @Query("q") q: String? = null,
        @Query("limit") limit: Int = 200
    ): EchoStackItemsResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/echostack/preview")
    suspend fun preview(
        @Body request: EchoStackPreviewRequest
    ): EchoStackPreviewResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/echostack/items/create")
    suspend fun createItem(
        @Body request: EchoStackCreateRequest
    ): EchoStackItemResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/echostack/items/update")
    suspend fun updateItem(
        @Body request: EchoStackUpdateRequest
    ): EchoStackItemResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/echostack/items/delete")
    suspend fun deleteItem(
        @Body request: EchoStackIdRequest
    ): EchoStackBasicResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/echostack/items/archive")
    suspend fun archiveItem(
        @Body request: EchoStackIdRequest
    ): EchoStackItemResponse
}
