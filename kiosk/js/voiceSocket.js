import { getSettings, wsBase } from "./settings.js";

/**
 * Thin wrapper around the /ws/voice protocol (docs/architecture.md §6).
 * Emits "control" (parsed JSON text frames), "audio" (raw ArrayBuffer PCM16
 * 24kHz chunks), "closed" and "error" events.
 */
export class VoiceSocket extends EventTarget {
  constructor() {
    super();
    this.ws = null;
  }

  connect(agentId, conversationId) {
    const settings = getSettings();
    const params = new URLSearchParams({ agent_id: agentId });
    if (conversationId) params.set("conversation_id", conversationId);
    if (settings.token) params.set("token", settings.token);

    this.ws = new WebSocket(`${wsBase(settings)}/ws/voice?${params.toString()}`);
    this.ws.binaryType = "arraybuffer";

    this.ws.onmessage = (event) => {
      if (typeof event.data === "string") {
        let payload;
        try {
          payload = JSON.parse(event.data);
        } catch (e) {
          return;
        }
        this.dispatchEvent(new CustomEvent("control", { detail: payload }));
      } else {
        this.dispatchEvent(new CustomEvent("audio", { detail: event.data }));
      }
    };
    this.ws.onclose = (event) => this.dispatchEvent(new CustomEvent("closed", { detail: event }));
    this.ws.onerror = (event) => this.dispatchEvent(new CustomEvent("error", { detail: event }));
  }

  sendAudio(arrayBuffer) {
    if (this.ws?.readyState === WebSocket.OPEN) this.ws.send(arrayBuffer);
  }

  _sendControl(type) {
    if (this.ws?.readyState === WebSocket.OPEN) this.ws.send(JSON.stringify({ type }));
  }

  start() {
    this._sendControl("start");
  }

  endUtterance() {
    this._sendControl("end_utterance");
  }

  bargeIn() {
    this._sendControl("barge_in");
  }

  stop() {
    this._sendControl("stop");
    this.ws?.close();
    this.ws = null;
  }
}
