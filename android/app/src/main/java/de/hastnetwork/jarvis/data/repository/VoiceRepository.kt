package de.hastnetwork.jarvis.data.repository

import de.hastnetwork.jarvis.data.remote.VoiceEvent
import de.hastnetwork.jarvis.data.remote.VoiceSocket
import kotlinx.coroutines.flow.Flow

/**
 * Thin repository over [VoiceSocket] so `VoiceViewModel` doesn't talk to the
 * transport layer directly. One [VoiceRepository] instance backs exactly one
 * `/ws/voice` session at a time (a new Voice-screen entry gets a fresh
 * socket via [AppContainer]).
 */
class VoiceRepository(private val voiceSocket: VoiceSocket) {

    fun connect(agentId: String, conversationId: String?): Flow<VoiceEvent> =
        voiceSocket.connect(agentId, conversationId)

    fun sendStart(sampleRate: Int = 16000) = voiceSocket.sendStart(sampleRate)

    fun sendAudio(pcm16le16k: ByteArray) = voiceSocket.sendAudio(pcm16le16k)

    fun sendEndUtterance() = voiceSocket.sendEndUtterance()

    fun sendBargeIn() = voiceSocket.sendBargeIn()

    fun sendStop() = voiceSocket.sendStop()

    fun close() = voiceSocket.close()
}
