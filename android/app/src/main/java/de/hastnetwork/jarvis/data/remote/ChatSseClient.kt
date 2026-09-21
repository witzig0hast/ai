package de.hastnetwork.jarvis.data.remote

import de.hastnetwork.jarvis.data.local.AppSettings
import de.hastnetwork.jarvis.data.model.ChatDoneEvent
import de.hastnetwork.jarvis.data.model.ChatErrorEvent
import de.hastnetwork.jarvis.data.model.ChatRequest
import de.hastnetwork.jarvis.data.model.ChatTokenEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

sealed class ChatSseEvent {
    data class Token(val text: String) : ChatSseEvent()
    data class Done(val event: ChatDoneEvent) : ChatSseEvent()
    data class Error(val message: String) : ChatSseEvent()
}

/**
 * Streams `POST /api/chat` as Server-Sent Events (architecture.md §5).
 *
 * Retrofit doesn't support SSE, so this issues the request directly through
 * the shared [OkHttpClient] (which already carries the base-URL rewrite and
 * auth interceptors, see [DynamicBaseUrlInterceptor] / [AuthInterceptor])
 * and hand-parses the `event:` / `data:` line protocol from the streaming
 * response body. This is intentionally minimal rather than pulling in
 * okhttp-sse, since the format here is a simple three-event contract.
 */
class ChatSseClient(
    private val okHttpClient: OkHttpClient,
    private val settingsProvider: () -> AppSettings,
    private val json: Json,
) {
    fun streamChat(request: ChatRequest): Flow<ChatSseEvent> = callbackFlow {
        val body = json.encodeToString(ChatRequest.serializer(), request)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val url = "${settingsProvider().normalizedBaseUrl}/api/chat"
        val httpRequest = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        val call: Call = okHttpClient.newCall(httpRequest)

        call.execute().use { response ->
            if (!response.isSuccessful) {
                trySend(ChatSseEvent.Error("HTTP ${response.code}: ${response.message}"))
                close()
                return@use
            }
            val source = response.body?.source()
            if (source == null) {
                trySend(ChatSseEvent.Error("Leere Antwort vom Server"))
                close()
                return@use
            }

            var currentEvent: String? = null
            val dataLines = StringBuilder()

            fun dispatch() {
                if (currentEvent == null && dataLines.isEmpty()) return
                val data = dataLines.toString()
                when (currentEvent) {
                    "token" -> runCatching {
                        json.decodeFromString(ChatTokenEvent.serializer(), data)
                    }.onSuccess { trySend(ChatSseEvent.Token(it.text)) }

                    "done" -> runCatching {
                        json.decodeFromString(ChatDoneEvent.serializer(), data)
                    }.onSuccess { trySend(ChatSseEvent.Done(it)) }

                    "error" -> runCatching {
                        json.decodeFromString(ChatErrorEvent.serializer(), data)
                    }.onSuccess { trySend(ChatSseEvent.Error(it.message)) }
                        .onFailure { trySend(ChatSseEvent.Error(data)) }
                }
                currentEvent = null
                dataLines.clear()
            }

            try {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> dispatch()
                        line.startsWith("event:") -> currentEvent = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> {
                            if (dataLines.isNotEmpty()) dataLines.append('\n')
                            dataLines.append(line.removePrefix("data:").trim())
                        }
                        // Ignore other SSE fields (id:, retry:, comments starting with ':').
                    }
                }
                // Flush a trailing event that wasn't terminated by a final blank line.
                dispatch()
            } catch (io: IOException) {
                trySend(ChatSseEvent.Error(io.message ?: "Verbindung unterbrochen"))
            } finally {
                close()
            }
        }

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)
}
