import { getSettings, isConfigured, wsBase } from "./settings.js";

/**
 * Long-lived, server->client-only push channel (docs/architecture.md §11):
 * reminder_due, briefing_ready. Reconnects with exponential backoff since
 * the kiosk is meant to sit connected for hours/days at a time.
 */
export class EventsSocket extends EventTarget {
  constructor() {
    super();
    this.ws = null;
    this.shouldReconnect = false;
    this.reconnectDelayMs = 2000;
  }

  connect() {
    this.shouldReconnect = true;
    this._open();
  }

  _open() {
    const settings = getSettings();
    if (!isConfigured(settings)) return;

    const params = new URLSearchParams();
    if (settings.token) params.set("token", settings.token);
    this.ws = new WebSocket(`${wsBase(settings)}/ws/events?${params.toString()}`);

    this.ws.onopen = () => {
      this.reconnectDelayMs = 2000;
    };
    this.ws.onmessage = (event) => {
      try {
        this.dispatchEvent(new CustomEvent("event", { detail: JSON.parse(event.data) }));
      } catch (e) {
        // ignore malformed frames
      }
    };
    this.ws.onclose = () => this._scheduleReconnect();
    this.ws.onerror = () => this.ws?.close();
  }

  _scheduleReconnect() {
    if (!this.shouldReconnect) return;
    setTimeout(() => this._open(), this.reconnectDelayMs);
    this.reconnectDelayMs = Math.min(this.reconnectDelayMs * 2, 30000);
  }

  disconnect() {
    this.shouldReconnect = false;
    this.ws?.close();
    this.ws = null;
  }
}
