# Status

Where the work actually stands. Updated at the end of every session, before the
report; if this file and a report disagree, this file is wrong and gets fixed.

Navigation: [README](../README.md) · [PLAN](PLAN.md) · [FEATURES](FEATURES.md) ·
[ARCHITECTURE](ARCHITECTURE.md)

**Last updated:** 2026-09-14 · app version 0.3.0 · commit after `bb0559b`

---

## The one sentence

The app is written end to end and builds, and **not one part of the recording
path has been measured on a real phone**. Until a guided test call is run on a
handset, "it records" is a design claim, not a result.

## Legend

| Mark | Means |
|---|---|
| ✅ **done** | written, and something outside the code proves it |
| 🟡 **written** | code is in and compiles; nothing has proven it behaves |
| 🔴 **not built** | does not exist |
| ⏸ **blocked** | waiting on the owner |

## Where each feature stands

| Feature | State | What proves it / what is missing |
|---|---|---|
| Runtime permissions asked (`call-recording`) | 🟡 written | Emulator shows the honest "not allowed yet" screen (ev-0006). The grant flow itself is untried. |
| The always-on ear survives Android 14 (`call-recording`) | 🟡 written | Permission checked before the microphone type is claimed. No live service on a handset yet. |
| Recording, stereo-first, with a source ladder (`call-recording`) | 🟡 written | Compiles; the ladder and the stereo attempt have never met a real call. |
| Both-voices verdict from measurement (`call-recording`) | 🟡 written | `VoiceCheck` is the only writer of `BOTH_VOICES` (grep-verified). The thresholds have never been checked against real audio. |
| The guided test call (`call-recording`) | 🟡 written | The screen and the silent-window reading exist. **This is the measurement everything else waits on.** |
| Number and direction on Android 12+ (`call-recording`) | 🟡 written | Read from the call log with a retry. Needs one real incoming and one real outgoing call. |
| Loudspeaker route (`call-recording`) | 🟡 written | Mono on purpose, least-processed source first. Untested. |
| Calls inside other apps (`voip`) | 🟡 written | Detection is generic — no package list, so every calling app is covered by the same line and none is named. Unmeasured for every app. |
| Record / skip lists (`record-lists`) | ✅ done | Applied twice — early on the ringing number, again at call end against the call log. Guard tests green. |
| Encrypted vault + seal (`vault`) | ✅ done | Jetpack Security + SHA-256 over the stored bytes, taken at close. |
| The recordings list (`vault`) | ✅ done | Grouped by person then time, with search; every verdict shown as a pill. |
| One recording's screen (`vault`) | 🟡 written | Player, seal check, transcript slot, share behind a warning, delete. No recording has existed on a device to open. |
| PIN + fingerprint (`disguise`) | 🔴 not built | The settings switch is present and does nothing. No `BiometricPrompt` anywhere. |
| Disguised name and icon (`disguise`) | 🔴 not built | The settings switch is present and does nothing. No `activity-alias` in the manifest. |
| Speaker-labeled transcript (`transcript`) | 🔴 not built | Needs whisper.cpp through the NDK and a ~75 MB model. Every good recording is currently marked "writing the text…" forever — nothing ever clears it. |
| Danger-phrase SOS (`sos`) | 🔴 not built | M6. |
| Off-phone backup (`backup`) | 🔴 not built | M7. |
| Signed APK + GitHub Release | ⏸ blocked | No `signingConfig` and no keystore, so `assembleRelease` can only emit an unsigned APK. Creating the key and cutting the release both need the owner. |
| In-app update check | 🔴 not built | The app declares no `INTERNET` permission at all and has no network code. |

## The four measurements the whole project waits on

Nobody can move past these by writing more code. Each needs one real call on a
real handset, and [PROVERA.md](../PROVERA.md) walks through them.

1. **Does the quiet route carry BOTH people?** Decided by the manufacturer's
   audio driver; no API reports it. The test call answers it in twenty seconds.
2. **Do the number and the direction arrive?** One incoming and one outgoing
   call.
3. **Is a call inside another app noticed at all?** The audio-mode signal is a
   platform expectation this project has never observed, for any app.
4. **Does the app come back after a reboot?** Android 14 forbids a
   `BOOT_COMPLETED` receiver from starting a microphone service, so the app now
   puts a notification in front of her instead. Whether that path works, and
   whether one tap then restores capture, is unmeasured.

## What was found and fixed in the last audit (2026-09-14)

Five read-only auditors went through the code against its own claims. What they
found in the freshly written capture chain, all fixed in the same session:

- The privileged recorder did not retain the file descriptor it was handed, so
  the first garbage collection in the shell process would have closed it
  mid-call and left a 44-byte file.
- Two microphone channels were being fed to a test written for uplink and
  downlink, so one person talking loudly could have been stamped as evidence —
  the exact failure the half-recording law exists to prevent. The loudspeaker
  route records mono now, and the stream carries a flag saying it is not call
  audio.
- The service could be destroyed mid-call leaving a session open, which would
  have kept a worker thread recording the room and silently dropped every later
  call.
- Both watchers called one shared "the call ended", so either could end the
  other's recording.
- The loudspeaker route for other apps could never be tested, so everything it
  recorded would have been filed "not checked" forever.
- A conversation arriving while another was open vanished with no row, while the
  code's own comment promised the opposite.
- The setup guide told the user to tap a re-arm button on the home screen. There
  was no such button.
- The throwaway M0 probe was compiling into the shipped APK — 55 classes, out of
  the manifest but not out of the dex.

## Still open, not yet acted on

- Below Android 12 the audio-mode watcher polls every two seconds, so the first
  two seconds of a call inside another app are lost on those handsets.
- A "not saved" row from another app looks the same as a failed carrier call —
  nothing on the row says which.
- There is no Kotlin test source set at all; every automated check in the
  project is a Python guard over structure and docs.
- Room's converters call `valueOf` with no fallback, so installing an older APK
  over a newer database would throw when the list is drawn.
- `androidx.work` and `androidx.biometric` are declared and unused, dragging
  permissions, services and receivers into the shipped manifest.
