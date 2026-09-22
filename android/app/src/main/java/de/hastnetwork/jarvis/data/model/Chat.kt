package de.hastnetwork.jarvis.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Body of `POST /api/chat` (architecture.md §5). */
@Serializable
data class ChatRequest(
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("agent_id") val agentId: String,
    val message: String,
    @SerialName("attachment_ids") val attachmentIds: List<String> = emptyList(),
)

/** Payload of the SSE `event: token` frame. */
@Serializable
data class ChatTokenEvent(val text: String)

/** Payload of the SSE `event: done` frame. */
@Serializable
data class ChatDoneEvent(
    val message: Message,
    val suggestions: List<String> = emptyList(),
)

/** Payload of the SSE `event: error` frame. */
@Serializable
data class ChatErrorEvent(val message: String)

/** Payload of the SSE `event: tool_call` frame (architecture.md §9, purely informational). */
@Serializable
data class ChatToolCallEvent(
    val name: String,
    val arguments: JsonObject = JsonObject(emptyMap()),
)

/** Payload of the SSE `event: tool_result` frame (architecture.md §9, purely informational). */
@Serializable
data class ChatToolResultEvent(
    val name: String,
    val result: JsonObject = JsonObject(emptyMap()),
)

/** Generic API error envelope: `{"error": {"code": str, "message": str}}`. */
@Serializable
data class ApiErrorEnvelope(val error: ApiError)

@Serializable
data class ApiError(val code: String, val message: String)
