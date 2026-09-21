package de.hastnetwork.jarvis.data.repository

import de.hastnetwork.jarvis.data.model.ChatRequest
import de.hastnetwork.jarvis.data.model.ConversationDetail
import de.hastnetwork.jarvis.data.model.ConversationSummary
import de.hastnetwork.jarvis.data.model.FileUploadResponse
import de.hastnetwork.jarvis.data.remote.ChatSseClient
import de.hastnetwork.jarvis.data.remote.ChatSseEvent
import de.hastnetwork.jarvis.data.remote.JarvisApi
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Text-chat + history data access: `/api/chat` (SSE), `/api/conversations`,
 * `/api/files`.
 */
class ConversationRepository(
    private val api: JarvisApi,
    private val chatSseClient: ChatSseClient,
) {

    suspend fun getConversations(): Result<List<ConversationSummary>> = runCatching {
        api.getConversations()
    }

    suspend fun getConversation(id: String): Result<ConversationDetail> = runCatching {
        api.getConversation(id)
    }

    suspend fun deleteConversation(id: String): Result<Unit> = runCatching {
        api.deleteConversation(id)
        Unit
    }

    suspend fun uploadFile(file: File, mimeType: String?): Result<FileUploadResponse> = runCatching {
        val requestBody = file.asRequestBody((mimeType ?: "application/octet-stream").toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
        api.uploadFile(part)
    }

    /** Streams `POST /api/chat`; see [ChatSseClient] for the SSE parsing details. */
    fun streamChat(
        conversationId: String?,
        agentId: String,
        message: String,
        attachmentIds: List<String>,
    ): Flow<ChatSseEvent> = chatSseClient.streamChat(
        ChatRequest(
            conversationId = conversationId,
            agentId = agentId,
            message = message,
            attachmentIds = attachmentIds,
        )
    )
}
