package de.hastnetwork.jarvis.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the `Message` entity from architecture.md §4. `role` is either
 * "user" or "assistant"; `suggestions` is only ever populated when
 * `role == "assistant"`.
 */
@Serializable
data class Message(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    val role: String,
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    val suggestions: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String,
) {
    val isUser: Boolean get() = role == ROLE_USER

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}
