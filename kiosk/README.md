# Jarvis Kiosk

Browser-Client für ein Raspberry Pi + Touchdisplay als physisches
Jarvis-Interface (die ursprünglich vorgesehene Idee, siehe
`../docs/architecture.md` §7). Reines Vanilla-JS/HTML/CSS, kein Build-Schritt,
kein Framework — läuft direkt als statische Seite in Chromium im
Kiosk-Modus. Implementiert denselben Vertrag wie die Android-App
(`../docs/architecture.md`): REST, `/ws/voice`, `/ws/events`.

## Funktionsumfang

- **Home**: Uhrzeit/Datum, dezente Status-Zeile (Backend/Ollama/STT/TTS/HA/n8n,
  antippbar für Details), Wetter-Chip (nur sichtbar wenn konfiguriert),
  Termin-Ticker (erscheint kurz, verschwindet automatisch wieder). Tippen
  irgendwo auf die Mitte → direkt in den Sprachmodus.
- **Voice**: gesprochene Begrüßung inkl. Tages-Briefing, Untertitel-Toggle,
  Barge-in (Reinreden stoppt die Antwort sofort), Wechsel zu Text.
- **Chat**: Bubbles, Streaming-Antworten, Vorschlags-Chips unter der letzten
  Antwort, Verlauf (Slide-in-Panel), Datei-Anhänge.
- **Reminder**: Liste, Anlegen (Schnellauswahl-Chips oder Datum/Uhrzeit),
  Löschen.
- **Settings**: Backend-URL, Geräte-Token, Darstellung (System/Hell/Dunkel).
- **Gedächtnis** (über Settings erreichbar): Liste der Fakten, die sich
  Jarvis über dich gemerkt hat (`remember_fact`-Tool), mit Lösch-Möglichkeit
  - reine Transparenz-/Kontroll-Ansicht, Anlegen passiert normalerweise durch
  den Agenten selbst im Gespräch.
- Proaktiver `/ws/events`-Kanal mit automatischem Reconnect (Backoff) - fällige
  Reminder erscheinen als Toast, auch außerhalb des Voice-/Chat-Screens.

## Setup auf dem Raspberry Pi

Vorausgesetzt: Raspberry Pi OS (Desktop), Touchdisplay, Chromium ist
vorinstalliert.

1. Dieses Verzeichnis (`kiosk/`) auf den Pi kopieren, z. B. nach
   `/home/pi/jarvis-kiosk`.
2. Als statische Seite ausliefern - entweder lokal über einen simplen
   Webserver:
   ```bash
   cd /home/pi/jarvis-kiosk
   python3 -m http.server 8080
   ```
   oder über nginx/Caddy, falls ohnehin vorhanden.
3. Chromium im Kiosk-Modus starten:
   ```bash
   chromium-browser --kiosk --noerrdialogs --disable-infobars \
     --autoplay-policy=no-user-gesture-required \
     http://localhost:8080/index.html
   ```
4. Für Autostart: einen `.desktop`-Eintrag in
   `~/.config/autostart/jarvis-kiosk.desktop` mit obigem Befehl anlegen, oder
   einen systemd-User-Service.

### Mikrofonzugriff (wichtig)

`getUserMedia()` (Mikrofonzugriff für den Sprachmodus) verlangt einen
**secure context** - das ist entweder `https://` oder `http://localhost`.
Wird die Kiosk-Seite lokal auf dem Pi selbst ausgeliefert (Schritt 2/3 oben,
`localhost:8080`), ist das automatisch erfüllt. Wird sie stattdessen von
einem anderen Rechner im Netz geladen (z. B. direkt vom Backend-Host), muss
das über HTTPS laufen (derselbe Reverse Proxy wie für das Backend, siehe
`../backend/README.md`).

### Backend erreichbar machen

Beim ersten Start zeigt die App den Settings-Screen: Backend-URL (z. B.
`https://jarvis.example.tld`) und Geräte-Token (`DEVICE_TOKENS` im Backend)
eintragen. Ohne gültige Verbindung zeigt die Status-Zeile auf dem Home-Screen
konsequent alles als "nicht erreichbar" - das ist erwartetes Verhalten, kein
Absturz.

## Architektur / Aufbau

- `index.html` - alle Screens als `<section>`-Elemente, per CSS-Klasse
  `active` umgeschaltet (kein Router, keine Framework-Abhängigkeit).
- `js/settings.js` - Backend-URL/Token/Darstellung in `localStorage`.
- `js/api.js` - REST-Aufrufe + ein manueller SSE-Parser für
  `POST /api/chat` (kein `EventSource`, da der Browser dafür keine POST-Bodies
  / Custom-Header unterstützt).
- `js/voiceSocket.js` / `js/eventsSocket.js` - WebSocket-Wrapper für
  `/ws/voice` bzw. `/ws/events` (Letzterer mit Reconnect/Backoff).
- `js/audio.js` + `js/capture-processor.js` - Mikrofon-Aufnahme über einen
  `AudioWorkletProcessor`: Der Capture-`AudioContext` wird direkt mit
  `sampleRate: 16000` erzeugt, Chromium resampelt den Mikrofon-Stream dann
  intern - kein manuelles Resampling nötig. Wiedergabe der 24kHz-TTS-Chunks
  läuft über einen zweiten `AudioContext`, mit sequenziellem Scheduling für
  lückenlose Wiedergabe und `stopAndFlush()` für Barge-in.
- `js/vad.js` - dieselbe simple RMS-Sprachaktivitätserkennung wie in der
  Android-App (das Backend hat ohnehin eine serverseitige Silero-VAD als
  Fallback, siehe `../docs/architecture.md` §6).
- `js/app.js` - der gesamte UI-Controller: Screen-Wechsel, Statuszeile,
  Wetter/Termin-Ticker, Agent-Wechsel (einfaches Durchklicken statt Dropdown,
  bei typischerweise wenigen Agenten ausreichend), Voice-/Chat-/Reminder-Logik.

## Getestet

Mit Playwright (Chromium, `--use-fake-device-for-media-stream`) gegen eine
lokale statische Auslieferung geprüft: Screen-Navigation (Settings → Home →
Voice → Reminder), Mikrofon-Worklet lädt fehlerfrei, alle Module laden ohne
404/Syntaxfehler. **Nicht** getestet: echtes Zusammenspiel mit einem
laufenden Backend (kein Ollama/Whisper/XTTS in dieser Sandbox verfügbar) -
das ist der nächste Schritt beim echten Pi+Backend-Setup.

## Bekannte Einschränkungen

- Agent-Wechsel ist ein einfaches Durchklicken (Tap auf den Agenten-Namen im
  Header), kein Dropdown/Bottom-Sheet wie in der Android-App - für wenige
  Agenten ausreichend, bei vielen Agenten ausbaufähig.
- Kein Offline-Modus; ohne erreichbares Backend zeigt die App konsequent
  "nicht erreichbar" statt zwischengespeicherter Daten.
- `GET /api/briefing/today` (Wetter-Chip) löst serverseitig bei vorhandenen
  Fakten einen LLM-Aufruf aus - deshalb pollt der Kiosk das bewusst nur alle
  10 Minuten, nicht häufiger.
