package com.pqnas.mobile.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Streaming

interface WorkspaceFilesApi {
    @GET("/api/v4/workspaces")
    suspend fun listWorkspaces(): WorkspacesResponse

    @GET("/api/v4/workspaces/files/list")
    suspend fun listWorkspaceFiles(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String? = null
    ): FilesListResponse

    @Streaming
    @GET("/api/v4/workspaces/files/get")
    suspend fun downloadWorkspaceFile(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String
    ): ResponseBody

    @GET("/api/v4/workspaces/files/read_text")
    suspend fun readWorkspaceTextFile(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String,
        @Query("session_id") sessionId: String
    ): ReadTextResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/files/write_text")
    suspend fun writeWorkspaceTextFile(
        @Body request: WorkspaceWriteTextRequest
    ): WriteTextResponse

    @POST("/api/v4/workspaces/files/delete")
    suspend fun deleteWorkspaceFile(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String
    ): DeleteFileResponse

    @POST("/api/v4/workspaces/files/move")
    suspend fun moveWorkspaceFile(
        @Query("workspace_id") workspaceId: String,
        @Query("from") from: String,
        @Query("to") to: String
    ): MoveFileResponse

    @POST("/api/v4/workspaces/files/mkdir")
    suspend fun mkdirWorkspace(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String
    ): MkdirResponse

    @PUT("/api/v4/workspaces/files/put")
    suspend fun uploadWorkspaceFile(
        @Query("workspace_id") workspaceId: String,
        @Query("path") path: String,
        @Query("overwrite") overwrite: Int = 0,
        @Body body: RequestBody
    ): UploadFileResponse

    @GET("/api/v4/shares/list")
    suspend fun listWorkspaceShares(
        @Query("workspace_id") workspaceId: String
    ): SharesListResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/shares/create")
    suspend fun createWorkspaceShare(
        @Body request: WorkspaceCreateShareRequest
    ): CreateShareResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/files/edit_lease/acquire")
    suspend fun acquireWorkspaceEditLease(
        @Body request: WorkspaceEditLeaseRequest
    ): WorkspaceEditLeaseResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/files/edit_lease/refresh")
    suspend fun refreshWorkspaceEditLease(
        @Body request: WorkspaceEditLeaseRequest
    ): WorkspaceEditLeaseResponse

    @Headers("Content-Type: application/json")
    @POST("/api/v4/workspaces/files/edit_lease/release")
    suspend fun releaseWorkspaceEditLease(
        @Body request: WorkspaceEditLeaseReleaseRequest
    ): WorkspaceEditLeaseReleaseResponse
}