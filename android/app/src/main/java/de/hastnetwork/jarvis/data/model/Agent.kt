package de.hastnetwork.jarvis.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the `Agent` entity from architecture.md §4.
 */
@Serializable
data class Agent(
    val id: String,
    val name: String,
    val description: String,
    @SerialName("system_prompt") val systemPrompt: String,
    @SerialName("ollama_model") val ollamaModel: String,
    @SerialName("voice_id") val voiceId: String,
    @SerialName("avatar_color") val avatarColor: String,
    @SerialName("is_default") val isDefault: Boolean = false,
)

/** Body for `POST /api/agents` — same shape as [Agent] but without `id`. */
@Serializable
data class AgentCreateRequest(
    val name: String,
    val description: String,
    @SerialName("system_prompt") val systemPrompt: String,
    @SerialName("ollama_model") val ollamaModel: String,
    @SerialName("voice_id") val voiceId: String,
    @SerialName("avatar_color") val avatarColor: String,
    @SerialName("is_default") val isDefault: Boolean = false,
)
