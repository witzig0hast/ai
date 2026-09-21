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
- **Screens**: `ui/screens/{home,voice,chat,history,settings}`, wired up in
  `ui/navigation/JarvisNavHost.kt`. `agentId`/`conversationId` are carried
  as nav args between Voice and Chat so switching modes continues the same
  conversation with the same agent.

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

This is intentionally out of scope for this pass (architecture.md §9) and
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

## Suggested first steps for whoever picks this up

1. Open in Android Studio, resolve the Gradle wrapper, sync, and fix
   whatever the compiler flags (expect this to be a short list, not a
   rewrite).
2. Point Settings at a running `backend/` instance and click through Home
   -> Voice -> barge-in -> Chat -> History once end-to-end.
3. Sanity-check `AudioRecord`/`AudioTrack` behavior on a real device -
   emulator microphones are unreliable for VAD testing.
