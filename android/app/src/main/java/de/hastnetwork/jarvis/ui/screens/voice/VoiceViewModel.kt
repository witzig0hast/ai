package de.hastnetwork.jarvis.ui.screens.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hastnetwork.jarvis.audio.AudioPlayer
import de.hastnetwork.jarvis.audio.AudioRecorder
import de.hastnetwork.jarvis.data.model.Agent
import de.hastnetwork.jarvis.data.remote.VoiceEvent
import de.hastnetwork.jarvis.data.repository.AgentRepository
import de.hastnetwork.jarvis.data.repository.VoiceRepository
import de.hastnetwork.jarvis.ui.components.VoiceOrbState
import de.hastnetwork.jarvis.ui.components.toolCallFriendlyLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VoiceConnectionState { CONNECTING, READY, CLOSED, ERROR }

data class CaptionEntry(
    val id: Long,
    val role: String, // "user" | "assistant"
    val text: String,
    val isFinal: Boolean,
)

data class VoiceUiState(
    val connectionState: VoiceConnectionState = VoiceConnectionState.CONNECTING,
    val orbState: VoiceOrbState = VoiceOrbState.IDLE,
    val agents: List<Agent> = emptyList(),
    val selectedAgentId: String? = null,
    val conversationId: String? = null,
    val subtitlesEnabled: Boolean = true,
    val captions: List<CaptionEntry> = emptyList(),
    val errorMessage: String? = null,
    val micPermissionGranted: Boolean = false,
    val activeToolLabel: String? = null,
)

class VoiceViewModel(
    private val agentRepository: AgentRepository,
    private val voiceRepository: VoiceRepository,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer,
    initialAgentId: String?,
    initialConversationId: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VoiceUiState(
            selectedAgentId = initialAgentId ?: agentRepository.selectedAgent?.id,
            conversationId = initialConversationId,
            agents = agentRepository.agents.value,
        )
    )
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private var nextCaptionId = 0L
    private var consecutiveSilentChunks = 0
    private var spokeSinceLastEndUtterance = false
    private var assistantTokenBuffer = StringBuilder()
    private var assistantCaptionId: Long? = null
    private var userPartialCaptionId: Long? = null

    init {
        viewModelScope.launch {
            // If we already know which agent to talk to (nav arg, or the
            // repository's cached default from a previous screen) connect
            // immediately so the socket opens without waiting on the network.
            // Otherwise wait for the agent list so we have a default to fall
            // back to (this is the common "tap Home -> Voice" first-run path).
            if (_uiState.value.selectedAgentId != null) {
                connect()
            }
            agentRepository.refresh().onSuccess { agents ->
                _uiState.value = _uiState.value.copy(agents = agents)
                if (_uiState.value.selectedAgentId == null) {
                    val defaultAgentId = agentRepository.selectedAgent?.id
                    if (defaultAgentId != null) {
                        _uiState.value = _uiState.value.copy(selectedAgentId = defaultAgentId)
                        connect()
                    }
                }
            }
        }
    }

    private fun connect() {
        val agentId = _uiState.value.selectedAgentId ?: return
        viewModelScope.launch {
            voiceRepository.connect(agentId, _uiState.value.conversationId).collect { event ->
                handleEvent(event)
            }
        }
    }

    private fun handleEvent(event: VoiceEvent) {
        when (event) {
            is VoiceEvent.SessionReady -> {
                _uiState.value = _uiState.value.copy(
                    connectionState = VoiceConnectionState.READY,
                    conversationId = event.conversationId,
                    orbState = VoiceOrbState.LISTENING,
                )
                voiceRepository.sendStart()
                maybeStartRecording()
            }

            is VoiceEvent.GreetingText -> {
                appendCaption(role = "assistant", text = event.text, isFinal = true)
            }

            is VoiceEvent.TranscriptPartial -> {
                upsertUserPartial(event.text)
            }

            is VoiceEvent.TranscriptFinal -> {
                finalizeUserCaption(event.text)
            }

            is VoiceEvent.LlmToken -> {
                assistantTokenBuffer.append(event.text)
                upsertAssistantStreaming(assistantTokenBuffer.toString())
                // The tool-call indicator disappears once the result arrives
                // OR the next text token comes in, whichever is first.
                if (_uiState.value.activeToolLabel != null) {
                    _uiState.value = _uiState.value.copy(activeToolLabel = null)
                }
            }

            VoiceEvent.TtsStart -> {
                audioPlayer.prepare()
                _uiState.value = _uiState.value.copy(orbState = VoiceOrbState.SPEAKING)
            }

            is VoiceEvent.AudioChunk -> {
                audioPlayer.enqueue(event.pcm16le24k)
            }

            VoiceEvent.TtsEnd -> {
                _uiState.value = _uiState.value.copy(orbState = VoiceOrbState.LISTENING)
            }

            is VoiceEvent.LlmDone -> {
                finalizeAssistantCaption(event.fullText)
                assistantTokenBuffer = StringBuilder()
            }

            is VoiceEvent.ToolCall -> {
                _uiState.value = _uiState.value.copy(activeToolLabel = toolCallFriendlyLabel(event.name))
            }

            is VoiceEvent.ToolResult -> {
                _uiState.value = _uiState.value.copy(activeToolLabel = null)
            }

            is VoiceEvent.Error -> {
                _uiState.value = _uiState.value.copy(errorMessage = event.message)
            }

            is VoiceEvent.ConnectionClosed -> {
                _uiState.value = _uiState.value.copy(connectionState = VoiceConnectionState.CLOSED)
                stopRecording()
            }

            is VoiceEvent.ConnectionFailed -> {
                _uiState.value = _uiState.value.copy(
                    connectionState = VoiceConnectionState.ERROR,
                    errorMessage = event.throwable.message,
                )
                stopRecording()
            }
        }
    }

    // --- Captions ---

    private fun appendCaption(role: String, text: String, isFinal: Boolean): Long {
        val id = nextCaptionId++
        _uiState.value = _uiState.value.copy(captions = _uiState.value.captions + CaptionEntry(id, role, text, isFinal))
        return id
    }

    private fun upsertUserPartial(text: String) {
        val id = userPartialCaptionId
        if (id == null) {
            userPartialCaptionId = appendCaption("user", text, isFinal = false)
        } else {
            replaceCaption(id, text, isFinal = false)
        }
    }

    private fun finalizeUserCaption(text: String) {
        val id = userPartialCaptionId
        if (id == null) {
            appendCaption("user", text, isFinal = true)
        } else {
            replaceCaption(id, text, isFinal = true)
        }
        userPartialCaptionId = null
    }

    private fun upsertAssistantStreaming(text: String) {
        val id = assistantCaptionId
        if (id == null) {
            assistantCaptionId = appendCaption("assistant", text, isFinal = false)
        } else {
            replaceCaption(id, text, isFinal = false)
        }
    }

    private fun finalizeAssistantCaption(text: String) {
        val id = assistantCaptionId
        if (id == null) {
            appendCaption("assistant", text, isFinal = true)
        } else {
            replaceCaption(id, text, isFinal = true)
        }
        assistantCaptionId = null
    }

    private fun replaceCaption(id: Long, text: String, isFinal: Boolean) {
        _uiState.value = _uiState.value.copy(
            captions = _uiState.value.captions.map { if (it.id == id) it.copy(text = text, isFinal = isFinal) else it }
        )
    }

    // --- Mic / recording / barge-in ---

    fun onMicPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(micPermissionGranted = granted)
        if (granted) maybeStartRecording()
    }

    private fun maybeStartRecording() {
        if (!_uiState.value.micPermissionGranted) return
        if (_uiState.value.connectionState != VoiceConnectionState.READY) return
        if (recordingJob != null) return

        recordingJob = viewModelScope.launch {
            audioRecorder.start().collect { chunk ->
                voiceRepository.sendAudio(chunk.pcm16le)

                if (chunk.isSpeaking) {
                    consecutiveSilentChunks = 0
                    spokeSinceLastEndUtterance = true
                    // Barge-in: user is talking again while the agent is still speaking.
                    if (_uiState.value.orbState == VoiceOrbState.SPEAKING) {
                        audioPlayer.stopAndFlush()
                        voiceRepository.sendBargeIn()
                        _uiState.value = _uiState.value.copy(orbState = VoiceOrbState.LISTENING)
                    }
                } else {
                    consecutiveSilentChunks++
                    if (spokeSinceLastEndUtterance && audioRecorder.hasLikelyFinishedSpeaking(consecutiveSilentChunks)) {
                        voiceRepository.sendEndUtterance()
                        spokeSinceLastEndUtterance = false
                    }
                }
            }
        }
    }

    private fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
    }

    // --- UI actions ---

    fun toggleSubtitles() {
        _uiState.value = _uiState.value.copy(subtitlesEnabled = !_uiState.value.subtitlesEnabled)
    }

    fun selectAgent(agent: Agent) {
        agentRepository.selectAgent(agent.id)
        _uiState.value = _uiState.value.copy(selectedAgentId = agent.id)
        // Per architecture.md the agent is bound to the socket session via
        // the `agent_id` query param, so switching agents mid-session means
        // reconnecting with the new agent (conversation_id is preserved).
        stopRecording()
        voiceRepository.close()
        _uiState.value = _uiState.value.copy(connectionState = VoiceConnectionState.CONNECTING, orbState = VoiceOrbState.IDLE)
        connect()
    }

    override fun onCleared() {
        super.onCleared()
        stopRecording()
        voiceRepository.sendStop()
        voiceRepository.close()
        audioPlayer.release()
    }
}
