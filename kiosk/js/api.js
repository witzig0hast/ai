import { getSettings, httpBase } from "./settings.js";

function authHeaders() {
  const { token } = getSettings();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function getJson(path) {
  const res = await fetch(`${httpBase()}${path}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`GET ${path} -> ${res.status}`);
  return res.json();
}

async function del(path) {
  const res = await fetch(`${httpBase()}${path}`, {
    method: "DELETE",
    headers: authHeaders(),
  });
  if (!res.ok && res.status !== 404) throw new Error(`DELETE ${path} -> ${res.status}`);
}

async function postJson(path, body) {
  const res = await fetch(`${httpBase()}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`POST ${path} -> ${res.status}`);
  return res.json();
}

export const api = {
  getStatus: () => getJson("/api/status"),
  getAgents: () => getJson("/api/agents"),
  getConversations: () => getJson("/api/conversations"),
  getConversation: (id) => getJson(`/api/conversations/${id}`),
  getBriefing: () => getJson("/api/briefing/today"),
  getUpcomingEvents: (withinMinutes = 180) =>
    getJson(`/api/calendar/upcoming?within_minutes=${withinMinutes}`),

  getReminders: (includeFired = false) =>
    getJson(`/api/reminders?include_fired=${includeFired}`),
  createReminder: (text, dueAtIso) => postJson("/api/reminders", { text, due_at: dueAtIso }),
  deleteReminder: (id) => del(`/api/reminders/${id}`),

  /**
   * Sends a chat message and streams the SSE response
   * (docs/architecture.md §5), invoking onEvent({type, data}) for each
   * "token" / "tool_call" / "tool_result" / "done" / "error" event.
   * EventSource can't be used here: it doesn't support POST bodies or
   * custom headers, so we parse the stream manually off fetch().
   */
  async sendChatMessage({ conversationId, agentId, message, attachmentIds = [], onEvent }) {
    const res = await fetch(`${httpBase()}/api/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({
        conversation_id: conversationId,
        agent_id: agentId,
        message,
        attachment_ids: attachmentIds,
      }),
    });
    if (!res.ok || !res.body) throw new Error(`chat -> ${res.status}`);
    await parseSseStream(res.body, onEvent);
  },

  async uploadFile(file) {
    const form = new FormData();
    form.append("file", file);
    const res = await fetch(`${httpBase()}/api/files`, {
      method: "POST",
      headers: authHeaders(),
      body: form,
    });
    if (!res.ok) throw new Error(`upload -> ${res.status}`);
    return res.json();
  },
};

async function parseSseStream(body, onEvent) {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let sepIndex;
    while ((sepIndex = buffer.indexOf("\n\n")) !== -1) {
      const rawEvent = buffer.slice(0, sepIndex);
      buffer = buffer.slice(sepIndex + 2);
      const parsed = parseSseEvent(rawEvent);
      if (parsed) onEvent(parsed);
    }
  }
}

function parseSseEvent(raw) {
  let eventType = "message";
  const dataLines = [];
  for (const line of raw.split("\n")) {
    if (line.startsWith("event:")) eventType = line.slice(6).trim();
    else if (line.startsWith("data:")) dataLines.push(line.slice(5).trim());
  }
  if (dataLines.length === 0) return null;
  try {
    return { type: eventType, data: JSON.parse(dataLines.join("\n")) };
  } catch (e) {
    return { type: eventType, data: null };
  }
}
