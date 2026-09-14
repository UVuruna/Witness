# Plan

Milestones, the scenario matrix, and the risk register.

Navigation: [README](../README.md) · [FEATURES](FEATURES.md) · [ARCHITECTURE](ARCHITECTURE.md)

---

## Decisions locked

Newest first; each supersedes what it contradicts.

1. **Two voices means two PEOPLE, by any route** (owner, 2026-09-14). Whether
   the two are captured from one privileged source or through the microphone
   with the speaker on does not matter. What matters is who ends up in the file,
   and that the app never says "both" without having measured it.
2. **Coverage is measured, never promised** (2026-09-14). Whether the far party
   reaches the recording is decided below the app by the manufacturer's audio
   driver. A twenty-second guided test call answers it per phone and per route;
   until it has, the app does not claim to be protecting anyone.
3. **One system setting may be asked for** (owner, 2026-09-14), and only as the
   cure for a MEASURED failure, with the app walking her through it. Turning
   Wi-Fi calling off is the named case: on many phones it is the single
   difference between a recording and silence.
4. **Distribution is GitHub, not Play** (owner, 2026-09-11). The capture
   mechanism cannot pass Play review and the owner chose the mechanism. The app
   is to ship as a signed APK from GitHub Releases and check there for updates —
   neither is built yet (M5). This replaces the 2026-09-01 decision to build
   strictly inside Play policy.
5. **Capture is Shizuku** (owner, 2026-09-11), with one guided pairing the user
   performs on her own phone — no computer, no root. This replaces the
   2026-09-01 decision to use the microphone only; the loudspeaker route
   survives as the fallback that works everywhere.
6. **The SOS is fully automatic** (2026-09-01). Recognized danger phrase →
   location goes to the trusted contacts with no user interaction. A confirmation
   tap was considered and REJECTED: if the phrase was spoken, the abuser may
   already control the user's hands.
7. **Protection ranks equal to recording** (2026-09-01) — hidden identity, PIN,
   encryption and off-phone backup are core, not polish.

## The hard part, named

Android deliberately blocks third-party access to call audio. Both routes out of
that are partial, and the product's honesty rests on admitting which:

- **Shizuku / `VOICE_CALL` as shell.** The privilege is real — the ADB shell uid
  holds `CAPTURE_AUDIO_OUTPUT`. What the buffer then contains is decided by the
  OEM audio HAL: both voices on recent stock Pixels, usually the user alone on
  Samsung since the S9, anything from mixed channels to silence elsewhere, and
  commonly silence over Wi-Fi calling. A further trap: some devices expose
  uplink and downlink as the two channels of one stereo stream, so a mono
  request silently discards one person.
- **Loudspeaker + microphone.** Physically unstoppable and therefore the only
  mechanism that satisfies EVERY PHONE, OR IT DOES NOT COUNT — at the cost of
  being audible to whoever is in the room.

A conference bridge (`ROLE_DIALER` + carrier three-way into a SIP bridge) was
considered and rejected: merge is an unprovisioned supplementary service on much
prepaid and in roaming, and it leaves a second call to the recording number on
the itemized bill, which an abuser reviewing the account can see. That breaks
THE INSPECTION TEST at a layer no app code reaches.

## Milestones

| # | Milestone | Delivers | Feature slugs | State |
|---|-----------|----------|---------------|-------|
| M0 | **Feasibility probe** (throwaway) | What each audio source yields on a real device. | `call-recording` | done, superseded |
| M1 | **Record + protect** | Call-triggered recording service, encrypted vault, hash+timestamp seal. | `call-recording` `vault` | vault + seal done |
| M2 | **Measured capture** | Permissions actually requested, the service that survives Android 14, stereo-first recording with a source ladder, per-channel measurement, the guided test call, honest quality labels, number and direction on Android 12+, the loudspeaker route, VoIP detection. | `call-recording` `voip` `record-lists` | written 2026-09-14, **nothing in it measured on a handset yet** |
| M3 | **The door** | PIN and fingerprint gate, the disguised name and icon, the single-recording screen with player, seal check, share and delete. | `disguise` `vault` | next |
| M4 | **Understand** | On-device whisper.cpp transcription; exact speaker labels where the device gave two channels. | `transcript` | planned |
| M5 | **Release** | Signing config, signed APK, GitHub Release, in-app update check. | — | blocked on the owner |
| M6 | **SOS** | Danger-phrase training, on-device recognition, automatic location message, cancel window. | `sos` | planned |
| M7 | **Backup** | User-connected cloud copy of the vault. | `backup` | planned |

## Scenario matrix

| # | Scenario | Expected behaviour |
|---|----------|---------------------|
| 1 | Incoming call from a number on the record list | Recording starts by itself, no visible sign |
| 2 | Call from a number on the skip list | Nothing recorded, nothing logged — and the check is repeated at call end, when the number is actually known |
| 3 | Number unavailable (withheld or permission denied) | Default rule applies; the row says `UNKNOWN` rather than inventing a direction |
| 4 | The phone splits the call into two channels | Both are recorded, compared, and the row reads "both voices" on evidence |
| 5 | The phone yields one voice only | The row says so, the home screen says so, and the loudspeaker route is offered |
| 6 | Nothing is permitted yet | The app asks, in one numbered list, and never pretends to be armed |
| 7 | Call inside another app (any of them — the detection names none) | Recorded when the loudspeaker route is on; otherwise a visible "not saved" row. **Unmeasured** for every app |
| 8 | Abuser takes the phone and inspects it | Neutral icon, PIN, nothing in gallery/files/recents *(M3)* |
| 9 | Recording offered as evidence | Hash + timestamp prove the file was never altered |
| 10 | Battery dies / reboot mid-call | Partial recording up to that point is sealed and kept |
| 11 | Reboot, then a call before the app is opened | **Unmeasured.** Shizuku must be restarted; the app must recover with one tap |
| 12 | Danger phrase spoken mid-call *(M6)* | Location message leaves silently; short cancel window |

## Risk register

| Risk | Reality | Strategy | Fallback |
|------|---------|----------|----------|
| The quiet route yields one voice | Decided by the OEM audio HAL; unknowable from code | Measure it with the guided test call, per phone, and label every recording from what was measured | The loudspeaker route, which captures both people anywhere |
| Wi-Fi calling records as silence | Common, and the app cannot change the setting itself | Offer the exact settings screen after a failed test — the one system setting the owner allowed asking for | The loudspeaker route |
| Shizuku does not survive a reboot | Android's limit, not ours | The boot receiver restarts the ear; the home screen offers one button to re-arm | **Unproven — scenario 11** |
| Calls in other apps | No modem audio to tap; the call-state signal never fires | Detect through the audio mode — generic, no app list — and record on the loudspeaker | An honest "not saved" row. **Unproven — scenario 7** |
| Distribution outside Play | No store review, no automatic updates | Signed APK from GitHub Releases | **Neither exists yet**: no signing config, and no in-app update check (the app declares no INTERNET permission at all) |
| A user believes she is covered when she is not | The most dangerous failure this product has | Status is `READY` only after a measurement; every other state names itself and gives one action | — |

## Open

- **Signing and release** — `app/build.gradle.kts` has no `signingConfig` and no
  keystore exists, so `assembleRelease` produces an unsigned APK. Creating the
  key and cutting the Release both need the owner.
- **Folder rename** `Safety` → `Witness` ([RENAME](../RENAME.md)).
- Scenario 11 (re-arm after reboot) needs one measurement on a real phone.
