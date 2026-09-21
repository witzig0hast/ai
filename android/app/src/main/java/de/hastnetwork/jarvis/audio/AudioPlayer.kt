package de.hastnetwork.jarvis.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Sample rate of the server's TTS audio (architecture.md §6, XTTS-v2 native output rate). */
const val PLAYER_SAMPLE_RATE_HZ = 24_000

/**
 * Plays streamed PCM16LE mono 24kHz TTS audio chunks via [AudioTrack] in
 * streaming ("write as you go") mode. [stopAndFlush] is the barge-in path:
 * it must drop any buffered-but-unplayed audio immediately so the agent's
 * voice actually stops the instant the user starts talking again.
 */
class AudioPlayer {

    private var audioTrack: AudioTrack? = null

    /** Creates (or recreates) the underlying [AudioTrack], ready for [enqueue] calls. */
    fun prepare() {
        release()

        val minBufferSize = AudioTrack.getMinBufferSize(
            PLAYER_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(1)
        val bufferSize = minBufferSize * 2

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(PLAYER_SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        // minSdk is 26 (== Build.VERSION_CODES.O), so the AudioTrack.Builder
        // API (added in API 23/26 for this attribute set) is always available
        // - no legacy fallback constructor needed.
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()
    }

    /** Queues raw PCM16LE 24kHz bytes for playback. Blocks briefly if the internal buffer is full. */
    fun enqueue(pcm16le: ByteArray) {
        val track = audioTrack ?: return
        if (track.state != AudioTrack.STATE_INITIALIZED) return
        track.write(pcm16le, 0, pcm16le.size)
    }

    /**
     * Barge-in: immediately silences the agent. Per architecture.md §6,
     * already-streamed audio chunks are stopped client-side right away via
     * `pause()` + `flush()` (drops buffered-but-unplayed audio) rather than
     * a graceful `stop()`, which would let the buffered tail keep playing.
     */
    fun stopAndFlush() {
        val track = audioTrack ?: return
        if (track.state != AudioTrack.STATE_INITIALIZED) return
        runCatching {
            track.pause()
            track.flush()
            track.play()
        }
    }

    fun release() {
        audioTrack?.let { track ->
            runCatching {
                track.stop()
                track.release()
            }
        }
        audioTrack = null
    }
}
