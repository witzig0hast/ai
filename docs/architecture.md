# Jarvis – Architektur & Schnittstellenvertrag

Dieses Dokument ist der verbindliche Vertrag zwischen `backend/` und `android/`.
Beide Seiten werden getrennt entwickelt und müssen sich nur an dieses Protokoll
halten, damit sie kompatibel bleiben.

## 1. Ziel

Ein persönlicher, Jarvis-artiger Sprachassistent als Ersatz für Google
Assistant/Gemini auf dem Smartphone, mit Live-Sprachchat (nicht nur
Sprachbefehl → Antwort, sondern durchgehender Dialog mit Unterbrechung/
Barge-in), Text-Chat im ChatGPT-Stil, mehreren wählbaren Agenten/Personas,
und späterer Anbindung an Home Assistant und n8n.

## 2. Hardware/Runtime-Setup (Backend-Host)

- Debian-Server, GPU: NVIDIA Tesla P40 (Pascal, 24 GB VRAM, **kein** schnelles
  FP16/keine Tensor Cores → für STT/TTS wird int8/float32-Inferenz statt fp16
  eingesetzt, siehe `backend/README.md`).
- Ollama läuft **nativ** auf dem Host (kein Docker), damit parallel z. B. Open
  WebUI auf dieselbe Ollama-Instanz zugreifen kann. Das Backend spricht Ollama
  ausschließlich über dessen HTTP-API an (`OLLAMA_HOST`, Default
  `http://localhost:11434`) – startet/verwaltet den Ollama-Prozess nicht.
- STT: `faster-whisper` (CTranslate2), GPU-beschleunigt, `compute_type`
  konfigurierbar (Default `int8_float32` für Pascal-Kompatibilität).
- TTS: Coqui **XTTS-v2** (kein Piper – Sprachqualität war der Ausschlusgrund),
  Streaming-Inferenz, Voice-Cloning aus Referenzsample möglich.
- Das Backend selbst (FastAPI) kann containerisiert oder nativ laufen; Ollama
  bleibt in jedem Fall nativ auf dem Host.

## 3. Transport

- Eigene Domain, z. B. `https://jarvis.example.tld`, terminiert TLS (Reverse
  Proxy wie Caddy/nginx davor – nicht Teil dieses Repos, siehe Backend-README).
- REST unter `/api/...`, WebSocket-Voice unter `/ws/voice`.
- Auth: Bearer-Device-Token (`Authorization: Bearer <token>` bei REST,
  `?token=<token>` Query-Param beim WebSocket-Upgrade, da Browser/OkHttp dort
  keine Custom-Header beim Handshake garantieren). Tokens werden serverseitig
  über `DEVICE_TOKENS` (kommagetrennte Liste) konfiguriert. Das ist bewusst
  simpel gehalten für ein Einzelnutzer-Setup – siehe „Bekannte
  Einschränkungen" unten für Härtungshinweise.

## 4. Domänenmodell

```
Agent
  id: str (slug, z.B. "jarvis")
  name: str
  description: str
  system_prompt: str
  ollama_model: str        # z.B. "llama3.1:70b"
  voice_id: str             # Referenz auf XTTS-Speaker/Sample
  avatar_color: str         # Hex, für UI-Avatar
  is_default: bool

Conversation
  id: str (uuid)
  agent_id: str
  title: str                 # aus erster Nachricht generiert
  created_at, updated_at: datetime

Message
  id: str (uuid)
  conversation_id: str
  role: "user" | "assistant"
  content: str
  attachments: [Attachment]
  suggestions: [str]          # nur bei role=assistant, Folgefragen-Vorschläge
  created_at: datetime

Attachment
  id: str
  filename: str
  content_type: str
  size_bytes: int
```

## 5. REST-API

Alle Antworten JSON, Fehler als `{"error": {"code": str, "message": str}}`
mit passendem HTTP-Status.

### `GET /api/status`
Liefert Health-Übersicht für die Status-Zeile im Home-Screen.
```json
{
  "ollama": true,
  "stt": true,
  "tts": true,
  "home_assistant": false,
  "n8n": false,
  "active_agent": "jarvis"
}
```
`home_assistant`/`n8n` sind Feature-Flags (siehe §8) – solange nicht
konfiguriert, immer `false`, nie ein Fehler.

### Agents
- `GET /api/agents` → `Agent[]`
- `POST /api/agents` → erstellt Agent, Body ohne `id`
- `GET /api/agents/{id}`
- `PUT /api/agents/{id}`
- `DELETE /api/agents/{id}` (Default-Agent kann nicht gelöscht werden → 400)

### Conversations / History
- `GET /api/conversations` → Liste `{id, agent_id, title, updated_at, preview}`
  (neueste zuerst)
- `GET /api/conversations/{id}` → `{..., messages: Message[]}`
- `DELETE /api/conversations/{id}`

### Chat (Text-Modus, SSE-Streaming)
`POST /api/chat`
Body:
```json
{
  "conversation_id": "uuid oder null (= neue Conversation)",
  "agent_id": "jarvis",
  "message": "Text der Nutzerin/des Nutzers",
  "attachment_ids": ["..."]
}
```
Response: `text/event-stream`, Events:
- `event: token` `data: {"text": "..."}` – LLM-Tokens, fortlaufend anhängen
- `event: done` `data: {"message": Message, "suggestions": ["...", "..."]}`
- `event: error` `data: {"message": "..."}`

Grund für SSE statt WebSocket im Text-Modus: einfacher, zustandslos,
HTTP-cachefreundlich; der bidirektionale Voice-Modus braucht dagegen
WebSocket (§6).

### Anhänge
`POST /api/files` – `multipart/form-data`, Feld `file`
→ `{"file_id": "...", "filename": "...", "content_type": "..."}`
Wird vor dem Senden einer Chat-Nachricht mit Anhang aufgerufen; die
zurückgegebene `file_id` kommt dann in `attachment_ids`.

### Kalender/Termine (Ticker im Home-Screen)
`GET /api/calendar/upcoming?within_minutes=180`
→ `[{"title": "...", "start_time": "ISO8601", "location": "..."}]`
Vorerst Stub (leeres Array oder Mockdaten), spätere Anbindung an Home
Assistant Kalender-Entities oder CalDAV – siehe §8.

## 6. WebSocket-Voice-Protokoll — `wss://.../ws/voice?agent_id=jarvis&conversation_id=<uuid|neu>&token=<token>`

Ziel: durchgehender Live-Dialog mit Unterbrechbarkeit (Barge-in), nicht nur
Anfrage/Antwort. Audio vom Client: PCM16 mono, 16 kHz. Audio vom Server
(TTS-Output): PCM16 mono, 24 kHz (native XTTS-v2-Ausgaberate).

### Framing
Zwei Frame-Typen auf derselben Verbindung:
- **Text-Frames**: JSON, siehe unten.
- **Binary-Frames**: rohe PCM16LE-Samples, kein Header/Envelope.

### Client → Server
| Typ | Payload | Bedeutung |
|---|---|---|
| Text `{"type":"start","sample_rate":16000}` | – | Session-Start, vor erstem Audio-Frame |
| Binary | PCM16LE-Chunks | laufender Audiostrom während Nutzer spricht |
| Text `{"type":"end_utterance"}` | – | Client-VAD hat Sprechende erkannt |
| Text `{"type":"barge_in"}` | – | Nutzer beginnt zu sprechen, während der Agent noch antwortet → Server bricht laufende TTS-Ausgabe sofort ab |
| Text `{"type":"stop"}` | – | Session sauber beenden |

Server macht zusätzlich serverseitige VAD (Silero) als Fallback, falls der
Client kein `end_utterance` schickt (z. B. bei Netzwerkausfall am Client) –
Details siehe Backend-README.

### Server → Client
| Typ | Payload | Bedeutung |
|---|---|---|
| Text `{"type":"session_ready","conversation_id":"..."}` | – | nach Verbindungsaufbau |
| Text `{"type":"greeting_text","text":"..."}` + folgende `tts_start`/Audio/`tts_end` | – | gesprochene Begrüßung inkl. Namen + Briefing beim Betreten des Voice-Modus |
| Text `{"type":"transcript_partial","text":"..."}` | – | laufende STT-Zwischenergebnisse |
| Text `{"type":"transcript_final","text":"..."}` | – | finales STT-Ergebnis der Äußerung |
| Text `{"type":"llm_token","text":"..."}` | – | LLM-Token für Untertitel |
| Text `{"type":"tts_start"}` | – | vor erstem Audio-Chunk der Antwort |
| Binary | PCM16LE 24kHz | Audio-Chunks der Antwort |
| Text `{"type":"tts_end"}` | – | nach letztem Audio-Chunk |
| Text `{"type":"llm_done","full_text":"...","suggestions":["...","..."]}` | – | Antwort komplett, inkl. Vorschlägen für Folgefragen |
| Text `{"type":"error","message":"..."}` | – | Fehlerfall, Session bleibt offen |

Bei `barge_in` bricht der Server laufendes TTS/LLM-Streaming ab und wartet
auf die neue Äußerung; bereits gestreamte Audio-Chunks werden clientseitig
sofort gestoppt (AudioTrack `pause()+flush()`).

## 7. Ordnerstruktur

```
backend/    FastAPI-Service ("Jarvis Core")
android/    Natives Kotlin-App ("Jarvis Android"), Jetpack Compose
docs/       dieses Dokument + weitere Notizen
```

## 8. Home Assistant / n8n – bewusst nur Stubs

Der Agent soll **nicht zwingend** über Home Assistant laufen; das kommt als
Erweiterung später. Aktueller Stand:
- `backend/app/services/integrations/home_assistant.py` und `.../n8n.py`
  existieren als Client-Klassen mit klaren Methoden (`call_service`,
  `get_states` bzw. `trigger_webhook`), sind aber über
  `HOME_ASSISTANT_ENABLED=false` / `N8N_ENABLED=false` per Default
  deaktiviert. `/api/status` meldet sie dann korrekt als `false`, ohne
  Fehler zu werfen. Aktivierung erfolgt später per `.env`.

## 9. Bekannte Einschränkungen dieses Grundgerüsts

- Auth ist ein einfacher geteilter Bearer-Token pro Gerät, kein OAuth/mTLS –
  für den öffentlichen Domain-Einsatz sollte das vor Produktivbetrieb
  gehärtet werden (z. B. mTLS oder ein Reverse-Proxy mit Auth davor).
- Die Android-App kann in dieser Sandbox nicht kompiliert werden (kein
  Android SDK verfügbar) – Code ist manuell geprüft, aber nicht per
  `./gradlew assembleDebug` verifiziert.
- „Google Assistant ersetzen" (Rolle als Standard-Assistent unter Android)
  braucht eine `VoiceInteractionService`/`VoiceInteractionSessionService`
  plus Rollenanfrage (`RoleManager.ROLE_ASSISTANT`); dieses Grundgerüst legt
  die App/den Voice-Modus an, die eigentliche Assistant-Rollen-Integration
  ist als nächster Ausbauschritt markiert (siehe `android/README.md`).
