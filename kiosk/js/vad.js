/**
 * Very simple energy-based voice-activity detector, mirroring the Android
 * client's approach (docs/architecture.md §6): it does NOT need to be
 * robust, since the server also runs Silero VAD as a fallback if the kiosk
 * never sends end_utterance.
 */
export class SilenceDetector {
  constructor({ rmsThreshold = 800, silentChunksToEndUtterance = 12 } = {}) {
    this.rmsThreshold = rmsThreshold;
    this.silentChunksToEndUtterance = silentChunksToEndUtterance;
    this.consecutiveSilentChunks = 0;
    this.hasSpokenYet = false;
  }

  /** @param {ArrayBuffer} pcm16Buffer @returns {boolean} true once utterance likely finished */
  feed(pcm16Buffer) {
    const int16 = new Int16Array(pcm16Buffer);
    let sumSquares = 0;
    for (let i = 0; i < int16.length; i++) sumSquares += int16[i] * int16[i];
    const rms = Math.sqrt(sumSquares / Math.max(1, int16.length));

    if (rms >= this.rmsThreshold) {
      this.hasSpokenYet = true;
      this.consecutiveSilentChunks = 0;
    } else {
      this.consecutiveSilentChunks++;
    }

    return this.hasSpokenYet && this.consecutiveSilentChunks >= this.silentChunksToEndUtterance;
  }

  reset() {
    this.consecutiveSilentChunks = 0;
    this.hasSpokenYet = false;
  }
}
