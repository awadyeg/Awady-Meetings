# Riffado Meetings (Android)

Standalone Android app that turns the phone into a meeting recorder: record, transcribe, and summarize without a separate device or a Riffado server.

## Features

- Background recording in a foreground service (screen off, other apps open). Pause, resume, and stop from the notification.
- AAC/m4a, 16 kHz mono, 32 kbps (about 14 MB per hour). Long meetings roll over to a new file every 20 MB so each upload stays under the 25 MB limit of OpenAI and Groq.
- Transcription via any OpenAI-compatible `/audio/transcriptions` endpoint (OpenAI `whisper-1`, Groq `whisper-large-v3`, or a local Whisper server over `http://`), or via Sarvam AI Saaras V4 for Hindi and 22 Indian languages, or via Munsit for Arabic with speaker diarization.
- Speaker separation via ElevenLabs Scribe (`scribe_v2`) and Munsit (`munsit` / `munsit-en-ar`): transcripts come back as `Speaker 1`, `Speaker 2`, ... Speakers can be renamed per recording, and the summary attributes points, decisions, and action items by name. Multi-part recordings are joined on the phone before upload (ElevenLabs) or transcribed per-segment with speaker labels merged (Munsit).
- Meeting minutes via `/chat/completions`: summary, key points, decisions, action items, plus an auto-generated title. Written in the transcript language.
- Runs through WorkManager, so processing continues after the app is closed and retries on network errors and 429/5xx.
- English and Arabic UI.

## Privacy

Recordings, transcripts, and summaries stay in app-private storage on the phone. Audio is sent only to the provider configured in Settings. API keys are encrypted with an AES-256-GCM key held in the Android Keystore. App backup is disabled.

## Build

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
cd android
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

CI (`.github/workflows/android.yml`) builds the debug APK on every push that touches `android/` and uploads it as the `riffado-meetings-debug` artifact.

## Setup on the phone

1. Install the APK (allow installs from unknown sources).
2. Open Settings, pick a preset (OpenAI, Groq, ElevenLabs, Sarvam Saaras for Hindi, or Munsit for Arabic), paste an API key, save.
3. Tap the record button. Grant microphone and notification permissions.
4. Stop the recording. Transcript and summary appear when processing finishes.
5. For existing recordings, use the re-transcribe button to transcribe again with the current provider.

## Automatic speaker naming

Settings → My voice downloads the speaker model once (about 26 MB) and records 30 seconds of enrollment audio. Embeddings are computed on the phone; enrollment audio is not uploaded or retained. The display name and match threshold can be changed in Settings. Re-detect speakers in a recording applies the current profile while preserving manual names.

Munsit segment timestamps and ElevenLabs word timestamps are parsed from internal timed transcript lines into `speakerTurns` associated with their original audio segment. Displayed transcripts and summaries omit timing metadata. Independently transcribed audio files receive distinct speaker labels because provider speaker numbers are local to each request. Older recordings did not retain these timings, and Gemini currently returns untimed text. Re-transcribe with Munsit or ElevenLabs before voice matching these recordings; timing is never inferred from text length. A two-speaker call can name the remaining unnamed participant from the contact name or number even without a voice model, when the other speaker is already named. Ambiguous calls remain unnamed.
