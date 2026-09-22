/**
 * Microphone capture at 16kHz mono PCM16LE, matching the /ws/voice protocol
 * (docs/architecture.md §6). Uses an AudioWorklet running in a
 * sampleRate:16000 AudioContext - Chromium resamples the mic input to match
 * the context's declared sample rate, so no manual resampling is needed
 * here (see js/capture-processor.js).
 */
export class MicCapture {
  constructor() {
    this.audioContext = null;
    this.stream = null;
    this.workletNode = null;
    this.sourceNode = null;
  }

  /** @param {(chunk: ArrayBuffer) => void} onChunk */
  async start(onChunk) {
    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
    });
    this.audioContext = new AudioContext({ sampleRate: 16000 });
    await this.audioContext.audioWorklet.addModule("js/capture-processor.js");

    this.sourceNode = this.audioContext.createMediaStreamSource(this.stream);
    this.workletNode = new AudioWorkletNode(this.audioContext, "capture-processor");
    this.workletNode.port.onmessage = (event) => onChunk(event.data);

    // Deliberately not connected to destination - we must not hear our own mic.
    this.sourceNode.connect(this.workletNode);
  }

  stop() {
    this.workletNode?.disconnect();
    this.sourceNode?.disconnect();
    this.stream?.getTracks().forEach((track) => track.stop());
    this.audioContext?.close();
    this.workletNode = null;
    this.sourceNode = null;
    this.stream = null;
    this.audioContext = null;
  }
}

/**
 * Streaming playback of PCM16LE mono 24kHz TTS audio chunks (XTTS-v2's
 * native output rate). Schedules buffers back-to-back for gapless playback;
 * stopAndFlush() is the barge-in path - it must silence the agent instantly.
 */
export class AudioPlayer {
  constructor() {
    this.audioContext = new AudioContext({ sampleRate: 24000 });
    this.nextStartTime = 0;
    this.activeSources = [];
  }

  /** @param {ArrayBuffer} pcm16Buffer */
  enqueue(pcm16Buffer) {
    const int16 = new Int16Array(pcm16Buffer);
    const float32 = new Float32Array(int16.length);
    for (let i = 0; i < int16.length; i++) {
      const sample = int16[i];
      float32[i] = sample / (sample < 0 ? 0x8000 : 0x7fff);
    }

    const buffer = this.audioContext.createBuffer(1, float32.length, 24000);
    buffer.copyToChannel(float32, 0);

    const source = this.audioContext.createBufferSource();
    source.buffer = buffer;
    source.connect(this.audioContext.destination);

    const startAt = Math.max(this.nextStartTime, this.audioContext.currentTime);
    source.start(startAt);
    this.nextStartTime = startAt + buffer.duration;

    this.activeSources.push(source);
    source.onended = () => {
      this.activeSources = this.activeSources.filter((s) => s !== source);
    };
  }

  /** Barge-in: drop everything buffered-but-unplayed immediately. */
  stopAndFlush() {
    for (const source of this.activeSources) {
      try {
        source.stop();
      } catch (e) {
        // already stopped/ended - fine
      }
    }
    this.activeSources = [];
    this.nextStartTime = this.audioContext.currentTime;
  }

  /** AudioContext starts "suspended" until a user gesture - call from a click handler. */
  async resume() {
    if (this.audioContext.state === "suspended") {
      await this.audioContext.resume();
    }
  }
}
