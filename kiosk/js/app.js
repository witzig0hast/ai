import { api } from "./api.js";
import { AudioPlayer, MicCapture } from "./audio.js";
import { EventsSocket } from "./eventsSocket.js";
import { getSettings, isConfigured, saveSettings } from "./settings.js";
import { SilenceDetector } from "./vad.js";
import { VoiceSocket } from "./voiceSocket.js";

const el = (id) => document.getElementById(id);

const state = {
  agents: [],
  selectedAgentId: getSettings().agentId,
  conversationId: null,
  lastStatus: null,
  eventsSocket: null,
};

// ---------- screens ----------

function showScreen(name) {
  document.querySelectorAll(".screen").forEach((s) => s.classList.remove("active"));
  el(`screen-${name}`).classList.add("active");
}

// ---------- theme ----------

function effectiveTheme(settings = getSettings()) {
  if (settings.darkMode === "dark") return "dark";
  if (settings.darkMode === "light") return "light";
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function applyTheme() {
  const theme = effectiveTheme();
  document.documentElement.dataset.theme = theme;
  el("dark-toggle").textContent = theme === "dark" ? "☀️" : "🌙";
}

el("dark-toggle").addEventListener("click", () => {
  const next = effectiveTheme() === "dark" ? "light" : "dark";
  saveSettings({ darkMode: next });
  applyTheme();
});

// ---------- toast ----------

let toastTimer = null;
function showToast(message, ms = 3500) {
  const toast = el("toast");
  toast.textContent = message;
  toast.classList.remove("hidden");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.add("hidden"), ms);
}

// ---------- clock ----------

function tickClock() {
  const now = new Date();
  el("clock").textContent = now.toLocaleTimeString("de-DE", {
    hour: "2-digit",
    minute: "2-digit",
  });
  el("date").textContent = now.toLocaleDateString("de-DE", {
    weekday: "long",
    day: "2-digit",
    month: "long",
  });
}

// ---------- status row ----------

const STATUS_LABELS = {
  backend: "Backend",
  ollama: "Ollama",
  stt: "STT",
  tts: "TTS",
  home_assistant: "Home Assistant",
  n8n: "n8n",
};

async function refreshStatus() {
  try {
    const status = await api.getStatus();
    state.lastStatus = { backend: true, ...status };
  } catch (e) {
    state.lastStatus = {
      backend: false,
      ollama: false,
      stt: false,
      tts: false,
      home_assistant: false,
      n8n: false,
    };
  }
  renderStatusRow();
}

function renderStatusRow() {
  const row = el("status-row");
  row.innerHTML = "";
  for (const [key, label] of Object.entries(STATUS_LABELS)) {
    const up = Boolean(state.lastStatus?.[key]);
    const pill = document.createElement("div");
    pill.className = `status-pill${up ? " up" : ""}`;
    pill.innerHTML = `<span class="dot"></span><span>${label}</span>`;
    row.appendChild(pill);
  }
  row.onclick = () => {
    el("status-detail").classList.toggle("hidden");
    renderStatusDetail();
  };
}

function renderStatusDetail() {
  const detail = el("status-detail");
  if (detail.classList.contains("hidden")) return;
  detail.innerHTML = Object.entries(STATUS_LABELS)
    .map(([key, label]) => {
      const up = Boolean(state.lastStatus?.[key]);
      return `<div><span>${label}</span><span>${up ? "erreichbar" : "nicht erreichbar"}</span></div>`;
    })
    .join("");
}

// ---------- weather + events ticker ----------

async function refreshWeatherChip() {
  try {
    const briefing = await api.getBriefing();
    const chip = el("weather-chip");
    if (briefing.weather) {
      chip.textContent = `${Math.round(briefing.weather.temperature_c)}°C, ${briefing.weather.condition}`;
      chip.classList.remove("hidden");
    } else {
      chip.classList.add("hidden");
    }
  } catch (e) {
    el("weather-chip").classList.add("hidden");
  }
}

async function tickEventsTicker() {
  try {
    const events = await api.getUpcomingEvents();
    if (!events.length) return;
    const ticker = el("events-ticker");
    ticker.textContent = events
      .slice(0, 2)
      .map((e) => {
        const time = new Date(e.start_time).toLocaleTimeString("de-DE", {
          hour: "2-digit",
          minute: "2-digit",
        });
        return `${time} ${e.title}`;
      })
      .join("  ·  ");
    ticker.classList.remove("hidden");
    setTimeout(() => ticker.classList.add("hidden"), 10000);
  } catch (e) {
    // calendar stub / network hiccup - just skip this tick, never show an empty ticker
  }
}

// ---------- agent switching (simple cycle - no modal framework) ----------

async function loadAgents() {
  try {
    state.agents = await api.getAgents();
    if (!state.agents.some((a) => a.id === state.selectedAgentId)) {
      state.selectedAgentId = state.agents.find((a) => a.is_default)?.id ?? state.agents[0]?.id;
    }
  } catch (e) {
    state.agents = [];
  }
  updateAgentIndicators();
}

function currentAgent() {
  return state.agents.find((a) => a.id === state.selectedAgentId) ?? null;
}

function updateAgentIndicators() {
  const agent = currentAgent();
  const label = agent ? `● ${agent.name}` : "Jarvis";
  el("voice-agent-indicator").textContent = label;
  el("chat-agent-indicator").textContent = label;
}

function cycleAgent() {
  if (state.agents.length < 2) return;
  const idx = state.agents.findIndex((a) => a.id === state.selectedAgentId);
  state.selectedAgentId = state.agents[(idx + 1) % state.agents.length].id;
  saveSettings({ agentId: state.selectedAgentId });
  updateAgentIndicators();
}

el("voice-agent-indicator").addEventListener("click", cycleAgent);
el("chat-agent-indicator").addEventListener("click", cycleAgent);

// ---------- voice screen ----------

const voice = {
  socket: null,
  mic: null,
  player: null,
  vad: new SilenceDetector(),
  agentSpeaking: false,
  captionsEl: el("captions-list"),
  orbEl: el("voice-orb"),
  statusEl: el("voice-status"),
  captionsVisible: true,
  currentAssistantCaption: null,
};

function addCaption(role, text) {
  if (!voice.captionsVisible) return null;
  const row = document.createElement("div");
  row.className = `caption-${role}`;
  row.textContent = text;
  voice.captionsEl.appendChild(row);
  voice.captionsEl.scrollTop = voice.captionsEl.scrollHeight;
  return row;
}

function setOrbState(mode) {
  voice.orbEl.classList.remove("listening", "speaking");
  if (mode) voice.orbEl.classList.add(mode);
}

async function enterVoice(conversationId = null) {
  showScreen("voice");
  voice.captionsEl.innerHTML = "";
  voice.currentAssistantCaption = null;
  voice.statusEl.textContent = "Verbinde …";
  setOrbState(null);
  voice.vad.reset();
  voice.agentSpeaking = false;

  voice.player = new AudioPlayer();
  await voice.player.resume();

  voice.socket = new VoiceSocket();
  voice.socket.addEventListener("control", (e) => handleVoiceControl(e.detail));
  voice.socket.addEventListener("audio", (e) => voice.player.enqueue(e.detail));
  voice.socket.addEventListener("closed", () => {
    voice.statusEl.textContent = "Verbindung getrennt";
  });
  voice.socket.addEventListener("error", () => showToast("Verbindungsfehler im Sprachmodus"));

  voice.socket.connect(state.selectedAgentId, conversationId);
  voice.socket.start();

  try {
    voice.mic = new MicCapture();
    await voice.mic.start((chunk) => handleMicChunk(chunk));
  } catch (e) {
    showToast("Mikrofonzugriff fehlgeschlagen: " + e.message);
  }
}

function handleMicChunk(chunk) {
  voice.socket?.sendAudio(chunk);

  if (voice.agentSpeaking) {
    // Barge-in: agent is talking and the user just started speaking again.
    const int16 = new Int16Array(chunk);
    let sumSquares = 0;
    for (let i = 0; i < int16.length; i++) sumSquares += int16[i] * int16[i];
    const rms = Math.sqrt(sumSquares / Math.max(1, int16.length));
    if (rms >= 900) {
      voice.socket?.bargeIn();
      voice.player?.stopAndFlush();
      voice.agentSpeaking = false;
      setOrbState("listening");
    }
    return;
  }

  if (voice.vad.feed(chunk)) {
    voice.socket?.endUtterance();
    voice.vad.reset();
  }
}

function handleVoiceControl(payload) {
  switch (payload.type) {
    case "session_ready":
      state.conversationId = payload.conversation_id;
      voice.statusEl.textContent = "Verbunden";
      setOrbState("listening");
      break;
    case "greeting_text":
      addCaption("assistant", payload.text);
      break;
    case "transcript_partial":
      break;
    case "transcript_final":
      if (payload.text) addCaption("user", payload.text);
      break;
    case "llm_token":
      if (!voice.currentAssistantCaption) {
        voice.currentAssistantCaption = addCaption("assistant", "");
      }
      if (voice.currentAssistantCaption) voice.currentAssistantCaption.textContent += payload.text;
      break;
    case "llm_done":
      voice.currentAssistantCaption = null;
      voice.statusEl.textContent = "Höre zu …";
      break;
    case "tts_start":
      voice.agentSpeaking = true;
      setOrbState("speaking");
      voice.statusEl.textContent = "Spricht …";
      break;
    case "tts_end":
      voice.agentSpeaking = false;
      setOrbState("listening");
      voice.statusEl.textContent = "Höre zu …";
      break;
    case "tool_call":
      addCaption("tool", `🔧 ${describeTool(payload.name)} …`);
      break;
    case "tool_result":
      break;
    case "error":
      showToast(payload.message);
      break;
  }
}

function describeTool(name) {
  const names = {
    get_current_time: "prüfe Uhrzeit",
    get_weather: "prüfe Wetter",
    create_reminder: "lege Reminder an",
    list_reminders: "prüfe Reminder",
    cancel_reminder: "storniere Reminder",
    home_assistant_call_service: "steuere Home Assistant",
    home_assistant_get_state: "prüfe Home Assistant",
    trigger_n8n_workflow: "starte n8n-Workflow",
  };
  return names[name] ?? name;
}

function exitVoice() {
  voice.socket?.stop();
  voice.mic?.stop();
  voice.socket = null;
  voice.mic = null;
}

el("voice-back").addEventListener("click", () => {
  exitVoice();
  showScreen("home");
});

el("voice-captions-toggle").addEventListener("click", () => {
  voice.captionsVisible = !voice.captionsVisible;
  voice.captionsEl.classList.toggle("hidden", !voice.captionsVisible);
});

el("voice-switch-chat").addEventListener("click", () => {
  exitVoice();
  enterChat(state.conversationId);
});

el("home-tap-target").addEventListener("click", () => {
  enterVoice();
});

// ---------- chat screen ----------

const chat = {
  pendingAttachments: [], // {file_id, filename}
  messagesEl: el("messages"),
};

function renderMessage(role, text) {
  const row = document.createElement("div");
  row.className = `bubble-row ${role}`;
  const bubble = document.createElement("div");
  bubble.className = "bubble";
  bubble.textContent = text;
  row.appendChild(bubble);
  chat.messagesEl.appendChild(row);
  chat.messagesEl.scrollTop = chat.messagesEl.scrollHeight;
  return bubble;
}

function renderToolNote(text) {
  const note = document.createElement("div");
  note.className = "tool-note";
  note.textContent = `🔧 ${text}`;
  chat.messagesEl.appendChild(note);
  chat.messagesEl.scrollTop = chat.messagesEl.scrollHeight;
}

function renderSuggestions(suggestions) {
  document.querySelectorAll(".suggestions").forEach((n) => n.remove());
  if (!suggestions?.length) return;
  const wrap = document.createElement("div");
  wrap.className = "suggestions";
  for (const s of suggestions) {
    const chip = document.createElement("button");
    chip.className = "suggestion-chip";
    chip.textContent = s;
    chip.addEventListener("click", () => sendChatMessage(s));
    wrap.appendChild(chip);
  }
  chat.messagesEl.appendChild(wrap);
  chat.messagesEl.scrollTop = chat.messagesEl.scrollHeight;
}

async function enterChat(conversationId = null) {
  showScreen("chat");
  chat.messagesEl.innerHTML = "";
  state.conversationId = conversationId;

  if (conversationId) {
    try {
      const convo = await api.getConversation(conversationId);
      for (const m of convo.messages) {
        renderMessage(m.role, m.content);
        if (m.role === "assistant" && m.suggestions?.length) renderSuggestions(m.suggestions);
      }
    } catch (e) {
      showToast("Konnte Unterhaltung nicht laden");
    }
  }
}

async function sendChatMessage(text) {
  if (!text.trim()) return;
  renderMessage("user", text);
  document.querySelectorAll(".suggestions").forEach((n) => n.remove());

  const assistantBubble = renderMessage("assistant", "");
  const attachmentIds = chat.pendingAttachments.map((a) => a.file_id);
  chat.pendingAttachments = [];
  renderPendingAttachments();

  try {
    await api.sendChatMessage({
      conversationId: state.conversationId,
      agentId: state.selectedAgentId,
      message: text,
      attachmentIds,
      onEvent: (event) => {
        if (event.type === "token") {
          assistantBubble.textContent += event.data.text;
          chat.messagesEl.scrollTop = chat.messagesEl.scrollHeight;
        } else if (event.type === "tool_call") {
          renderToolNote(`${describeTool(event.data.name)} …`);
        } else if (event.type === "done") {
          state.conversationId = event.data.message.conversation_id;
          renderSuggestions(event.data.suggestions);
        } else if (event.type === "error") {
          showToast(event.data.message);
        }
      },
    });
  } catch (e) {
    showToast("Chat-Fehler: " + e.message);
  }
}

function renderPendingAttachments() {
  const wrap = el("pending-attachments");
  wrap.innerHTML = "";
  wrap.classList.toggle("hidden", chat.pendingAttachments.length === 0);
  for (const a of chat.pendingAttachments) {
    const chip = document.createElement("span");
    chip.className = "chip";
    chip.textContent = a.filename;
    wrap.appendChild(chip);
  }
}

el("chat-send-btn").addEventListener("click", () => {
  const input = el("chat-input");
  const text = input.value;
  input.value = "";
  sendChatMessage(text);
});

el("chat-input").addEventListener("keydown", (e) => {
  if (e.key === "Enter") {
    e.preventDefault();
    el("chat-send-btn").click();
  }
});

el("chat-attach-btn").addEventListener("click", () => el("chat-file-input").click());

el("chat-file-input").addEventListener("change", async (e) => {
  const file = e.target.files?.[0];
  e.target.value = "";
  if (!file) return;
  try {
    const uploaded = await api.uploadFile(file);
    chat.pendingAttachments.push(uploaded);
    renderPendingAttachments();
  } catch (err) {
    showToast("Upload fehlgeschlagen");
  }
});

el("chat-mic-btn").addEventListener("click", () => {
  enterVoice(state.conversationId);
});

el("chat-back").addEventListener("click", () => showScreen("home"));

el("chat-history-btn").addEventListener("click", async () => {
  const panel = el("history-panel");
  panel.classList.toggle("hidden");
  if (panel.classList.contains("hidden")) return;
  try {
    const conversations = await api.getConversations();
    const list = el("history-list");
    list.innerHTML = "";
    for (const c of conversations) {
      const item = document.createElement("div");
      item.className = "history-item";
      item.innerHTML = `<div class="title">${escapeHtml(c.title || "Unterhaltung")}</div><div class="preview">${escapeHtml(c.preview || "")}</div>`;
      item.addEventListener("click", () => {
        panel.classList.add("hidden");
        enterChat(c.id);
      });
      list.appendChild(item);
    }
  } catch (e) {
    showToast("Verlauf konnte nicht geladen werden");
  }
});

el("history-close-btn").addEventListener("click", () => el("history-panel").classList.add("hidden"));

function escapeHtml(s) {
  const div = document.createElement("div");
  div.textContent = s;
  return div.innerHTML;
}

// ---------- reminders screen ----------

async function enterReminders() {
  showScreen("reminders");
  await refreshReminders();
}

async function refreshReminders() {
  const list = el("reminders-list");
  list.innerHTML = "<p>Lädt …</p>";
  try {
    const reminders = await api.getReminders();
    list.innerHTML = "";
    if (!reminders.length) {
      list.innerHTML = "<p>Keine offenen Reminder.</p>";
      return;
    }
    for (const r of reminders) {
      const row = document.createElement("div");
      row.className = "reminder-item";
      const due = new Date(r.due_at).toLocaleString("de-DE", {
        day: "2-digit",
        month: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      });
      row.innerHTML = `<div><div class="text">${escapeHtml(r.text)}</div><div class="due">${due}</div></div>`;
      const deleteBtn = document.createElement("button");
      deleteBtn.className = "delete-btn";
      deleteBtn.textContent = "✕";
      deleteBtn.addEventListener("click", async () => {
        await api.deleteReminder(r.id);
        refreshReminders();
      });
      row.appendChild(deleteBtn);
      list.appendChild(row);
    }
  } catch (e) {
    list.innerHTML = "<p>Reminder konnten nicht geladen werden.</p>";
  }
}

el("reminders-btn").addEventListener("click", enterReminders);
el("reminders-back").addEventListener("click", () => showScreen("home"));

for (const chip of document.querySelectorAll("#reminder-quick-chips button")) {
  chip.addEventListener("click", () => {
    const minutes = Number(chip.dataset.minutes);
    const due = new Date(Date.now() + minutes * 60000);
    // datetime-local expects local time without a timezone suffix.
    const pad = (n) => String(n).padStart(2, "0");
    el("reminder-datetime").value =
      `${due.getFullYear()}-${pad(due.getMonth() + 1)}-${pad(due.getDate())}T${pad(due.getHours())}:${pad(due.getMinutes())}`;
  });
}

el("reminder-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const text = el("reminder-text").value.trim();
  const datetimeValue = el("reminder-datetime").value;
  if (!text || !datetimeValue) {
    showToast("Bitte Text und Zeitpunkt angeben");
    return;
  }
  try {
    await api.createReminder(text, new Date(datetimeValue).toISOString());
    el("reminder-text").value = "";
    el("reminder-datetime").value = "";
    refreshReminders();
  } catch (err) {
    showToast("Reminder konnte nicht angelegt werden");
  }
});

// ---------- settings screen ----------

function loadSettingsForm() {
  const settings = getSettings();
  el("settings-backend-url").value = settings.backendUrl;
  el("settings-token").value = settings.token;
  el("settings-dark-mode").value = settings.darkMode;
}

el("settings-btn").addEventListener("click", () => {
  loadSettingsForm();
  showScreen("settings");
});
el("settings-back").addEventListener("click", () => showScreen("home"));

el("settings-form").addEventListener("submit", (e) => {
  e.preventDefault();
  saveSettings({
    backendUrl: el("settings-backend-url").value.trim(),
    token: el("settings-token").value.trim(),
    darkMode: el("settings-dark-mode").value,
  });
  applyTheme();
  showToast("Gespeichert");
  showScreen("home");
  bootstrapAfterConfig();
});

// ---------- proactive events ----------

function connectEventsSocket() {
  state.eventsSocket?.disconnect();
  state.eventsSocket = new EventsSocket();
  state.eventsSocket.addEventListener("event", (e) => {
    const payload = e.detail;
    if (payload.type === "reminder_due") {
      showToast(`⏰ ${payload.reminder.text}`, 8000);
      if (el("screen-reminders").classList.contains("active")) refreshReminders();
    } else if (payload.type === "briefing_ready") {
      showToast(payload.summary, 8000);
    }
  });
  state.eventsSocket.connect();
}

// ---------- bootstrap ----------

async function bootstrapAfterConfig() {
  await Promise.all([refreshStatus(), loadAgents(), refreshWeatherChip()]);
  tickEventsTicker();
  connectEventsSocket();
}

function init() {
  applyTheme();
  tickClock();
  setInterval(tickClock, 1000);
  setInterval(refreshStatus, 30000);
  setInterval(refreshWeatherChip, 600000); // weather triggers an LLM call server-side - keep this infrequent
  setInterval(tickEventsTicker, 60000);

  if (!isConfigured()) {
    loadSettingsForm();
    showScreen("settings");
    return;
  }

  showScreen("home");
  bootstrapAfterConfig();
}

init();
