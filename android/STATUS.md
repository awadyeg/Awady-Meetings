# Awady meetings v2 — Android status / handoff log

_Last updated: 2026-10-18 (Asia/Dubai). Keep this file current: update it after every completed item and commit it._

## Overview
Plaud-style meeting & phone-call recorder for Android. Records meetings (mic) and phone/VoIP calls
(in-app call recorder), transcribes with diarization through a provider fallback chain, names speakers
(on-device voice match + contacts/call log), and writes Markdown minutes with an LLM.

- Branch: `ccr-e1dcbcac-mnypp3` (the `main` branch is the unrelated Next.js web app; Android lives in `android/`).
- Debug applicationId: `com.riffado.meetings.v2`, label **Awady meetings v2** (installs side-by-side with the old `com.riffado.meetings`).
- Signed with the committed `android/app/debug.keystore` (stable signature → updates install over the previous build). Never regenerate it.
- minSdk 26, target/compile 35, Kotlin 2.1.21, Compose BOM 2025.05.01, AGP 8.10.1, Gradle 8.14.3, JDK 17.

## Build / run
```bash
cd android
export JAVA_HOME=/path/to/jdk-17 ANDROID_HOME=/path/to/android-sdk   # sdk.dir in local.properties also works
./gradlew assembleDebug lintDebug testDebugUnitTest
# APK: app/build/outputs/apk/debug/riffado-meetings-debug.apk
aapt2 dump badging app/build/outputs/apk/debug/riffado-meetings-debug.apk | head -1   # package: com.riffado.meetings.v2
```
CI (GitHub Actions) uploads the artifact `riffado-meetings-debug`; download via
`https://nightly.link/awadyeg/riffado/actions/runs/<run-id>/riffado-meetings-debug.zip`.

## Architecture (app/src/main/java/com/riffado/meetings)
| Area | Files |
|---|---|
| App / DI | `MeetingsApp.kt` (stores, singletons), `MainActivity.kt` (navigation shell) |
| Data | `data/` — `RecordingStore` (JSON index + audio segments in filesDir), `Recording`, `SettingsStore` (encrypted keys via `KeystoreCipher`), `TranscriptionProviderConfig`/`TranscriptionStore` (fallback chain), `CallRecordingStore`, `VoiceProfileStore`, `Speakers` (diarized transcript parsing) |
| Recording | `recording/RecordingService` (mic FGS, segmenting, pause/resume, live level → `RecorderState`), `QuickRecordActivity`, `RecordingTileService` (QS tile) |
| Call recorder | `callrecorder/` — AccessibilityService + PHONE_STATE receiver + mic FGS, `CallParticipantResolver` (contacts/call log), diagnostics log |
| AI | `ai/` — `TranscriptionCoordinator` (ordered fallback, quota detection, reset day, `ProviderRecoveryWorker`), clients for ElevenLabs / Munsit / Gemini / Sarvam / OpenAI-compatible, `ProcessingWorker` (WorkManager), `TranscriptChunker` (chunked summaries), speaker ID (`SpeakerIdentifier`, `SpeakerNaming`, sherpa-onnx model bundled as asset) |
| UI | `ui/` — Compose. `Theme.kt` (Apple palette + type), `Glass.kt` (Liquid Glass primitives on Haze), screens (Home, Detail, Recorder, Settings, Provider management, Tasks) |

UI design system (Phase 1): Apple HIG-like colours (`AppleColors`, `Apple.colors`), iOS type scale (`AppleType`),
glass surfaces = `Modifier.glass()` → real backdrop blur via [Haze](https://github.com/chrisbanes/haze) 1.6.10
(RenderEffect on Android 12+, near-opaque frosted fallback below), plus a specular rim (`specularEdge`).
Scrolling content is marked `hazeSource(state)`; floating chrome reads `LocalHazeState`.

## Feature list
| Feature | Status |
|---|---|
| Meeting recording (FGS, segments, pause/resume) | done |
| Audio import button + share target | done |
| In-app call recorder (Accessibility + phone state) | done (Teams call verified; real GSM call not yet tested by user) |
| Background reliability checklist | done |
| Quick record: power double-press shortcut, QS tile, launcher shortcut, vibration | done |
| All / Meetings / Calls filter, search, person filter | done |
| Contact names from contacts / call log | done |
| Diagnostics log + test recorder | done |
| Transcription providers: ElevenLabs, Munsit, Gemini (`gemini-3.5-flash`), Sarvam, OpenAI-compatible | done |
| Provider fallback chain (ElevenLabs → Munsit → Gemini, reset day, auto return) | done |
| Chunked summaries for long calls | done |
| Speaker naming (on-device voice profile + contact for the other side), re-detect | done (not device-tested) |
| RTL for mixed Arabic/English | done; re-checked in redesign |
| **Phase 1 Liquid Glass redesign** | in progress (see below) |

### Phase 1 — Liquid Glass redesign (in progress)
| Item | Status |
|---|---|
| Apple theme (colours, type, dark/light) | done |
| Glass primitives (Haze blur + fallback, specular rim, press springs, haptics) | done |
| iOS component kit (large title, glass top bar, segmented control, search field, iOS switch, capsule buttons, avatars, inset groups/rows) + `HomeLogic` (grouping, snippets, initials, action items) with unit tests | done |
| Floating glass tab bar (Recordings · Tasks · ⏺ · Search · Settings) with sliding glass lens + raised centre record button | done |
| Large-title headers collapsing into glass top bar | done (Home, Tasks, Detail; Settings pending) |
| Home grouped Today / Yesterday / This week / Older, glass cards, search field, segmented filter, people avatars row | done |
| Full-screen recorder (live waveform, timer, pause/bookmark/stop) + mini pill | done (bookmarks saved to `Recording.bookmarks`) |
| Detail: glass player with waveform scrubber (real loudness envelope, cached), ±10 s, bookmark markers/chips, tap transcript line to seek + live line highlight, Summary/Transcript segments, glass speaker chips, more-menu | done |
| Settings as iOS grouped inset lists (all existing settings kept) | not started |
| Provider management restyle | not started |
| Tasks tab placeholder (read-only open action items `- [ ]` from minutes) | done |
| Compose preview screenshots | not started |

## Phase plan
1. **Phase 1 — UI/UX redesign** (Liquid Glass). _In progress._
2. Ask AI — chat over all recordings.
3. Tasks + calendar — extract action items & dates, add to calendar.
4. Bookmarks + comments — markers during recording, comments on transcript lines.
5. Export — PDF, Notion, WhatsApp share.
6. Person page — all calls/meetings/tasks per contact.
7. MCP server — expose recordings/transcripts/tasks to external agents.

## Known issues / notes
- Nothing in the redesign has been tested on a physical device yet.
- Haze blur needs Android 12+; older devices get a frosted (non-blurred) fill by design.
- No GH_TOKEN on the build box at times → commits stay local; push later with `git push origin ccr-e1dcbcac-mnypp3`.

## Next steps
- Continue Phase 1 items in the table above.

## Changelog (newest first)
- 2026-10-18: Detail screen redesign (player bar, waveform scrubber, seekable transcript).
- 2026-10-18: Navigation shell (tabs + push transitions + recorder overlay), new Home, Recorder, mini pill, tab bar, Tasks placeholder, waveform engine (`Waveform.kt`), `Recording.bookmarks`.
- 2026-10-18: iOS component kit (`IosComponents.kt`) + `HomeLogic` with `HomeLogicTest`.
- 2026-10-18: Phase 1 started — Apple theme + glass primitives; Haze dependency added; this STATUS.md created.
