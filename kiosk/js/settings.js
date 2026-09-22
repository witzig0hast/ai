const STORAGE_KEY = "jarvis-settings";

const defaults = {
  backendUrl: "",
  token: "",
  darkMode: "system", // "system" | "light" | "dark"
  agentId: "jarvis",
};

export function getSettings() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? { ...defaults, ...JSON.parse(raw) } : { ...defaults };
  } catch (e) {
    return { ...defaults };
  }
}

export function saveSettings(partial) {
  const merged = { ...getSettings(), ...partial };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
  } catch (e) {
    // Private mode / storage disabled - the app still works for this session.
  }
  return merged;
}

export function isConfigured(settings = getSettings()) {
  return Boolean(settings.backendUrl);
}

export function httpBase(settings = getSettings()) {
  return settings.backendUrl.replace(/\/+$/, "");
}

export function wsBase(settings = getSettings()) {
  const http = httpBase(settings);
  if (http.startsWith("https://")) return "wss://" + http.slice("https://".length);
  if (http.startsWith("http://")) return "ws://" + http.slice("http://".length);
  return http;
}
