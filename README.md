# Jarvis

Persönlicher, Jarvis-artiger KI-Assistent als Ersatz für Google
Assistant/Gemini: eigenes Backend (FastAPI, läuft gegen eine nativ
installierte Ollama-Instanz auf einer NVIDIA Tesla P40) plus eine native
Android-App mit Live-Sprachchat (Whisper-STT, Coqui XTTS-v2-TTS statt Piper)
und ChatGPT-artigem Text-Chat.

- [`docs/architecture.md`](docs/architecture.md) — verbindlicher Vertrag:
  Datenmodelle, REST-API, WebSocket-Voice-Protokoll. Startpunkt für alles.
- [`backend/`](backend/README.md) — FastAPI-Service.
- [`android/`](android/README.md) — natives Kotlin/Compose-App.

Home Assistant und n8n sind als deaktivierte Stubs angelegt (siehe
`docs/architecture.md` §8) — der Agent läuft bewusst nicht zwingend über Home
Assistant, das kommt als spätere Erweiterung.
