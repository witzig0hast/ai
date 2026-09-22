package de.hastnetwork.jarvis.data.remote

import de.hastnetwork.jarvis.data.local.AppSettings
import de.hastnetwork.jarvis.data.model.JarvisPushEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Receive-only wrapper for the proactive push channel `wss://.../ws/events`
 * (architecture.md §11). Unlike [VoiceSocket], this is not tied to a single
 * screen's lifecycle: it's meant to be started once by [JarvisEventsService]
 * and kept open for as long as the app wants proactive notifications, with
 * automatic reconnect/backoff (there is no client -> server traffic at all
 * besides the implicit connection handshake, so a dropped connection just
 * needs to be quietly reopened).
 */
class EventsSocket(
    private val okHttpClient: OkHttpClient,
    private val settingsProvider: () -> AppSettings,
    private val json: Json,
) {
    private val _events = MutableSharedFlow<JarvisPushEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<JarvisPushEvent> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var stopped = true
    private var backoffMs = INITIAL_BACKOFF_MS

    /** Starts connecting (and keeps reconnecting on failure) using [scope] for its internal coroutines. */
    fun start(scope: CoroutineScope) {
        stopped = false
        backoffMs = INITIAL_BACKOFF_MS
        connect(scope)
    }

    fun stop() {
        stopped = true
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "client_stopped")
        webSocket = null
    }

    private fun connect(scope: CoroutineScope) {
        if (stopped) return

        val settings = settingsProvider()
        if (settings.normalizedBaseUrl.isEmpty() || settings.token.isBlank()) {
            // Not configured yet (fresh install) - retry later rather than
            // spinning; Settings being filled in doesn't otherwise poke us.
            scheduleReconnect(scope)
            return
        }

        val wsBase = when {
            settings.normalizedBaseUrl.startsWith("https://") -> "wss://" + settings.normalizedBaseUrl.removePrefix("https://")
            settings.normalizedBaseUrl.startsWith("http://") -> "ws://" + settings.normalizedBaseUrl.removePrefix("http://")
            else -> settings.normalizedBaseUrl
        }
        val url = "$wsBase/ws/events?token=${settings.token}"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    backoffMs = INITIAL_BACKOFF_MS
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parse(text)?.let { _events.tryEmit(it) }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scheduleReconnect(scope)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scheduleReconnect(scope)
                }
            },
        )
    }

    private fun scheduleReconnect(scope: CoroutineScope) {
        if (stopped) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            connect(scope)
        }
    }

    private fun parse(text: String): JarvisPushEvent? = runCatching {
        val obj = json.parseToJsonElement(text).jsonObject
        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "reminder_due" -> {
                val reminderObj = obj["reminder"]?.jsonObject
                val id = reminderObj?.get("id")?.jsonPrimitive?.contentOrNull ?: ""
                val reminderText = reminderObj?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                JarvisPushEvent.ReminderDue(id, reminderText)
            }
            "briefing_ready" -> JarvisPushEvent.BriefingReady(obj["summary"]?.jsonPrimitive?.contentOrNull ?: "")
            else -> null
        }
    }.getOrNull()

    companion object {
        private const val INITIAL_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 60_000L
    }
}
