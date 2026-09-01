# Safety

Android app for victims of violence: automatic call recording, on-device
speaker-labeled transcripts, an encrypted tamper-evident evidence vault, and a
voice-triggered silent SOS to trusted contacts.

This file inherits the monorepo constitution (`CLAUDE.md` at the root) and may
only ADD or TIGHTEN its rules — never loosen them.

profiles: phone-portrait
installable: yes

<!-- installable: release = signed AAB uploaded to the Play Console, on the
     owner's word only (BUILD.md applies). There is no NSIS installer. -->

## Stack

- Language / runtime: Kotlin, native Android (min SDK 29, target = current
  Play requirement)
- GUI: Jetpack Compose
- Key libraries: whisper.cpp via JNI (on-device STT), Picovoice Porcupine
  (on-device custom wake phrase, SOS trigger), Jetpack Security
  (EncryptedFile / EncryptedSharedPreferences — the vault)
- Data / storage: app-private encrypted files + Room index; nothing in shared
  storage, nothing in the gallery

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
(no product code yet — M0 feasibility probe is the next milestone)
```

## How to test

```
python -m pytest tests/            guard tests
python tests/run_guards.py         guards, FULL (Stop hook)
python tests/run_guards.py --fast  guards, fast (PostToolUse hook)
```

## Project laws

- **PRIVACY IS A LAW, NOT A FEATURE.** No audio, transcript, or metadata ever
  leaves the device except through a channel the user explicitly enabled
  (backup, SOS message). No analytics, no telemetry, no crash reporting that
  carries content. Ever.
- **THE INSPECTION TEST.** Every feature must survive "the abuser takes the
  phone and looks through it": nothing in the gallery, file manager, share
  sheets, notification history, or recent-apps screen may reveal what this
  app holds.
- **OFFICIAL APIS ONLY** (owner decree 2026-09-01): no Accessibility-API
  recording, no root paths, no reflection into blocked audio sources. If Play
  policy forbids it, this project does not do it — the users cannot afford to
  lose the app to a takedown.
- **THE SOS IS FULLY AUTOMATIC** (owner decree 2026-09-01): once the danger
  phrase is recognized, no tap, no confirmation, no unlock may stand between
  recognition and the outgoing alert — by the time the phrase is spoken, the
  user's hands may not be free. A short audible-free cancel window is the only
  permitted gate.
- RATCHET (files allowed over the structure wall, shrinking only): none.

## Docs

- [README](README.md) — what it is, the name story, the navigation chain root
- [docs/PLAN.md](docs/PLAN.md) — milestones, scenario matrix, Play-policy risk register
- [docs/FEATURES.md](docs/FEATURES.md) — the user-facing feature catalogue
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — module map, data flow, tech decisions

## Open items

- Final project name — candidates proposed 2026-09-01, owner has not picked yet.
- M0 feasibility probe (mic capture during speakerphone call, real devices).
