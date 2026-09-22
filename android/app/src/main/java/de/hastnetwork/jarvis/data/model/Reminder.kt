package de.hastnetwork.jarvis.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors the `Reminder` entity from architecture.md §10. */
@Serializable
data class Reminder(
    val id: String,
    val text: String,
    @SerialName("due_at") val dueAt: String,
    @SerialName("created_at") val createdAt: String,
    val fired: Boolean = false,
    @SerialName("conversation_id") val conversationId: String? = null,
)

/** Body of `POST /api/reminders` (architecture.md §10). */
@Serializable
data class ReminderCreateRequest(
    val text: String,
    @SerialName("due_at") val dueAt: String,
    @SerialName("conversation_id") val conversationId: String? = null,
)
