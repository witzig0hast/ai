# Jarvis Backend

FastAPI-Service für den persönlichen Jarvis-Assistenten: REST-API (Chat,
Agents, Verlauf, Anhänge, Kalender-Stub) plus ein WebSocket-Endpoint für den
Live-Sprachdialog. Der volle Vertrag (Datenmodelle, REST-Endpoints,
WebSocket-Protokoll) steht in [`../docs/architecture.md`](../docs/architecture.md)
— dieses README beschreibt nur Setup/Betrieb.

## Warum dieser Stack

- **Ollama läuft nativ** auf dem Debian-Host (kein Docker), damit z. B. Open
  WebUI dieselbe Instanz mitnutzen kann. Dieses Backend startet/verwaltet
  Ollama nicht, sondern spricht es nur über dessen HTTP-API an.
- **STT: faster-whisper** (CTranslate2) statt der originalen `openai-whisper`-
  Implementierung — deutlich schneller, GPU-tauglich.
- **TTS: Coqui XTTS-v2**, bewusst **nicht Piper** — Piper klingt für den
  Anwendungsfall zu unnatürlich; XTTS-v2 bietet natürlichere Stimmen und
  Voice-Cloning aus einem kurzen Referenzsample.

## Hardware-Hinweis: NVIDIA Tesla P40

Die P40 ist eine Pascal-GPU: 24 GB VRAM, aber **keine Tensor Cores und kein
schnelles FP16**. Deshalb:
- `WHISPER_COMPUTE_TYPE=int8_float32` (Default in `.env.example`) statt
  `float16` — auf Pascal ist int8/float32-Inferenz die praktikable Wahl.
- XTTS-v2 läuft in FP32 auf der GPU; das ist auf der P40 langsamer als auf
  einer Ampere/Ada-Karte, aber mit 24 GB VRAM problemlos machbar. Falls die
  Latenz im Live-Voice-Modus zu hoch ist, zuerst ein kleineres Whisper-Modell
  (`medium` statt `large-v3`) probieren, bevor an der TTS-Konfiguration
  gedreht wird — STT ist meist der größere Latenzblock.
- Beide Modelle laden **lazy** beim ersten echten Request, nicht beim
  Serverstart — `/api/status` zeigt `stt`/`tts: true` erst, sobald sie
  tatsächlich im GPU-Speicher liegen.

## Setup

```bash
cd backend
./scripts/install_native.sh   # legt .venv an, installiert Torch (CUDA) + requirements.txt
cp .env.example .env          # falls das Skript es nicht schon getan hat
$EDITOR .env                  # DEVICE_TOKENS, OLLAMA_HOST, Modellnamen setzen
```

Referenz-Samples für XTTS-Voice-Cloning ablegen (mind. ~10s saubere Sprache,
16-24kHz WAV):
```
data/voices/default.wav        # Fallback für alle Agenten ohne eigene Stimme
data/voices/<voice_id>.wav      # pro Agent, siehe Agent.voice_id
```

Start (Entwicklung):
```bash
.venv/bin/uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Dauerbetrieb: `scripts/systemd/jarvis-backend.service` nach
`/etc/systemd/system/` kopieren, Pfade/User anpassen, dann:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now jarvis-backend
```

Eigene Domain: TLS-Terminierung übernimmt ein Reverse Proxy (Caddy/nginx)
davor, der auf `127.0.0.1:8000` proxied — nicht Teil dieses Repos.

## Tests

```bash
pip install -r requirements.txt   # oder nur die leichten Pakete für Unit-Tests,
                                    # STT/TTS werden lazy geladen und sind für
                                    # die Tests unten nicht nötig
pytest
```

`tests/test_health.py` deckt Startup, `/api/status` und die
Default-Agent-Seeding-Logik ab — ohne GPU, ohne laufendes Ollama (der
Chat-Endpoint selbst braucht für einen echten End-to-End-Test natürlich
Ollama + Modelle).

## Home Assistant / n8n

Aktuell bewusst nur Stub-Clients (`app/services/integrations/`), per
`HOME_ASSISTANT_ENABLED=false` / `N8N_ENABLED=false` deaktiviert — der Agent
soll nicht zwingend über Home Assistant laufen, das ist ein späterer
Ausbauschritt (siehe `docs/architecture.md` §8). Sobald aktiviert, sind sie
auch als Tools nutzbar (siehe unten).

## Neue Fähigkeiten: Tool-Calling, Reminder, Wetter, Briefing, Push-Events

Der Agent kann jetzt tatsächlich etwas tun, nicht nur reden — Details im
Vertrag unter `docs/architecture.md` §9–§13. Kurzfassung:

- **Tool-Calling** (`app/services/tools.py`, `app/services/tool_loop.py`):
  Modelle mit Tool-Support (z. B. `llama3.1`) können `get_current_time`,
  `get_weather`, `create_reminder`/`list_reminders`/`cancel_reminder` sowie
  (falls aktiviert) `home_assistant_call_service`/`home_assistant_get_state`/
  `trigger_n8n_workflow` aufrufen — sowohl im Text-Chat als auch im
  Sprachdialog. Läuft transparent: normale Antworten ohne Tool-Bedarf
  streamen weiterhin Token für Token wie bisher, nur wenn das Modell
  tatsächlich ein Tool anfragt, gibt es eine (unsichtbare) Zwischenrunde.
- **Reminder/Timer**: `/api/reminders` (CRUD) + ein Hintergrund-Task
  (`app/services/reminder_scheduler.py`), der fällige Reminder alle
  `REMINDER_POLL_INTERVAL_SECONDS` erkennt und über `/ws/events` pusht.
- **Wetter**: `app/services/weather_service.py` nutzt die kostenlose,
  key-lose Open-Meteo-API. Ohne `HOME_LATITUDE`/`HOME_LONGITUDE` in `.env`
  bleibt das Feature inaktiv (kein Fehler, einfach kein Wetter in
  Tool-Antworten/Briefing).
- **Tages-Briefing**: `GET /api/briefing/today` fasst Wetter + offene
  Reminder + (später) Kalendertermine in einem kurzen, vom LLM formulierten
  Satz zusammen. Wird auch als gesprochene Begrüßung beim Betreten des
  Voice-Screens verwendet (`greeting_text` in `/ws/voice`).
- **Proaktiver Event-Kanal**: `/ws/events` — reine Server→Client-Pushes
  (`reminder_due`, künftig `briefing_ready`), unabhängig vom Voice-Channel,
  damit die App auch ohne offenen Chat/Voice-Screen benachrichtigen kann.
- **Satzweises TTS-Streaming**: `/ws/voice` synthetisiert jetzt Satz für
  Satz, sobald ein Satz vom LLM fertig generiert ist (statt auf die
  komplette Antwort zu warten) — spürbar niedrigere Latenz bis zur ersten
  hörbaren Antwort im Live-Gespräch (`app/services/sentence_splitter.py`).

## Bekannte Einschränkungen

Siehe `docs/architecture.md` §14 (Auth ist ein einfacher geteilter
Bearer-Token, kein OAuth/mTLS — für den Domain-Einsatz vor Produktivbetrieb
härten). Die Tool-Loop begrenzt sich auf maximal 4 Runden pro Anfrage, um
Endlosschleifen zu verhindern.
