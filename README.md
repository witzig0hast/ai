# Jarvis

Persönlicher, Jarvis-artiger KI-Assistent als Ersatz für Google
Assistant/Gemini: eigenes Backend (FastAPI, läuft gegen eine nativ
installierte Ollama-Instanz auf einer NVIDIA Tesla P40) mit Tool-Calling
(Uhrzeit, Wetter, Reminder, Home Assistant, n8n), Reminder/Timer, einem
proaktiven Push-Kanal und Tages-Briefing, plus drei Frontends gegen denselben
Vertrag: eine native Android-App, ein Browser-Kiosk-Client fürs
Raspberry-Pi-Touchdisplay, und Live-Sprachchat überall (Whisper-STT, Coqui
XTTS-v2-TTS statt Piper, satzweises TTS-Streaming für niedrige Latenz).

- [`docs/architecture.md`](docs/architecture.md) — verbindlicher Vertrag:
  Datenmodelle, REST-API, WebSocket-Protokolle (`/ws/voice`, `/ws/events`),
  Tool-Calling. Startpunkt für alles.
- [`backend/`](backend/README.md) — FastAPI-Service.
- [`android/`](android/README.md) — natives Kotlin/Compose-App.
- [`kiosk/`](kiosk/README.md) — Browser-Client fürs Raspberry-Pi-Touchdisplay.

Home Assistant und n8n sind als deaktivierte Stubs angelegt (siehe
`docs/architecture.md` §8) — der Agent läuft bewusst nicht zwingend über Home
Assistant, ist aber (sobald aktiviert) direkt als Tool nutzbar.
