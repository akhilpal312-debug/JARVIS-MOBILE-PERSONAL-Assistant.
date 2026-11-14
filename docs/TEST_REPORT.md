# Jarvis — build and test report

All results below come from commands that were actually run on the build machine
on 14 November 2026. Nothing here is estimated, and no test that could not be
executed is reported as passing.

Build machine: Linux, 2 vCPU, no KVM, **no Android device and no emulator**, no
display. JDK 17 (Temurin), Android SDK platform 35 / build-tools 35.0.0, Gradle
8.11.1, Node 26.

---

## 1. Android build

Command:

```bash
cd android
JAVA_HOME=$HOME/opt/jdk ANDROID_HOME=$HOME/opt/android \
  gradle assembleDebug assembleRelease bundleRelease
```

Result (build log `~/logs/build4.log`):

```
BUILD SUCCESSFUL in 5m 23s
96 actionable tasks: 90 executed, 4 from cache, 2 up-to-date
```

### Artifacts

| Artifact | Path | Size | SHA-256 |
| --- | --- | --- | --- |
| Debug APK | `artifacts/jarvis-1.0.0-debug.apk` | 297.2 MiB (311,662,876 B) | `6a49d1b90767244db2610c282a3626630f09f4dff7e10e46e920b45c52041617` |
| Release APK | `artifacts/jarvis-1.0.0-release.apk` | 290.8 MiB (304,878,413 B) | `d048653e4d8ff48ff0a420c1e98f432548d6f6144bb11f4d3cae84e443f9edeb` |
| Release AAB | `artifacts/jarvis-1.0.0-release.aab` | 207.8 MiB (217,939,473 B) | `b0dda96189fc4b81edb25fe2535526a0715b159abec3f4a6beb98c422000935b` |

`artifacts/SHA256SUMS.txt` holds the same digests.

Manifest identity (`aapt2 dump badging`):

```
package: name='com.jarvis.assistant' versionCode='1' versionName='1.0.0'
minSdkVersion:'26'  targetSdkVersion:'35'  compileSdkVersion='35'
```

Signing (`apksigner verify --print-certs` on the release APK):

```
Signer #1 certificate DN: CN=Jarvis Personal Assistant, OU=Jarvis, O=Jarvis, L=Local, ST=Local, C=IN
Signer #1 certificate SHA-256 digest: b5d61098943f04177bff8de12858d813655b2c79c5cd3a8bcf5cd415ee4d5e09
```

That is the **development keystore** at `android/keystore/jarvis-release.jks`
(generated for this build). Replace it with your own upload key before
publishing to Play — instructions in the README.

### Offline voice payload inside the package

Verified by listing the built packages, not by inspecting sources.

| Check | Release APK | Release AAB |
| --- | --- | --- |
| `assets/models/**` entries | 371 | 371 |
| Bundled model bytes | 247,966,941 B (236.5 MiB) | same |
| espeak-ng-data files (shared Hindi/English phoneme data) | 355 | 355 |
| `libsherpa-onnx-jni.so`, `libonnxruntime.so`, `libsherpa-onnx-c-api.so`, `libsherpa-onnx-cxx-api.so` | arm64-v8a + x86_64 | arm64-v8a + x86_64 |
| Licences | `assets/models/licenses/` present | present |

Key weights and their SHA-256 prefixes:

| File | Bytes | SHA-256 (16) |
| --- | --- | --- |
| `asr/tiny-encoder.int8.onnx` (Whisper tiny multilingual) | 12,937,772 | `d24fb083ae3b1041` |
| `asr/tiny-decoder.int8.onnx` | 89,855,401 | `d2fece8dd42771f1` |
| `tts/hi/model.onnx` (Piper hi_IN priyamvada) | 63,145,178 | `8871f3e07adb6ca4` |
| `tts/en/model.onnx` (Piper en_US lessac) | 63,149,198 | `4ba07d8549906668` |

`assets/models/manifest.json` is a flat `path → byte size` map covering all 371
files; `ModelInstaller` re-checks every entry against the copied file before the
engine loads, so a truncated APK or a partial copy is detected instead of
producing garbage audio. Startup therefore needs no download, no model picker and
no separate APK.

---

## 2. Backend tests

```bash
cd server && node --test test/
```

```
ℹ tests 36
ℹ pass 36
ℹ fail 0
```

Covered: bearer-token auth (401/400/503), device isolation, task lifecycle and
history filtering, memory CRUD and `all=1` clear, SSE streaming frames, degraded
command/chat responses, rate-limit 429, provider-key non-exposure, router
fallback and circuit breaker, prompt validation, JSON tool-call contract parsing
and the health endpoint.

### Bundled artifact test (what actually ships to Vercel)

The Vercel function is built by `server/scripts/bundle.mjs` (esbuild) into one
`api/[...path].js`. That artifact is served through a plain Node HTTP server and
exercised over real HTTP by `server/scripts/test-bundle.mjs`:

```
✔ GET /api/health dispatches to the health route
✔ trailing slash is tolerated
✔ unknown endpoint returns 404 with the endpoint list
✔ nested route requires a token
✔ authorised request reaches the handler
✔ command degrades honestly when no provider is configured

BUNDLE OK
```

Bundle: `server/dist/api/[...path].js`, 28,759 bytes, SHA-256
`f5c87765e053cff0d86aef928fcaa3a90e93bd9c8c1892905d7ba64ece8a3957`.

---

## 3. Deployment status

**Not deployed from this environment.** The Vercel integration available here can
create projects, set environment variables and deploy *inline file contents*, but
it cannot read a 28 KB bundle off this disk, and interactive
`npx vercel login` cannot complete in a headless cloud sandbox. No deployment URL
is reported because none exists — inventing one would be worthless to you.

Deploy it in two minutes from your machine (the backend has no dependencies):

```bash
cd server
npx vercel deploy --prod          # creates the project, builds api/** as functions
```

Then, in Vercel → Project → Settings → Environment Variables, add:

| Name | Required | Value |
| --- | --- | --- |
| `JARVIS_API_TOKEN` | yes | a long random string, e.g. `openssl rand -hex 32` |
| one provider key, e.g. `GROQ_API_KEY` | recommended | from that provider's console |
| `SUPABASE_URL` + `SUPABASE_SERVICE_ROLE_KEY` | optional | enables durable cloud sync (schema in `server/db/schema.sql`) |

`JARVIS_API_TOKEN` is the same value you paste into the app's Settings screen.
Without it every `/api/assistant/*` call correctly answers
`503 server_not_configured`; `/api/health` works either way. With no provider key
the backend answers `200 {degraded:true}` and the phone falls back to its
on-device parser, so nothing breaks.

`OVH_AI_TOKEN` is not needed: OVHcloud's anonymous free tier works keyless at
2 requests/minute once the token above exists.

---

## 4. Not tested — no device available

The build machine has 2 vCPU, no KVM and no display, so no emulator could run.
These items are **implemented but unverified at runtime** and must be checked on a
real phone:

- install, first launch, permission flows (including denial);
- model copy + integrity verification on first launch, and its failure path;
- offline ASR with Hindi / English / Hinglish utterances, and its accuracy;
- offline Hindi and English TTS output quality;
- wake-word detection ("Jarvis") and the foreground service lifecycle;
- reminder delivery, snooze/follow-up, reboot rescheduling, exact-vs-inexact alarm;
- call and media suppression behaviour, audio-focus ducking;
- process death / low-memory recovery and persistence across restart;
- real end-to-end app ↔ deployed-backend integration.

Unit-testable logic was covered where it could be (backend: 36 tests; the Android
project builds cleanly including Compose screens, Kotlin compiles with the
vendored sherpa-onnx API at v1.12.24). Anything involving audio hardware, the
Android runtime or the Play/Play-less install path is open.

## 5. Requirement coverage snapshot

| Requirement | Where |
| --- | --- |
| 11 required screens + drawer | `android/.../ui/screens/` (Home, Voice, Tasks, Reminders, Routines, Memory, History, Models, Voice settings, Privacy, Settings, About) |
| Task board with pending / in-progress / scheduled / blocked / completed history | `data/Repositories.kt`, `ui/screens/TasksScreen.kt` |
| Completed tasks leave the active list and keep evidence | `TaskRepository.complete()`, history query |
| Reminders, snooze, follow-up, reboot receiver | `service/ReminderScheduler.kt`, `receiver/` |
| Routines + memory with edit/delete/clear/pause | `Repository`, `ui/screens/Routine*`, `MemoryScreen` |
| Wake word, foreground service, mic indicator | `service/JarvisService.kt` |
| Call/media awareness | `voice/AudioGuard.kt` |
| Device actions via public intents | `ai/DeviceActions.kt` |
| Provider registry, fallback, circuit breaker, rate limits, SSE | `server/lib/*` |
| Provider keys only on the server | env vars, never echoed in responses or logs (test-covered) |
