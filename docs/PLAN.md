# Plan

Milestones, the MVP scenario matrix, and the Play-policy risk register.

Navigation: [README](../README.md) · [FEATURES](FEATURES.md) · [ARCHITECTURE](ARCHITECTURE.md)

---

## Decisions locked (owner, 2026-09-01)

1. **One recording + software diarization**, not two audio channels. Android
   gives no app access to the call's own audio streams; the microphone during
   a speakerphone call captures both voices mixed, and speaker separation
   happens in the transcript, not in the file.
2. **Android only, Google Play only, official APIs only.** The owner has a
   Play developer account; the project ships nothing Play policy forbids.
   iOS later at most as an import-and-analyze companion (Apple's native
   recorder makes the file; our app would vault and transcribe it).
3. **The SOS is fully automatic.** Recognized danger phrase → location goes to
   the trusted contact(s) with no user interaction. A confirmation tap was
   considered and REJECTED by the owner: if the phrase was spoken, the abuser
   may already control the user's hands. Trusted contacts: one or more, the
   user's choice.
4. **Protection ranks equal to recording** — hidden identity, PIN, encryption
   and off-phone backup are core, not polish (the abuser often has access to
   the victim's phone).

## Feasibility & duplication (START.md gate)

- **Duplication:** nothing equivalent in the monorepo (this is its first
  mobile safety app). Outside: personal-safety apps exist (panic buttons,
  location sharing) and call recorders exist, but no Play-compliant app
  combines automatic recording + on-device speaker-labeled transcripts +
  tamper-evident vault + voice-triggered SOS for this audience. The
  combination is the product.
- **The hard part, named:** Android deliberately blocks third-party access to
  call audio. Our path — microphone capture (speakerphone where needed) with
  software diarization — is officially allowed but device-dependent: some
  OEMs mute the microphone for other apps during a call. Therefore **M0 is a
  throwaway feasibility probe, not a scaffold**: prove on real devices what
  the microphone actually hears during a call, per device profile.

## Milestones

| # | Milestone | Delivers | Feature slugs |
|---|-----------|----------|---------------|
| M0 | **Feasibility probe** (throwaway) | Measured answer per test device: what does mic capture yield during a call — both voices / own voice only / silence? With and without speakerphone. | `call-recording` |
| M1 | **Record + protect** | Call-triggered recording service, encrypted vault, hash+timestamp seal, PIN lock, neutral identity. | `call-recording` `vault` `disguise` |
| M2 | **Understand** | On-device whisper.cpp transcription + diarization → timestamped speaker timeline. | `transcript` |
| M3 | **Lists + Play release** | Record/skip lists, restricted-permission declarations, store listing, closed testing → production. | `record-lists` |
| M4 | **SOS** | Danger-phrase training + on-device recognition, automatic location message to trusted contacts, cancel window. | `sos` |
| M5 | **Backup** | User-connected cloud copy of the vault. | `backup` |

## Scenario matrix (MVP = M0–M3)

| # | Scenario | Expected behaviour |
|---|----------|---------------------|
| 1 | Incoming call from a number on the record list | Recording starts by itself, no visible sign |
| 2 | Call from a number on the skip list | Nothing recorded, nothing logged |
| 3 | Number unavailable (permission denied) | Default rule applies (record everything) |
| 4 | Call ends, phone has no internet | Transcript is still produced (on-device STT) |
| 5 | Abuser takes the phone and inspects it | Neutral icon, PIN, nothing in gallery/files/recents |
| 6 | Phone destroyed or confiscated | Off-phone copy of the vault exists (if user enabled it) |
| 7 | Recording offered as evidence | Hash + timestamp prove the file was never altered |
| 8 | Battery dies / reboot mid-call | Partial recording up to that point is sealed and kept |
| 9 | OEM mutes the mic during calls | App detects it and tells the user honestly, never records silence in secret |
| 10 | Danger phrase spoken mid-call (M4) | Location message leaves silently to all trusted contacts; short cancel window |

## Play-policy risk register

| Risk | Reality | Strategy | Fallback |
|------|---------|----------|----------|
| Recording the call itself | No app may touch the call audio stream; Accessibility-API recording is banned outright. | Microphone capture only, speakerphone where the device requires it; prominent-disclosure consent flow at onboarding. | If a device yields nothing usable, the app says so per scenario 9 — never a silent broken promise. |
| Caller number for lists | `READ_CALL_LOG` / `READ_PHONE_STATE` are restricted permissions needing a Play declaration. | Submit the declaration (safety use case). | Lists degrade gracefully: record-everything default works with no number access (scenario 3). |
| Automatic SOS message | `SEND_SMS` is a restricted permission; declaration approval is not guaranteed. | Submit the declaration (emergency use case). | **Internet relay, still fully automatic**: the alert goes over the network to an SMS gateway or to the contact's own app — no restricted permission, no user tap. The automatic promise survives either way. |
| Disguised identity | Play bans impersonation and misleading store listings. | The STORE listing is honest; the on-device name/icon choice is the user's own setting (established pattern in shipped victim-safety apps). | Ship a small set of neutral looks vetted against policy; the store identity never changes. |
| Background microphone | Foreground service with `microphone` type + visible notification is mandatory. | Use exactly that; the notification wears the neutral identity. | — |

## Open

- Final project name — owner picks from proposed candidates.
- M0 device list — which physical devices the probe runs on.
