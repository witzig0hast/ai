package de.hastnetwork.jarvis.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.sqrt

/** Sample rate the server expects for client -> server audio (architecture.md §6). */
const val RECORDER_SAMPLE_RATE_HZ = 16_000

/**
 * Captures microphone audio as PCM16LE mono 16kHz chunks via [AudioRecord]
 * and exposes it as a [Flow]. Also runs a very simple energy-based
 * (RMS-over-a-rolling-window) voice-activity detector purely to help the UI
 * decide when to proactively send `end_utterance`.
 *
 * This client-side VAD does NOT need to be perfect or robust: per
 * architecture.md §6, the server also runs its own VAD (Silero) as a
 * fallback in case the client never sends `end_utterance` (e.g. the app
 * loses network mid-utterance) - so we can keep this simple.
 */
class AudioRecorder {

    data class Chunk(val pcm16le: ByteArray, val isSpeaking: Boolean, val rms: Double)

    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // Rolling-window VAD tuning. Deliberately simple thresholds, not an ML model.
    private val speechRmsThreshold = 800.0
    private val silenceChunksToConsiderSpeechEnded = 12 // ~12 * ~40ms chunks ≈ 500ms of silence

    @SuppressLint("MissingPermission") // Caller must have requested RECORD_AUDIO first.
    fun start(): Flow<Chunk> = callbackFlow {
        val minBufferSize = AudioRecord.getMinBufferSize(
            RECORDER_SAMPLE_RATE_HZ,
            channelConfig,
            audioFormat,
        ).coerceAtLeast(1)
        // Read in small chunks for low latency; keep the AudioRecord internal
        // buffer a bit larger than the read chunk to absorb jitter.
        val readChunkBytes = 1280 // 640 samples * 2 bytes ≈ 40ms @ 16kHz mono
        val internalBufferSize = (minBufferSize * 2).coerceAtLeast(readChunkBytes * 4)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            RECORDER_SAMPLE_RATE_HZ,
            channelConfig,
            audioFormat,
            internalBufferSize,
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            close(IllegalStateException("AudioRecord konnte nicht initialisiert werden"))
            return@callbackFlow
        }

        var consecutiveSilentChunks = 0
        val buffer = ByteArray(readChunkBytes)

        audioRecord.startRecording()

        try {
            while (isActive) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val chunk = buffer.copyOf(read)
                val rms = computeRms(chunk, read)
                val isSpeaking = rms >= speechRmsThreshold
                if (isSpeaking) consecutiveSilentChunks = 0 else consecutiveSilentChunks++

                trySend(Chunk(chunk, isSpeaking, rms))
            }
        } finally {
            runCatching {
                audioRecord.stop()
                audioRecord.release()
            }
        }

        awaitClose {
            runCatching {
                audioRecord.stop()
                audioRecord.release()
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * True once enough consecutive near-silent chunks have passed after
     * speech was detected - a simple heuristic the ViewModel can use to
     * decide "the user has probably stopped talking, send end_utterance".
     * Exposed as a pure function so the ViewModel can apply it to a running
     * count if it wants finer control instead of relying on [Chunk.isSpeaking]
     * alone.
     */
    fun hasLikelyFinishedSpeaking(consecutiveSilentChunks: Int): Boolean =
        consecutiveSilentChunks >= silenceChunksToConsiderSpeechEnded

    private fun computeRms(buffer: ByteArray, length: Int): Double {
        var sum = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < length) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low
            sum += (sample * sample).toDouble()
            samples++
            i += 2
        }
        if (samples == 0) return 0.0
        return sqrt(sum / samples)
    }
}
