package de.hastnetwork.jarvis.data.remote

import de.hastnetwork.jarvis.data.local.AppSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/** Incoming server -> client frames of the `/ws/voice` protocol (architecture.md §6). */
sealed class VoiceEvent {
    data class SessionReady(val conversationId: String) : VoiceEvent()
    data class GreetingText(val text: String) : VoiceEvent()
    data class TranscriptPartial(val text: String) : VoiceEvent()
    data class TranscriptFinal(val text: String) : VoiceEvent()
    data class LlmToken(val text: String) : VoiceEvent()
    object TtsStart : VoiceEvent()
    data class AudioChunk(val pcm16le24k: ByteArray) : VoiceEvent()
    object TtsEnd : VoiceEvent()
    data class LlmDone(val fullText: String, val suggestions: List<String>) : VoiceEvent()
    data class Error(val message: String) : VoiceEvent()
    /** Not part of the wire protocol - emitted locally when the socket closes/fails. */
    data class ConnectionClosed(val reason: String?) : VoiceEvent()
    data class ConnectionFailed(val throwable: Throwable) : VoiceEvent()
}

@Serializable
private data class LlmDoneWire(
    @SerialName("full_text") val fullText: String,
    val suggestions: List<String> = emptyList(),
)

/**
 * Wraps the raw OkHttp [WebSocket] for `/ws/voice`. Text frames are JSON
 * control messages, binary frames are raw PCM16LE audio (client: 16kHz,
 * server: 24kHz) - no envelope, per architecture.md §6.
 */
class VoiceSocket(
    private val okHttpClient: OkHttpClient,
    private val settingsProvider: () -> AppSettings,
    private val json: Json,
) {
    private var webSocket: WebSocket? = null

    /**
     * Opens the connection and returns a cold [Flow] of [VoiceEvent]s. The
     * flow stays open until [close] is called or the socket fails/closes.
     */
    fun connect(agentId: String, conversationId: String?): Flow<VoiceEvent> = callbackFlow {
        val settings = settingsProvider()
        val httpBase = settings.normalizedBaseUrl
        val wsBase = when {
            httpBase.startsWith("https://") -> "wss://" + httpBase.removePrefix("https://")
            httpBase.startsWith("http://") -> "ws://" + httpBase.removePrefix("http://")
            else -> httpBase
        }

        val urlBuilder = StringBuilder("$wsBase/ws/voice?agent_id=$agentId")
        if (!conversationId.isNullOrBlank()) {
            urlBuilder.append("&conversation_id=$conversationId")
        }
        urlBuilder.append("&token=${settings.token}")

        val request = Request.Builder().url(urlBuilder.toString()).build()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = parseTextFrame(text)
                if (event != null) trySend(event)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                trySend(VoiceEvent.AudioChunk(bytes.toByteArray()))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySend(VoiceEvent.ConnectionClosed(reason))
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(VoiceEvent.ConnectionFailed(t))
                close(t)
            }
        }

        webSocket = okHttpClient.newWebSocket(request, listener)

        awaitClose {
            webSocket?.close(1000, "client_closed")
            webSocket = null
        }
    }

    private fun parseTextFrame(text: String): VoiceEvent? {
        return runCatching {
            val obj = json.parseToJsonElement(text).jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "session_ready" -> VoiceEvent.SessionReady(obj.stringOrEmpty("conversation_id"))
                "greeting_text" -> VoiceEvent.GreetingText(obj.stringOrEmpty("text"))
                "transcript_partial" -> VoiceEvent.TranscriptPartial(obj.stringOrEmpty("text"))
                "transcript_final" -> VoiceEvent.TranscriptFinal(obj.stringOrEmpty("text"))
                "llm_token" -> VoiceEvent.LlmToken(obj.stringOrEmpty("text"))
                "tts_start" -> VoiceEvent.TtsStart
                "tts_end" -> VoiceEvent.TtsEnd
                "llm_done" -> {
                    val wire = json.decodeFromJsonElement(LlmDoneWire.serializer(), obj)
                    VoiceEvent.LlmDone(wire.fullText, wire.suggestions)
                }
                "error" -> VoiceEvent.Error(obj.stringOrEmpty("message"))
                else -> null
            }
        }.getOrNull()
    }

    private fun Map<String, JsonElement>.stringOrEmpty(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: ""

    // --- Client -> Server ---

    fun sendStart(sampleRate: Int = 16000) {
        webSocket?.send("""{"type":"start","sample_rate":$sampleRate}""")
    }

    fun sendAudio(pcm16le16k: ByteArray) {
        webSocket?.send(ByteString.of(*pcm16le16k))
    }

    fun sendEndUtterance() {
        webSocket?.send("""{"type":"end_utterance"}""")
    }

    fun sendBargeIn() {
        webSocket?.send("""{"type":"barge_in"}""")
    }

    fun sendStop() {
        webSocket?.send("""{"type":"stop"}""")
    }

    fun close() {
        webSocket?.close(1000, "client_closed")
        webSocket = null
    }
}
