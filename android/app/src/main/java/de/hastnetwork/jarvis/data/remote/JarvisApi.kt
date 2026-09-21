package de.hastnetwork.jarvis.data.remote

import de.hastnetwork.jarvis.data.model.Agent
import de.hastnetwork.jarvis.data.model.AgentCreateRequest
import de.hastnetwork.jarvis.data.model.CalendarEvent
import de.hastnetwork.jarvis.data.model.ConversationDetail
import de.hastnetwork.jarvis.data.model.ConversationSummary
import de.hastnetwork.jarvis.data.model.FileUploadResponse
import de.hastnetwork.jarvis.data.model.StatusResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit definition of the REST surface from architecture.md §5.
 * `/api/chat` (SSE) is intentionally NOT here - see [ChatSseClient], since
 * Retrofit doesn't stream Server-Sent Events natively.
 */
interface JarvisApi {

    @GET("api/status")
    suspend fun getStatus(): StatusResponse

    @GET("api/agents")
    suspend fun getAgents(): List<Agent>

    @POST("api/agents")
    suspend fun createAgent(@Body body: AgentCreateRequest): Agent

    @GET("api/agents/{id}")
    suspend fun getAgent(@Path("id") id: String): Agent

    @PUT("api/agents/{id}")
    suspend fun updateAgent(@Path("id") id: String, @Body body: Agent): Agent

    @DELETE("api/agents/{id}")
    suspend fun deleteAgent(@Path("id") id: String): Response<Unit>

    @GET("api/conversations")
    suspend fun getConversations(): List<ConversationSummary>

    @GET("api/conversations/{id}")
    suspend fun getConversation(@Path("id") id: String): ConversationDetail

    @DELETE("api/conversations/{id}")
    suspend fun deleteConversation(@Path("id") id: String): Response<Unit>

    @Multipart
    @POST("api/files")
    suspend fun uploadFile(@Part file: MultipartBody.Part): FileUploadResponse

    @GET("api/calendar/upcoming")
    suspend fun getUpcomingEvents(@Query("within_minutes") withinMinutes: Int = 180): List<CalendarEvent>
}
