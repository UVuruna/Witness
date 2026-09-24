# Status

Where the work actually stands. Updated at the end of every session, before the
report; if this file and a report disagree, this file is wrong and gets fixed.

Navigation: [README](../README.md) · [PLAN](PLAN.md) · [FEATURES](FEATURES.md) ·
[ARCHITECTURE](ARCHITECTURE.md) · [SETUP GUIDE](SETUP_GUIDE.md)

**Last updated:** 2026-09-24 · app version 0.3.0 · commit after `bc72543`.
No new build this session: the 2026-09-14 debug APK was measured, and it is
newer than every source file, so it IS the current code.

---

## The one sentence

Measured on an Android 16 emulator on 2026-09-24: **the app does not record a
call by either of its routes today.** The quiet route is refused by Android's
audio server before it opens — an identity bug with a known, proven fix — and
the loudspeaker route is silenced by Android for the whole call, which is
platform policy that no app code can change. Nothing in the app may be offered
as protection yet.

## Legend

| Mark | Means |
|---|---|
| ✅ **works** | written, and a measurement outside the code proves it |
| 🟡 **written** | code is in; nothing has proven it behaves, or it is only half right |
| ❌ **broken** | measured, and it fails |
| 🔴 **not built** | does not exist |
| ⏸ **blocked** | waiting on the owner |

Evidence ids (`ev-NNNN`) are rows of session `8276b6bf` in
`.claude/evidence/` (untracked): `ev-0007` is the scripted run of every capture
measurement, `ev-0008` the reboot, the others are screenshots.

## Where each feature stands

| Feature | State | What proves it / what is missing |
|---|---|---|
| Runtime permissions (`call-recording`) | 🟡 written | The setup list shows and ticks all five (ev-0004). The Allow buttons were not exercised (the test granted through adb), and there is no way forward once Android stops showing the dialog after two refusals. |
| The always-on ear (`call-recording`) | ✅ works | Runs as a `microphone` foreground service once the app is opened (ev-0007). After a reboot it does NOT come back by itself; the "Open me once" notice appears and nothing crashes (ev-0008). |
| Quiet route: Shizuku, `VOICE_CALL` (`call-recording`) | ❌ broken | The privileged recorder does run as the shell uid, but AudioFlinger refuses all 8 AudioRecord attempts with `EX_SECURITY` — "invalid attr … uid: 2000" — because the process claims the app's package while running as uid 2000 (ev-0007 B). The same shell process claiming `com.android.shell` opens `VOICE_CALL`, is NOT silenced during the call, and delivers audio (ev-0007 A, D). The fix is one construction change; it is proven in a harness, not yet in the app. |
| Both-voices verdict (`call-recording`) | 🟡 written | `VoiceCheck` stays the only writer of `BOTH_VOICES`, but the measurement it trusts has four design faults — see the audit below. |
| The guided test call (`call-recording`) | ❌ broken | On Android 16 it reports "nothing recorded" and sends her to switch off Wi-Fi calling, which has nothing to do with the failure (ev-0005). The NOTHING verdict is then stored and hides the route for good. |
| Number and direction (`call-recording`) | ❌ broken | `CallIdentity` puts `LIMIT 1` in the sort order; CallLogProvider rejects it for any app without voicemail access ("Invalid token LIMIT", 5 of 5 attempts, ev-0007 B and C). Direction is always UNKNOWN; outgoing calls get no number at all. Incoming calls still carry the number from the ringing broadcast (ev-0003). |
| Loudspeaker route (`call-recording`) | ❌ dead | During a carrier call the app's `UNPROCESSED` capture is `silenced` by the audio policy for the whole call, also with the app in the foreground (ev-0007 C): 345 KB of digital silence, correctly labelled Unusable. AOSP's `AudioPolicyService::updateUidStates_l` silences every capture during a call that cannot bypass the concurrent-capture policy; on this Android 16 build even the shell's MIC is silenced (ev-0007 D). Only the call-audio sources pass. |
| Calls in other apps (`voip`) | 🔴 unmeasured | Detection never observed with a real calling app. Recording would use the loudspeaker route, which the same policy silences while the calling app holds the microphone (AOSP source, research 2026-09-24; not measured). |
| Record / skip lists (`record-lists`) | 🟡 written | The decision logic is right, but its second pass at call end runs against a number that never arrives (see "Number and direction"): an outgoing call to a never-record number is recorded, and in "only my list" mode an outgoing call to a listed number is dropped. |
| Encrypted vault + seal (`vault`) | ✅ works | Encrypts, seals, and verifies (ev-0010 "Sealed and unchanged"). Limits: `androidx.security:security-crypto` has every API deprecated since 1.1.0; the hash covers the ciphertext, so a shared copy cannot be checked against it; the time is the phone's own clock. |
| The recordings list (`vault`) | ✅ works | Rows with number, time, verdict pill (ev-0003, ev-0009). |
| One recording's screen (`vault`) | ✅ works | First seen on a device: verdict, player, seal, empty transcript, share, delete (ev-0010). |
| PIN + fingerprint (`disguise`) | 🔴 not built | The switch does not move when tapped (measured by UI automation). |
| Disguised name and icon (`disguise`) | 🔴 not built | Same: the switch does not move. |
| Speaker-labeled transcript (`transcript`) | 🔴 not built | whisper.cpp is the only on-device option with Serbian at all; quality for tiny/base is unmeasured. |
| Danger-phrase SOS (`sos`) | 🔴 not built | No offline keyword spotter supports Serbian today (Porcupine, openWakeWord, sherpa-onnx, Vosk: none). |
| Off-phone backup (`backup`) | 🔴 not built | M7. |
| Signed APK + GitHub Release | ⏸ blocked | No `signingConfig`, no keystore. |
| In-app update check | 🔴 not built | No `INTERNET` permission, no network code. |

## What only a real phone can answer

The emulator settled everything that is platform policy. What is left is decided
by each manufacturer's audio driver and dialer, and only a live call on a real
handset answers it. `PROVERA.md` in the project root (untracked, written for
the owner in his language) walks through each one.

1. **Does `VOICE_CALL` carry BOTH people on this phone?** Answerable today, with
   no app build, through the M0 probe (`app/probe-tools/CallCap.java`) over adb
   during a live call.
2. **Does the dialer tell the other side?** On the emulator, Google's Phone app
   (233.x) logged no disclaimer event with or without a capture running; that
   says nothing yet about real phones or other regions.
3. **Wi-Fi calling versus VoLTE** on the same phone.
4. **Number and direction** once the LIMIT fix is in.
5. **Re-arm after a reboot.** Measured: opening the app brings the ear back,
   but Shizuku stays stopped until she restarts it (Wi-Fi, wireless debugging,
   Start) — the home screen's "Turn saving back on" does not restart it
   (ev-0009). Shizuku 13.6.0 (GitHub build) can start itself on a trusted
   Wi-Fi on Android 13+; untested.

## What the 2026-09-24 audit found

Every line was measured on the emulator unless marked otherwise. Nothing was
fixed: the session was analysis only, and a build needs the owner's word.

**Blockers — the app cannot do its job until these change**

- The quiet route never opens on Android 16 (identity bug above). Fix: build the
  `AudioRecord` with an attribution for `com.android.shell`.
- The loudspeaker route records silence during every carrier call (platform
  policy). It cannot be repaired; every screen and string that offers it as the
  route "that works on every phone" is untrue.
- Number and direction never arrive (LIMIT in the sort order). Fix: the call
  log's `limit` query parameter, or a Bundle query.
- After a reboot the setup guide and the notice promise "tap the button, a few
  seconds". The button cannot restart Shizuku, so the promise is false on every
  phone (ev-0009).

**Faults in the proof of two voices (code reading)**

- The test call's silent window is timed from OFF-HOOK. For an outgoing call
  that is the moment she dials, so ringing — or the ringback tone, which rides
  the downlink — falls inside "her silence"; her own speech drifting 1.5 s into
  the window is also enough (15 % of it) to read as the other person.
- The test only checks that the OTHER side was heard. A downlink-only capture
  (only the far party, not her) passes as "both people".
- One passed test stamps every later single-channel recording from that route
  as `BOTH_VOICES`, whatever that call actually contained — a call over Wi-Fi
  calling, or a different rung of the ladder, inherits a proof it never earned.
- The privileged ladder falls back to `VOICE_COMMUNICATION` and `MIC` but still
  labels the stream as call audio and tries stereo first, so two microphone
  channels could pass the uplink/downlink test — the exact mistake the
  2026-09-14 audit removed from the loudspeaker recorder.

**Honest-copy faults (code reading)**

- Setup step 4 ends with "that is the last step — you are protected" before any
  test has run.
- The pairing step says "enter the code it shows you"; the code is shown by
  Android's Wireless debugging screen and typed into Shizuku's notification, and
  Wireless debugging will not even switch on without Wi-Fi.
- The guide links Shizuku on Google Play, which carries 13.5.4 (March 2024);
  13.6.0 with the self-start on a trusted Wi-Fi exists only on GitHub.
- The PIN, fingerprint and disguise switches do nothing and do not move.
- A permission refused twice leaves a dead Allow button.

**Platform and policy findings (research, sources in the session report)**

- `androidx.security:security-crypto`: every API deprecated since 1.1.0 (July
  2025), with a long record of keys lost after OS updates on Samsung.
- Android developer verification: enforced from 2026-09-30 in Brazil,
  Indonesia, Singapore and Thailand, globally from 2027. A GitHub-distributed
  APK needs a registered developer ($25, ID, package name + signing key).
- Android 16 Advanced Protection blocks sideloading and updates of sideloaded
  apps — the mode is aimed at exactly the at-risk users this app serves.
- Shizuku's own cost to her: developer options and wireless debugging stay on
  (some banking apps refuse to run; a system "wireless debugging" notice is
  visible), the pairing needs Wi-Fi, and Xiaomi, ColorOS and Huawei add their
  own switches or fail.
- Recording one's own call in Serbia (owner's framing, 2026-09-24): the app only
  ever records the USER'S OWN conversation — her voice and the person she is
  speaking with — never a conversation she is not part of. That is participant
  recording, materially different from the unauthorized interception of others'
  talk that KZ čl. 143 targets. What remains a lawyer's question is admissibility
  as evidence in a given proceeding, not the lawfulness of a party recording her
  own call. The app states what it does and gives no legal advice.

## What was found and fixed in the 2026-09-14 audit

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

- Code comments and user strings still describe the loudspeaker route as the
  one that works on every phone: `MicRecorder.kt`, `SpeakerphoneCaptureSource.kt`,
  `ToolboxApp.kt`, and `strings.xml` in both languages. They change with the fix.
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
- A failed start of the foreground service is caught without a log line, so the
  reboot case leaves no trace in logcat.
