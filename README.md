# Jarvis — personal AI assistant (Android + Vercel)

Jarvis is a Hindi / English / Hinglish personal assistant that runs its voice
engine **on the phone** and uses a user-owned Vercel backend only for advanced
reasoning.

- **Android app** (Kotlin, Jetpack Compose): offline speech recognition, offline
  neural speech output, wake word, task board, reminders, routines, memory,
  device actions.
- **Backend** (Vercel, zero-dependency Node functions): provider registry with
  fallback across free-tier LLM providers, structured command parsing, optional
  cloud sync.
- **Voice models ship inside the APK** — no model download, no separate model
  APK, no Google speech dependency for the primary engine.

## Repository layout

```
android/                 Android app (Gradle, single :app module)
  app/src/main/assets/models/     bundled Whisper + Piper models and licences
  app/src/main/jniLibs/           sherpa-onnx + onnxruntime native libs
  app/src/main/java/com/k2fsa/    vendored official sherpa-onnx Kotlin API (v1.12.24)
  app/src/main/java/com/jarvis/   the Jarvis app
server/                  Vercel backend + tests
docs/                    test report and evidence
```

## Build the app

Requirements: JDK 17, Android SDK platform 35 + build-tools 35, Gradle 8.11.

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle assembleDebug        # debug APK (signed with the debug key)
gradle bundleRelease        # release AAB (uses keystore/keystore.properties)
```

Release signing: copy `keystore/keystore.properties.example` to
`keystore/keystore.properties`, put your own keystore there, and replace the
sample dev key that was used for the local build. Play App Signing or your own
upload key both work — nothing in the code depends on the sample key.

## Run the backend

```bash
cd server
cp .env.example .env.local     # set JARVIS_API_TOKEN and at least one provider key
npm test                       # 36 tests, no network or quota needed
npm run dev                    # http://127.0.0.1:8787
```

Deploy with `npx vercel deploy --prod` (or import the `server` folder in Vercel),
then set the environment variables in the project settings. See
[server/README.md](server/README.md).

## Connect the app to the backend

Settings → Jarvis backend → paste the deployment URL and the same
`JARVIS_API_TOKEN` value. Provider keys never leave Vercel, and end users never
enter a provider key.

## Voice stack (bundled)

| Purpose | Model | Size |
| --- | --- | --- |
| Speech → text | Whisper **tiny multilingual**, int8 encoder + decoder (sherpa-onnx) | ~100 MB |
| Text → speech (Hindi) | Piper **hi_IN priyamvada** medium (VITS) | ~61 MB + shared espeak-ng data 19 MB |
| Text → speech (English) | Piper **en_US lessac** medium (VITS) | ~61 MB |
| Runtime | sherpa-onnx v1.12.24 + onnxruntime, arm64-v8a + x86_64 | ~53 MB |

Whisper tiny is multilingual, so Hindi, English and Hinglish are recognised by
the same local model with automatic language detection. Models are copied once
from `assets/` into private app storage and verified against
`assets/models/manifest.json` (per-file byte sizes) before loading.

## Privacy and limits

- Wake-word detection, recognition and speech output run locally; background
  audio is never uploaded.
- During a phone call nothing is spoken or listened to. With "stay quiet during
  music & calls" on (default), speech is skipped while media plays; otherwise a
  transient ducking audio-focus request is used and released immediately.
- No accessibility service, no hidden APIs, no call recording, no screen reading.
- Advanced reasoning needs the network and a configured provider. The app says
  so plainly and keeps working locally.
- Exact-alarm scheduling is requested but not assumed: without the permission
  reminders use Android's inexact, doze-safe alarm (±minutes) and the UI says so.

## Tests and evidence

See [docs/TEST_REPORT.md](docs/TEST_REPORT.md) for the commands that were run and
their results, including what could not be executed in a headless environment
(no Android device or emulator was available to this build machine).
