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
# Optional screenshots (Robolectric native graphics; first run downloads ~300 MB):
./gradlew testDebugUnitTest -Pscreenshots --tests '*RedesignScreenshotTest*'   # → app/screenshots/*.png (git-ignored)
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
| **Phase 1 Liquid Glass redesign** | done — builds, lint clean (warnings only), unit tests green; NOT device-tested |
| **Phase 2 Ask AI** (global + per-recording chat with cited, tappable sources) | done — unit-tested retrieval; NOT tested against a live provider |
| **Phase 2 Tasks + dates extraction + calendar** | done — unit-tested parsing/grouping; NOT tested against a live provider or a real calendar app |

### Phase 1 — Liquid Glass redesign (done, awaiting device test)
| Item | Status |
|---|---|
| Apple theme (colours, type, dark/light) | done |
| Glass primitives (Haze blur + fallback, specular rim, press springs, haptics) | done |
| iOS component kit (large title, glass top bar, segmented control, search field, iOS switch, capsule buttons, avatars, inset groups/rows) + `HomeLogic` (grouping, snippets, initials, action items) with unit tests | done |
| Floating glass tab bar (Recordings · Tasks · ⏺ · Search · Settings) with sliding glass lens + raised centre record button | done |
| Large-title headers collapsing into glass top bar | done (Home, Tasks, Detail, Settings) |
| Home grouped Today / Yesterday / This week / Older, glass cards, search field, segmented filter, people avatars row | done |
| Full-screen recorder (live waveform, timer, pause/bookmark/stop) + mini pill | done (bookmarks saved to `Recording.bookmarks`) |
| Detail: glass player with waveform scrubber (real loudness envelope, cached), ±10 s, bookmark markers/chips, tap transcript line to seek + live line highlight, Summary/Transcript segments, glass speaker chips, more-menu | done |
| Settings as iOS grouped inset lists (all existing settings kept; single Save in glass top bar saves app + call settings) | done |
| Provider management restyle (numbered fallback order, status pills, iOS switches) | done |
| Tasks tab placeholder (read-only open action items `- [ ]` from minutes) | done (replaced by the real Tasks tab in Phase 2) |
| Compose previews + Robolectric/Roborazzi screenshot tests (opt-in `-Pscreenshots`), downscaled copies in `docs/screenshots/` | done (Settings not rendered: needs `MeetingsApp`) |
| RTL pass: per-string direction (`TextStyle.auto`), LTR isolation for phone numbers, mirrored tab bar/segments/back arrow/scrubber | done |
| Looping animations only while recording (`LocalStaticAnimations` freezes them in tests) | done |

### Phase 2 — Ask AI + Tasks (in progress)
| Item | Status |
|---|---|
| `ai/AskAi.kt` — retrieval: recordings → passages (summary + transcript chunks of ~1400 chars with speaker names), Arabic-aware tokenizer (normalisation, article stripping, en+ar stopwords), BM25 ranking (+title/contact and summary boosts, prefix match), context packed under a token budget (default 5000, chars/2.5 like `TranscriptChunker`), numbered sources `[n]`, fallback to latest summaries when nothing matches; `AskAiTest` (9 tests) | done |
| `OpenAiClient.chat(config, messages)` multi-turn overload (uses the configured summary provider: Groq / OpenAI-compatible) | done |
| `AskAiViewModel` — in-memory conversations per scope (global / recording id), last 6 turns as history, retries with smaller budget (×0.55, min 1500) on 413 / TPM 429 / context-length 400 | done |
| `AskAiScreen` — glass top bar, user bubbles (tint) / assistant glass cards (markdown), numbered source chips → open recording, suggestions, typing indicator, glass composer; en + ar strings | done |
| Entry points: ✨ in Home top bar + "Ask AI about your recordings" row under search (also reached from the Search tab), ✨ in Detail top bar (scoped to that recording) | done |
| `ai/TaskExtractor.kt` — extraction prompt (meeting date as reference for relative dates, minutes + full transcript when it fits in 9000 chars), tolerant JSON parser (code fences, prose, top-level arrays, alias keys `task/owner/deadline/action_items`, string items, null-ish values, dedupe), due parsing (`YYYY-MM-DD` = all-day, `…THH:mm`, ISO offset); `TaskExtractorTest` (7 tests) | done |
| `data/TaskItem` + `TaskStore` (`tasks.json`, atomic writes) + `TaskLogic` (Overdue / Today / Tomorrow / This week / Later / No date buckets, stable ids from normalised title, re-extraction keeps done state); `TaskLogicTest` (5 tests) | done |
| `ai/TaskExtraction.run` — calls the summary provider, halves input on 413/TPM; hooked into `ProcessingWorker` right after the summary (non-fatal); `Recording.tasksExtractedAt`; tasks deleted with their recording | done |
| Tasks tab — Open/Done segments, due-date groups (overdue red, today orange), check circle with haptic, assignee chip, tappable source recording link, per-task "Add to calendar", "Add all N to calendar" (confirm dialog, then one calendar insert screen per task via `startActivities`), ⋯ menu: add all / extract from older recordings / re-extract all; banner "N recordings have no extracted tasks"; progress banner | done |
| Calendar: `TaskCalendar` uses `Intent.ACTION_INSERT` on `CalendarContract.Events.CONTENT_URI` (no permission); all-day → UTC midnight + 1 day, timed → +30 min; description has assignee, deadline wording, source recording | done |
| Detail ⋯ menu: "Extract tasks (n)" re-runs extraction for that recording | done |
| Version bump: `versionCode 2`, `versionName "2.1.0-phase2"` (debug `versionNameSuffix "-v2"` removed so the installed name is exactly `2.1.0-phase2`; applicationId suffix `.v2` unchanged) | done |

## Phase plan
1. **Phase 1 — UI/UX redesign** (Liquid Glass). _Done, accepted._
2. **Phase 2** — Ask AI (chat over recordings) + Tasks & dates extraction with calendar. _In progress._
4. Bookmarks + comments — markers during recording, comments on transcript lines.
5. Export — PDF, Notion, WhatsApp share.
6. Person page — all calls/meetings/tasks per contact.
7. MCP server — expose recordings/transcripts/tasks to external agents.

## Known issues / notes
- Task extraction runs once after each summary; recordings summarized before Phase 2 need "Extract from older recordings" (Tasks ⋯ menu or banner). Bulk extraction is sequential (one provider call per recording) and runs in the activity's ViewModel, so it stops if the app is killed.
- Relative dates are resolved by the model against the recording date; the result is only as good as the model (Groq/Llama may get "next Thursday" wrong). Long recordings without a summary only send the first 9000 chars of transcript.
- "Add all" opens one calendar insert screen per task (stacked); the user saves each. There is no silent bulk insert because that would need WRITE_CALENDAR.
- Done state lives in `tasks.json`; if a re-extraction rewrites a task title, its done state is lost (matching is by normalised title).
- Ask AI conversations are kept in memory only (lost when the process dies). Retrieval is keyword/BM25, not embeddings: synonyms or paraphrases with no shared words can miss; the fallback then uses the latest summaries.
- Ask AI sends excerpts of transcripts/summaries to the configured summary provider (same privacy surface as summaries).
- Nothing in the redesign has been tested on a physical device yet (Samsung, Android 14/15 is the target).
- Haze blur needs Android 12+; older devices get a frosted (non-blurred) fill by design. Robolectric screenshots also show the fallback (no RenderEffect).
- Only floating chrome (top bar, tab bar, player, pill, round buttons) uses real backdrop blur; list cards use a translucent "glass card" fill for scroll performance.
- The live waveform uses `MediaRecorder.maxAmplitude` sampled every 200 ms by `RecordingService`, so it is coarse (5 bars/s).
- Detail waveform is decoded once per recording on-device (MediaCodec, streamed, RMS per 100 ms) and cached in `cacheDir/waveforms`; a long call may take a few seconds before bars appear (placeholder bars meanwhile).
- Tap-to-seek in the transcript needs per-turn timestamps (`Recording.speakerTurns.startMs`); providers that return no timings fall back to a selectable, non-seekable transcript.
- Bookmarks are only captured for in-app meeting recordings (the call recorder does not publish `RecorderState`).
- The previous "Save" in Settings navigated back; Settings is now a tab, so Save stays on the page (one Save saves app + call-recording settings).
- If recording fails to start after tapping ⏺ (e.g. service error), the recorder view will pop up the next time a recording starts. Minor.
- Some strings in My voice progress messages are still English-only literals.

## Next steps
1. Install `riffado-v2-redesign-phase1.apk` on the phone; check blur, haptics, RTL, recorder flow (⏺ → full screen → minimize → pill → stop → lands on detail), call recording still works, QS tile/quick record still work.
2. Fix device findings, then bump `versionCode`.
3. Phase 2 remaining: full verification, APK.
4. Device test: Ask AI with Groq (`llama-3.3-70b-versatile` or similar), task extraction on an Arabic call, calendar insert with Google Calendar / Samsung Calendar.

## Changelog (newest first)
- 2026-10-18: Phase 2 item 3 — versionCode 2 / versionName 2.1.0-phase2.
- 2026-10-18: Phase 2 item 2 — tasks + dates extraction (JSON), real Tasks tab, Add to calendar (single + all), re-extract on demand, tests, screenshots `tasks_*`.
- 2026-10-18: Phase 2 item 1 — Ask AI (retrieval core + tests, chat screen, global + per-recording entry points, ar strings, screenshots `ask_ai_*`).
- 2026-10-18: Phase 1 complete: provider screen restyle, RTL fixes, screenshot tests, idle animation fix. APK built (com.riffado.meetings.v2, ~90 MB).
- 2026-10-18: Settings + My voice redesigned as grouped inset lists; strings moved to resources (en + ar).
- 2026-10-18: Detail screen redesign (player bar, waveform scrubber, seekable transcript).
- 2026-10-18: Navigation shell (tabs + push transitions + recorder overlay), new Home, Recorder, mini pill, tab bar, Tasks placeholder, waveform engine (`Waveform.kt`), `Recording.bookmarks`.
- 2026-10-18: iOS component kit (`IosComponents.kt`) + `HomeLogic` with `HomeLogicTest`.
- 2026-10-18: Phase 1 started — Apple theme + glass primitives; Haze dependency added; this STATUS.md created.
