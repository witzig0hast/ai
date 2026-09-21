package de.hastnetwork.jarvis.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/status` response (architecture.md §5). */
@Serializable
data class StatusResponse(
    val ollama: Boolean = false,
    val stt: Boolean = false,
    val tts: Boolean = false,
    @SerialName("home_assistant") val homeAssistant: Boolean = false,
    val n8n: Boolean = false,
    @SerialName("active_agent") val activeAgent: String? = null,
)
