# Jarvis Android

Native Kotlin/Jetpack Compose client for the Jarvis voice assistant. Built
strictly against `docs/architecture.md` (the shared contract with
`backend/`): REST under `/api/...`, voice over `wss://.../ws/voice`, client
audio PCM16LE mono 16kHz, server TTS audio PCM16LE mono 24kHz.

## Status: NOT compiled/verified in this sandbox

This project was written by hand in a sandbox with **no Android SDK
installed** (`ANDROID_HOME` unset, no `gradlew` run). Every file was
reviewed carefully for correctness, but nothing here has been through
`./gradlew assembleDebug`, a Gradle sync, or a Kotlin compiler pass. **The
first thing to do with this code is open it in Android Studio and let it
sync / build** - that will be the first real compile check, and it's
realistic to expect to need a few small fixes (import misses, a Compose API
signature drift between library versions, etc.) at that point.

## Requirements

- Android Studio (a recent stable release, e.g. Koala/Ladybug or newer)
- JDK 17
- Android SDK: `compileSdk 34`, `targetSdk 34`, `minSdk 26`
- Gradle 8.7 (see `gradle/wrapper/gradle-wrapper.properties`) - the wrapper
  **jar** and `gradlew`/`gradlew.bat` scripts were intentionally left out
  (per task scope, since they can't be fetched/verified here); Android
  Studio will offer to regenerate them on first open, or run
  `gradle wrapper --gradle-version 8.7` once you have a local Gradle
  install.
- Android Gradle Plugin 8.5.2, Kotlin 1.9.24 (both pinned in the root
  `build.gradle.kts` / `app/build.gradle.kts`)

## Opening the project

1. Open the `android/` folder (this directory) directly in Android Studio
   as a project root - not the repo root.
2. Let Gradle sync. If it complains about the wrapper jar being missing,
   use Android Studio's "Upgrade/repair Gradle wrapper" prompt, or run
   `gradle wrapper --gradle-version 8.7 --distribution-type bin` from a
   local Gradle install once inside `android/`.
3. Build/run the `app` module on an API 26+ device or emulator.

## Pointing the app at your backend

Nothing is hardcoded. On first launch, open **Settings** (gear icon, top
right of the Home screen) and fill in:

- **Backend-URL**: e.g. `https://jarvis.example.tld` (no trailing slash
  needed, no `/api` suffix - the app appends `/api/...` and `/ws/voice`
  itself).
- **Geräte-Token**: the bearer token configured server-side via
  `DEVICE_TOKENS` (see `backend/README.md`).

These are persisted via Jetpack DataStore. REST calls send
`Authorization: Bearer <token>`; the `/ws/voice` WebSocket appends
`?token=<token>` as a query param instead (header auth isn't reliable at
the WS handshake, matching architecture.md §3/§6).

If the base URL or token are empty/wrong, REST calls will fail and the
Home screen's status row will show everything as down - that's expected
and not a crash.

## Architecture / structure

- **MVVM, manual DI**: no Hilt/Dagger. `di/AppContainer.kt` is a small
  hand-rolled service locator, constructed once in `JarvisApp` (the
  `Application` subclass) and reached from Composables via
  `(LocalContext.current.applicationContext as JarvisApp).container`.
  Screens build their `ViewModel`s with the `viewModelFactory { initializer
  { ... } }` DSL from `androidx.lifecycle.viewmodel`, wiring in whatever
  repositories they need from the container.
- **Networking**: Retrofit + kotlinx.serialization for the plain REST
  endpoints (`data/remote/JarvisApi.kt`); a hand-rolled SSE line parser over
  a raw OkHttp streaming call for `POST /api/chat`
  (`data/remote/ChatSseClient.kt`, since Retrofit doesn't do SSE); a raw
  OkHttp `WebSocket` wrapper for `/ws/voice`
  (`data/remote/VoiceSocket.kt`), exposing a sealed `VoiceEvent` class as a
  `Flow`. All three share one `OkHttpClient` with two interceptors
  (`data/remote/NetworkInterceptors.kt`): one rewrites Retrofit's
  placeholder base URL to whatever's configured in Settings, the other
  attaches the bearer token.
- **Audio**: `audio/AudioRecorder.kt` (16kHz mono PCM16 capture via
  `AudioRecord`, plus a simple RMS-based VAD purely to help the client
  decide when to send `end_utterance` - the server's Silero VAD is the
  authoritative fallback per architecture.md §6) and `audio/AudioPlayer.kt`
  (24kHz mono PCM16 streaming playback via `AudioTrack`, with
  `stopAndFlush()` for barge-in).
- **Screens**: `ui/screens/{home,voice,chat,history,settings,reminders}`,
  wired up in `ui/navigation/JarvisNavHost.kt`. `agentId`/`conversationId`
  are carried as nav args between Voice and Chat so switching modes
  continues the same conversation with the same agent.

## Tool-calling, reminders, briefing/weather, proactive push (architecture.md §9-§13)

The backend was extended after the first pass; the app was updated to match:

- **Tool-calling transparency** (§9): both `ChatSseEvent` (SSE) and
  `VoiceEvent` (WebSocket) gained `ToolCall`/`ToolResult` variants, parsed
  in `ChatSseClient.kt`/`VoiceSocket.kt`. `ChatViewModel`/`VoiceViewModel`
  turn a `ToolCall` into a small `activeToolLabel` string (friendly German
  label via `ui/components/ToolCallIndicator.kt#toolCallFriendlyLabel`,
  e.g. "prüfe Wetter"), shown as an unobtrusive "🔧 ... …" line
  (`ui/components/ToolCallIndicator.kt`) in both Chat (above the input bar)
  and Voice (between the orb and the captions). It disappears on
  `tool_result` or the next streamed token, whichever comes first - never a
  blocking dialog/spinner.
- **Reminders** (§10): `data/model/Reminder.kt`,
  `data/repository/ReminderRepository.kt`, `JarvisApi.kt` (`GET/POST
  /api/reminders`, `DELETE /api/reminders/{id}`), and a new
  `ui/screens/reminders/{RemindersScreen.kt,RemindersViewModel.kt}` - a
  list of open (non-fired) reminders with a delete icon per row and a "+"
  FAB that opens a dialog: reminder text, quick-pick chips (10/30/60/180
  min from now, create immediately on tap), or a "Datum/Uhrzeit wählen"
  button using the classic `android.app.DatePickerDialog` +
  `TimePickerDialog` for an arbitrary due time. Entry point: a bell icon
  top-left on the Home screen, mirroring the dark-mode toggle top-right.
- **Briefing + weather** (§12/§13): `data/model/BriefingResponse.kt`,
  `data/repository/BriefingRepository.kt` (new, alongside
  `StatusRepository` - kept separate to stay focused), `JarvisApi.kt`
  (`GET /api/briefing/today`). `HomeViewModel` polls it every 15 minutes
  and exposes only the `weather` field as a `StateFlow<WeatherInfo?>`;
  `HomeScreen` renders a small "14°C, klar" line under the date **only**
  when non-null - same "never show an empty state" principle as the
  existing calendar ticker. The Voice screen's spoken greeting is now
  longer (briefing-based instead of a static sentence); no code change was
  needed there since the caption `Text` already wraps freely with no
  `maxLines` cap - verified by re-reading `VoiceScreen.kt`'s
  `CaptionsList`.
- **Long-term memory** (§14): `data/model/MemoryFact.kt`,
  `data/repository/MemoryRepository.kt`, `JarvisApi.kt` (`GET/POST
  /api/memory`, `DELETE /api/memory/{id}`), and a new
  `ui/screens/memory/{MemoryScreen.kt,MemoryViewModel.kt}` - list of
  remembered facts with a delete icon per row, **no create UI** (the agent
  writes facts itself via the `remember_fact` tool; this screen is purely
  transparency/control, per the coordinator's instruction). Entry point:
  an OutlinedButton "Gedächtnis verwalten" on the Settings screen
  (deliberately not on Home, to keep the ambient look uncluttered). The
  three new tool names (`remember_fact`, `list_remembered_facts`,
  `forget_fact`) got German labels in
  `ui/components/ToolCallIndicator.kt#toolCallFriendlyLabel`.
- **Proactive push channel** (§11): `data/remote/EventsSocket.kt` wraps
  `wss://.../ws/events` - receive-only, with exponential backoff
  reconnect (2s, doubling, capped at 60s, reset on a successful open).
  `JarvisEventsService.kt` is a foreground service (`foregroundServiceType
  ="dataSync"`, channel id `jarvis_events`/"Jarvis") that owns one
  `EventsSocket` for the process's lifetime and turns `reminder_due`
  pushes into a normal system notification ("Jarvis erinnert dich" /
  the reminder text); `briefing_ready` is parsed (forward-compatible) but
  intentionally a no-op today, per the coordinator's note that the backend
  doesn't send it yet.

### Notification permission & when the push service starts

**Chosen design: started automatically on app launch, not a Settings
opt-in toggle.** On `MainActivity` creation, `StartEventsServiceEffect()`
requests `POST_NOTIFICATIONS` (API 33+ only; a no-op permission before
that) via the standard Compose `rememberLauncherForActivityResult` flow,
then starts `JarvisEventsService` via `ContextCompat.startForegroundService`
**regardless of whether the permission was granted** - a denied permission
means the service still runs (keeping the reminder-due websocket open is
harmless and needed for future in-app use), it just can't post the visible
notification: `JarvisEventsService.postReminderNotification` checks
`ContextCompat.checkSelfPermission` before calling
`NotificationManagerCompat.notify` and silently skips if not granted,
rather than crashing with a `SecurityException`. If a Settings opt-in
toggle is preferred instead later, add a `DataStore` flag next to
`darkModeOverride` in `SettingsDataStore.kt`, gate the
`ContextCompat.startForegroundService` call on it in
`StartEventsServiceEffect()`, and stop the service (via an `Intent` +
`stopService`) when the user turns it off.

## Known limitation: not a system Assistant replacement (yet)

This app is a normal launcher app you open (or tap Home -> Voice) - it does
**not** register as the phone's default Assistant. See the doc comment at
the top of `MainActivity.kt` for the summary; in short, replacing Google
Assistant requires:

1. A `VoiceInteractionSessionService` + `VoiceInteractionService` pair
   declared in the manifest.
2. Requesting `RoleManager.ROLE_ASSISTANT` at runtime so the user can pick
   this app as their system assistant.
3. Handling assistant-invocation entry points (long-press home, the
   assistant gesture, etc.) inside the `VoiceInteractionSessionService`'s
   own UI rather than a normal `Activity`.

This is intentionally out of scope for this pass (architecture.md §14) and
is the natural next build-out step once the current app is verified
end-to-end against the backend.

## Deviations from the spec / things to flag for review

- **Dark-mode toggle behavior**: the Home screen's top-right toggle flips
  the Settings dark-mode override directly to the opposite of whatever is
  currently displayed (light<->dark), rather than cycling through
  System/Light/Dark. All three states are still reachable from the
  Settings screen's radio group.
- **`StatusRepository`** (`data/repository/StatusRepository.kt`) was added
  beyond the suggested file list (which only named `AgentRepository`,
  `ConversationRepository`, `VoiceRepository`) to keep `/api/status` and
  `/api/calendar/upcoming` out of the other repositories' way. Small,
  additive, no spec conflict.
- **DELETE endpoints** (`deleteAgent`, `deleteConversation`) return
  `Response<Void>` rather than `Response<Unit>` - a deliberate Retrofit
  idiom so an empty/204 response body doesn't trip the kotlinx.serialization
  converter (see comment in `JarvisApi.kt`).
- Agent CRUD (`POST/PUT/DELETE /api/agents`) is defined in `JarvisApi` for
  completeness against architecture.md §5, but there's no in-app "create/edit
  agent" screen - the spec only calls for an agent *switcher*, not an
  editor.
- No unit/instrumented tests were written, matching the "no `./gradlew`"
  constraint for this pass - there was no way to run them here.
- **`BriefingRepository`** was added (like `StatusRepository` before it)
  beyond the originally suggested file list, for the same reason: keeps
  `GET /api/briefing/today` out of the other repositories' way.
- **Push notification service start policy** was left to this pass's
  judgment per the coordinator's instructions ("app start or a Settings
  toggle, your choice") - see "Notification permission & when the push
  service starts" above for the reasoning and the toggle alternative.
- The reminder due-date "custom" picker uses the classic
  `android.app.DatePickerDialog`/`TimePickerDialog` (not a Compose Material3
  date/time picker) to avoid pulling in more experimental M3 APIs than
  necessary for a small secondary flow; swap it for
  `DatePicker`/`TimePicker` from `androidx.compose.material3` if a fully
  in-theme picker UI is wanted later.
- `JarvisEventsService`'s foreground notification uses
  `R.drawable.ic_launcher_foreground` as its small icon (reusing the
  existing adaptive-icon foreground layer, which is already a white
  silhouette on transparent) rather than adding a dedicated monochrome
  notification icon asset.

## Suggested first steps for whoever picks this up

1. Open in Android Studio, resolve the Gradle wrapper, sync, and fix
   whatever the compiler flags (expect this to be a short list, not a
   rewrite).
2. Point Settings at a running `backend/` instance and click through Home
   -> Voice -> barge-in -> Chat -> History once end-to-end.
3. Sanity-check `AudioRecord`/`AudioTrack` behavior on a real device -
   emulator microphones are unreliable for VAD testing.
