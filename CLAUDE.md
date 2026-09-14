# Safety

Android app for victims of violence: automatic call recording, on-device
speaker-labeled transcripts, an encrypted tamper-evident evidence vault, and a
voice-triggered silent SOS to trusted contacts.

This file inherits the monorepo constitution (`CLAUDE.md` at the root) and may
only ADD or TIGHTEN its rules — never loosen them.

profiles: phone-portrait
installable: yes

## Stack

- Language / runtime: Kotlin, native Android (min SDK 29, target = current
  Play requirement)
- GUI: Jetpack Compose
- Key libraries: Shizuku (ADB-privileged capture), Jetpack Security, Room,
  DataStore. PLANNED, absent: whisper.cpp JNI (STT), Porcupine (SOS phrase)
- Data / storage: app-private encrypted files + Room index; nothing shared

**Why native Kotlin** (Step-3 justification, START.md): the product IS the
platform's telephony surface — call detection, foreground services with
microphone type, restricted permissions, PackageInstaller-independent Play
delivery — all first-class only in native Android. Considered and rejected:
Flutter / React Native (telephony + background audio + JNI whisper.cpp all
live behind plugins that lag the platform; every hard part of this app would
be native anyway, the framework would only wrap it) and C# MAUI (weakest
telephony/audio ecosystem of the three, and no house experience advantage —
the WPF decree is desktop-only). iOS is out of scope: Apple exposes no
third-party call-recording API; a future iOS companion may import recordings
made by the system recorder for transcription and vaulting only.

## How to run

```
gradlew :app:assembleDebug                       build the APK
uv device android-phone <apk> --start-emulator   install on an emulator + screenshot
```

JAVA_HOME must point at a JDK 17+ (this machine uses Android Studio's bundled
JBR — see `gradle.properties`). The debug build deliberately drops FLAG_SECURE
so screenshots work; the release build keeps it.

## How to test

```
python -m pytest tests/            guard tests
python tests/run_guards.py         guards, FULL (Stop hook)
python tests/run_guards.py --fast  guards, fast (PostToolUse hook)
```

## Project laws

Owner decrees, newest first. Each is WHAT · WHO CHECKS.

- **A RECORDING WITHOUT BOTH VOICES IS NOT A RESULT** (2026-09-11). Nothing may
  be delivered — plan, table, feature or report — whose answer to "is the other
  party in the recording?" is no. **Two voices means two PEOPLE, by any route**
  (owner, 2026-09-14): the audio path does not matter, so speakerphone plus
  microphone satisfies it in full. · `tests/hook_no_half_recording.py` (Stop
  hook), pinned by `tests/test_half_recording_hook.py`.
- **EVERY PHONE, OR IT DOES NOT COUNT** (2026-09-11). A mechanism confined to
  one manufacturer is not a mechanism. The built-in dialer recorder is DEAD and
  is never raised again. · review.
- **EVERY OPTION ARRIVES WHOLE** (2026-09-11). Never name an idea without, in
  the same breath: what we build · what the owner provides · every step the user
  takes · what is still unproven · what it costs · which phones it covers. ·
  review.
- **ONE SETUP, GUIDED, THEN NOTHING** (2026-09-11, widened 2026-09-14). The user
  may be asked for ONE setup she performs on her own phone, with the app leading
  her step by step and no computer involved — Shizuku's wireless-debugging
  pairing is the approved case. A system setting she changes herself is allowed
  only as the cure for a MEASURED failure, guided by the app — Wi-Fi calling off
  is the named case (2026-09-14). Everything after it is automatic, reboot
  included; where auto-restart fails the app offers one button, never a
  procedure. Still forbidden: root, firmware, anything needing a PC. · review.
- **PRIVACY IS A LAW, NOT A FEATURE.** No audio, transcript or metadata leaves
  the device except through a channel the user explicitly enabled. No analytics,
  no telemetry, no crash reporting carrying content. · review.
- **THE INSPECTION TEST.** Every feature survives "the abuser takes the phone
  and looks through it": nothing in the gallery, file manager, share sheet,
  notification history or recents reveals what the app holds. · review.
- **DISTRIBUTION IS GITHUB, NOT PLAY** (2026-09-11). The capture mechanism
  cannot pass Play review, and the owner chose the mechanism. The app ships as a
  signed APK from GitHub Releases and checks there for updates. A Play build, if
  it ever exists, is a separate flavour without capture. · review.
- **THE SOS IS FULLY AUTOMATIC** (2026-09-01). Once the danger phrase is
  recognized, no tap, no confirmation and no unlock stands between recognition
  and the outgoing alert. A short silent cancel window is the only gate. ·
  review.
- RATCHET (files allowed over the structure wall, shrinking only): none.

## Docs

- [README](README.md) — what it is, the name story, the navigation chain root
- [docs/PLAN.md](docs/PLAN.md) — milestones, scenario matrix, risk register
- [docs/FEATURES.md](docs/FEATURES.md) — the user-facing feature catalogue
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — module map, data flow, tech decisions
- [docs/STATUS.md](docs/STATUS.md) — where the work stands, what is unproven

## Open items

- **Capture is Shizuku first, loudspeaker always** (2026-09-11 / 2026-09-14).
  Both-voices coverage is MEASURED per phone by the guided test call. Still
  unproven: re-arming after a reboot.
- **Folder rename** `Safety` → `Witness` ([RENAME.md](RENAME.md)) — still not
  executed; the tool refuses to run from inside the folder.
- **applicationId** is `com.pebblesoft.toolbox` (neutral, as RENAME.md requires).
  It can still change until the first store upload; after that it is forever.
- **The probe** (`com/uvuruna/callprobe/`) is out of the manifest AND out of the
  build. It stays on disk until the owner says it may be deleted.
