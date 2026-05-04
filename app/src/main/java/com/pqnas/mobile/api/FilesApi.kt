package com.pqnas.mobile.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Headers



data class ChunkedUploadStartRequest(
    val path: String,
    val size_bytes: Long,
    val overwrite: Boolean = false
)

data class ChunkedUploadStartResponse(
    val ok: Boolean,
    val upload_id: String,
    val path: String? = null,
    val size_bytes: Long = 0L,
    val chunk_size: Long = 67108864L,
    val chunks_total: Long = 0L
)

data class ChunkedUploadChunkResponse(
    val ok: Boolean,
    val upload_id: String? = null,
    val index: Long = 0L,
    val bytes: Long = 0L
)

data class ChunkedUploadFinishRequest(
    val upload_id: String
)

data class ChunkedUploadFinishResponse(
    val ok: Boolean,
    val chunked: Boolean = true,
    val fingerprint_hex: String? = null,
    val path: String? = null,
    val bytes: Long = 0L,
    val overwrite: Boolean = false
)

data class ChunkedUploadCancelRequest(
    val upload_id: String
)

data class ChunkedUploadCancelResponse(
    val ok: Boolean,
    val upload_id: String? = null,
    val removed_entries: Long = 0L
)

data class AppAvailabilityResponse(
    val ok: Boolean = false,
    val id: String = "",
    val available: Boolean = false,
    val mobile: Boolean = false,
    val error: String? = null
)
data class DropZoneInfo(
    val id: String = "",
    val name: String = "",
    val destination_path: String = "",
    val created_epoch: Long = 0L,
    val expires_epoch: Long = 0L,
    val last_used_epoch: Long = 0L,
    val max_file_bytes: Long = 0L,
    val max_total_bytes: Long = 0L,
    val bytes_uploaded: Long = 0L,
    val upload_count: Long = 0L,
    val password_required: Boolean = false,
    val disabled: Boolean = false
)

data class DropZonesListResponse(
    val ok: Boolean = false,
    val drop_zones: List<DropZoneInfo> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

data class DropZoneCreateRequest(
    val name: String = "Drop Zone",
    val destination_path: String = "",
    val password: String = "",
    val expires_in_seconds: Long = 7L * 24L * 60L * 60L,
    val max_file_bytes: Long = 0L,
    val max_total_bytes: Long = 0L
)

data class DropZoneCreateResponse(
    val ok: Boolean = false,
    val id: String = "",
    val url: String = "",
    val full_url: String = "",
    val expires_epoch: Long = 0L,
    val destination_path: String = "",
    val error: String? = null,
    val message: String? = null
)

data class DropZoneDisableRequest(
    val id: String,
    val disabled: Boolean = true
)

data class DropZoneDisableResponse(
    val ok: Boolean = false,
    val id: String = "",
    val disabled: Boolean = false,
    val error: String? = null,
    val message: String? = null
)
interface FilesApi {
    @GET("/api/v4/files/list")
    suspend fun listFiles(
        @Query("path") path: String? = null
    ): FilesListResponse

    @GET("/api/v4/me/storage")
    suspend fun getMyStorage(): MeStorageResponse
    @GET("/api/v4/dropzones/list")
    suspend fun listDropZones(): DropZonesListResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/dropzones/create")
    suspend fun createDropZone(
        @Body request: DropZoneCreateRequest
    ): DropZoneCreateResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/dropzones/disable")
    suspend fun disableDropZone(
        @Body request: DropZoneDisableRequest
    ): DropZoneDisableResponse

    @GET("/api/v4/apps/has")
    suspend fun hasApp(
        @Query("id") id: String
    ): AppAvailabilityResponse

    @Streaming
    @GET("/api/v4/files/get")
    suspend fun downloadFile(
        @Query("path") path: String
    ): ResponseBody

    @GET("/api/v4/files/read_text")
    suspend fun readTextFile(
        @Query("path") path: String
    ): ReadTextResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/files/write_text")
    suspend fun writeTextFile(
        @Body request: WriteTextRequest
    ): WriteTextResponse

    @POST("/api/v4/files/delete")
    suspend fun deleteFile(
        @Query("path") path: String
    ): DeleteFileResponse

    @POST("/api/v4/files/move")
    suspend fun moveFile(
        @Query("from") from: String,
        @Query("to") to: String
    ): MoveFileResponse

    @POST("/api/v4/files/mkdir")
    suspend fun mkdir(
        @Query("path") path: String
    ): MkdirResponse


    @PUT("/api/v4/files/put")
    suspend fun uploadFile(
        @Query("path") path: String,
        @Query("overwrite") overwrite: Int = 0,
        @Body body: RequestBody
    ): UploadFileResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/uploads/start")
    suspend fun startChunkedUpload(
        @Body request: ChunkedUploadStartRequest
    ): ChunkedUploadStartResponse

    @PUT("/api/v4/uploads/chunk")
    suspend fun uploadChunk(
        @Query("upload_id") uploadId: String,
        @Query("index") index: Long,
        @Body body: RequestBody
    ): ChunkedUploadChunkResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/uploads/finish")
    suspend fun finishChunkedUpload(
        @Body request: ChunkedUploadFinishRequest
    ): ChunkedUploadFinishResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/uploads/cancel")
    suspend fun cancelChunkedUpload(
        @Body request: ChunkedUploadCancelRequest
    ): ChunkedUploadCancelResponse

    @GET("/api/v4/files/favorites")
    suspend fun listFavorites(): FavoritesListResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/files/favorites/add")
    suspend fun addFavorite(
        @Body request: FavoriteMutateRequest
    ): FavoriteMutateResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/files/favorites/remove")
    suspend fun removeFavorite(
        @Body request: FavoriteMutateRequest
    ): FavoriteMutateResponse

    @GET("/api/v4/shares/list")
    suspend fun listShares(
        @Query("workspace_id") workspaceId: String? = null
    ): SharesListResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/shares/create")
    suspend fun createShare(
        @Body request: CreateShareRequest
    ): CreateShareResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/shares/revoke")
    suspend fun revokeShare(
        @Body request: RevokeShareRequest
    ): RevokeShareResponse

    @GET("/api/v4/files/versions/list")
    suspend fun listFileVersions(
        @Query("path") path: String
    ): FileVersionsListResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/files/restore_version")
    suspend fun restoreFileVersion(
        @Body request: RestoreVersionRequest
    ): RestoreVersionResponse
}