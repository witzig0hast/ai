package de.hastnetwork.jarvis.ui.screens.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hastnetwork.jarvis.data.model.Agent
import de.hastnetwork.jarvis.data.model.Attachment
import de.hastnetwork.jarvis.data.model.FileUploadResponse
import de.hastnetwork.jarvis.data.model.Message
import de.hastnetwork.jarvis.data.remote.ChatSseEvent
import de.hastnetwork.jarvis.data.repository.AgentRepository
import de.hastnetwork.jarvis.data.repository.ConversationRepository
import de.hastnetwork.jarvis.ui.components.toolCallFriendlyLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID

data class PendingAttachment(val fileId: String, val filename: String)

data class ChatUiState(
    val agents: List<Agent> = emptyList(),
    val selectedAgentId: String? = null,
    val conversationId: String? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val isUploading: Boolean = false,
    val isStreaming: Boolean = false,
    val errorMessage: String? = null,
    val activeToolLabel: String? = null,
)

/**
 * Text-chat ViewModel: `POST /api/chat` SSE streaming, `/api/files` uploads,
 * conversation resume from History.
 *
 * Takes an application [Context] (never an Activity context) only to copy a
 * picked-file `content://` Uri into a real [File] before the multipart
 * upload - Retrofit/OkHttp's `MultipartBody` needs a `RequestBody`, and the
 * simplest correct way to get one from an arbitrary content Uri is to
 * stream it into a cache file first.
 */
class ChatViewModel(
    private val agentRepository: AgentRepository,
    private val conversationRepository: ConversationRepository,
    private val appContext: Context,
    initialAgentId: String?,
    initialConversationId: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            selectedAgentId = initialAgentId ?: agentRepository.selectedAgent?.id,
            conversationId = initialConversationId,
            agents = agentRepository.agents.value,
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            agentRepository.refresh().onSuccess { agents ->
                _uiState.value = _uiState.value.copy(agents = agents)
                // If nothing selected it yet (no nav arg, no conversation
                // loaded), fall back to the repository's default agent now
                // that the list has actually loaded.
                if (_uiState.value.selectedAgentId == null) {
                    agentRepository.selectedAgent?.id?.let { defaultId ->
                        _uiState.value = _uiState.value.copy(selectedAgentId = defaultId)
                    }
                }
            }
        }
        initialConversationId?.let { loadConversation(it) }
    }

    private fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.getConversation(conversationId)
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(
                        conversationId = detail.id,
                        selectedAgentId = detail.agentId,
                        messages = detail.messages,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(errorMessage = e.message)
                }
        }
    }

    fun onInputTextChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun selectAgent(agent: Agent) {
        agentRepository.selectAgent(agent.id)
        _uiState.value = _uiState.value.copy(selectedAgentId = agent.id)
    }

    fun uploadAttachment(uri: android.net.Uri, displayName: String, mimeType: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            val tempFile = runCatching { copyUriToCacheFile(uri, displayName) }.getOrNull()
            if (tempFile == null) {
                _uiState.value = _uiState.value.copy(isUploading = false, errorMessage = "Datei konnte nicht gelesen werden")
                return@launch
            }
            conversationRepository.uploadFile(tempFile, mimeType)
                .onSuccess { response: FileUploadResponse ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        pendingAttachments = _uiState.value.pendingAttachments + PendingAttachment(response.fileId, response.filename),
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isUploading = false, errorMessage = e.message)
                }
            tempFile.delete()
        }
    }

    fun removePendingAttachment(fileId: String) {
        _uiState.value = _uiState.value.copy(
            pendingAttachments = _uiState.value.pendingAttachments.filterNot { it.fileId == fileId }
        )
    }

    fun sendSuggestion(text: String) = sendMessage(text)

    fun sendMessage(overrideText: String? = null) {
        val text = (overrideText ?: _uiState.value.inputText).trim()
        if (text.isEmpty()) return
        val agentId = _uiState.value.selectedAgentId ?: return
        val attachmentIds = _uiState.value.pendingAttachments.map { it.fileId }

        val optimisticUserMessage = Message(
            id = "local-${UUID.randomUUID()}",
            conversationId = _uiState.value.conversationId ?: "",
            role = Message.ROLE_USER,
            content = text,
            attachments = _uiState.value.pendingAttachments.map {
                Attachment(id = it.fileId, filename = it.filename, contentType = "", sizeBytes = 0)
            },
            createdAt = Instant.now().toString(),
        )

        val streamingId = "local-streaming-${UUID.randomUUID()}"
        val streamingPlaceholder = Message(
            id = streamingId,
            conversationId = _uiState.value.conversationId ?: "",
            role = Message.ROLE_ASSISTANT,
            content = "",
            createdAt = Instant.now().toString(),
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + optimisticUserMessage + streamingPlaceholder,
            inputText = "",
            pendingAttachments = emptyList(),
            isStreaming = true,
        )

        viewModelScope.launch {
            var streamedText = ""
            conversationRepository.streamChat(
                conversationId = _uiState.value.conversationId,
                agentId = agentId,
                message = text,
                attachmentIds = attachmentIds,
            ).collect { event ->
                when (event) {
                    is ChatSseEvent.Token -> {
                        streamedText += event.text
                        replaceMessage(streamingId, streamingPlaceholder.copy(content = streamedText))
                        // Disappears once the result arrives OR the next text
                        // token comes in, whichever is first (per spec).
                        if (_uiState.value.activeToolLabel != null) {
                            _uiState.value = _uiState.value.copy(activeToolLabel = null)
                        }
                    }

                    is ChatSseEvent.ToolCall -> {
                        _uiState.value = _uiState.value.copy(activeToolLabel = toolCallFriendlyLabel(event.name))
                    }

                    is ChatSseEvent.ToolResult -> {
                        _uiState.value = _uiState.value.copy(activeToolLabel = null)
                    }

                    is ChatSseEvent.Done -> {
                        val finalMessage = event.event.message.let { msg ->
                            if (msg.suggestions.isEmpty() && event.event.suggestions.isNotEmpty()) {
                                msg.copy(suggestions = event.event.suggestions)
                            } else msg
                        }
                        replaceMessage(streamingId, finalMessage)
                        _uiState.value = _uiState.value.copy(
                            isStreaming = false,
                            activeToolLabel = null,
                            conversationId = finalMessage.conversationId.ifBlank { _uiState.value.conversationId },
                        )
                    }

                    is ChatSseEvent.Error -> {
                        replaceMessage(streamingId, streamingPlaceholder.copy(content = "⚠️ ${event.message}"))
                        _uiState.value = _uiState.value.copy(isStreaming = false, activeToolLabel = null, errorMessage = event.message)
                    }
                }
            }
        }
    }

    private fun replaceMessage(id: String, replacement: Message) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { if (it.id == id) replacement else it }
        )
    }

    private fun copyUriToCacheFile(uri: android.net.Uri, displayName: String): File? {
        val resolver = appContext.contentResolver
        val input = resolver.openInputStream(uri) ?: return null
        val safeName = displayName.ifBlank { "attachment-${System.currentTimeMillis()}" }
        val outFile = File(appContext.cacheDir, "upload-${System.currentTimeMillis()}-$safeName")
        input.use { streamIn ->
            outFile.outputStream().use { streamOut ->
                streamIn.copyTo(streamOut)
            }
        }
        return outFile
    }
}
