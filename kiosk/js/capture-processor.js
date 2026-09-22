// AudioWorkletProcessor: runs on the audio rendering thread. Buffers
// incoming Float32 samples (already at 16kHz - see audio.js, the
// AudioContext itself is created with sampleRate:16000 so the browser
// resamples the mic input for us) into ~40ms chunks, converts to PCM16LE,
// and posts each chunk to the main thread.
class CaptureProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.chunkSamples = 640; // ~40ms @ 16kHz, matches the Android client
    this.buffer = new Float32Array(this.chunkSamples);
    this.offset = 0;
  }

  process(inputs) {
    const channel = inputs[0]?.[0];
    if (!channel) return true;

    for (let i = 0; i < channel.length; i++) {
      this.buffer[this.offset++] = channel[i];
      if (this.offset >= this.chunkSamples) {
        this._flush();
      }
    }
    return true;
  }

  _flush() {
    const pcm16 = new Int16Array(this.offset);
    for (let i = 0; i < this.offset; i++) {
      const sample = Math.max(-1, Math.min(1, this.buffer[i]));
      pcm16[i] = sample < 0 ? sample * 0x8000 : sample * 0x7fff;
    }
    this.port.postMessage(pcm16.buffer, [pcm16.buffer]);
    this.offset = 0;
  }
}

registerProcessor("capture-processor", CaptureProcessor);
