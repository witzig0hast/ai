package de.hastnetwork.jarvis.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Full `Conversation` entity from architecture.md §4.
 */
@Serializable
data class Conversation(
    val id: String,
    @SerialName("agent_id") val agentId: String,
    val title: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

/**
 * List-item shape returned by `GET /api/conversations` (architecture.md §5):
 * `{id, agent_id, title, updated_at, preview}` — no `created_at`, but has
 * `preview`.
 */
@Serializable
data class ConversationSummary(
    val id: String,
    @SerialName("agent_id") val agentId: String,
    val title: String,
    @SerialName("updated_at") val updatedAt: String,
    val preview: String = "",
)

/**
 * Response shape for `GET /api/conversations/{id}`: the conversation plus
 * its full message list.
 */
@Serializable
data class ConversationDetail(
    val id: String,
    @SerialName("agent_id") val agentId: String,
    val title: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val messages: List<Message> = emptyList(),
)
