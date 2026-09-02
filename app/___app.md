# app/ — M0/M0.5 feasibility probe (throwaway)

Not the product. This is the **feasibility harness** from
[docs/PLAN.md](../docs/PLAN.md). It answers the questions the project hinges
on, one throwaway screen, two probes:

> **M0 — call audio:** when a phone call is active, what does each audio
> source actually deliver to a third-party app on THIS device — both voices,
> only the user, only the far side, or nothing?
>
> **M0.5 — continuous listening:** can a microphone foreground service run for
> hours, the way the SOS wake-phrase listener must — at what battery cost, and
> does the OS suspend it?

**Neither question is answered yet.** An earlier round wrote "answered:
silence" into this file without a single measurement behind it — no recording,
no device log, no evidence entry — and the whole product plan was then bent
around that unmeasured sentence. The claim is withdrawn; the answer comes from
the matrix below, run on a real phone, or it does not exist.

It is documented here as one module (its files are Trivial per
[DOCS.md](../../../rules/DOCS.md)); at **M1** the real subsystems replace it and
graduate to their own `__about/` docs. Nothing here ships to Play.

## What it does

1. Pick an `AudioSource`. Ordinary sources (MIC, VOICE_RECOGNITION,
   VOICE_COMMUNICATION, UNPROCESSED, CAMCORDER) are what any app may open.
   **Privileged sources** (VOICE_CALL, VOICE_UPLINK, VOICE_DOWNLINK,
   REMOTE_SUBMIX) are the ones that actually carry telephony audio; the
   platform reserves them for callers holding `CAPTURE_AUDIO_OUTPUT`, so an
   ordinary app is expected to be refused. The probe offers them anyway —
   a recorded refusal, with the device's own error text, is a measurement;
   an assumed one is not.
2. Choose the route: **force loudspeaker** or leave the call on the earpiece.
   The loudspeaker is the only lever an ordinary app has (it puts the far
   side's voice into the room where the microphone can reach it), so every
   source is measured both ways.
3. Record manually ("Record now") or automatically when a call goes off-hook
   ("Auto on call").
4. While recording, a foreground service (`type=microphone`) captures 16 kHz
   mono PCM to an app-private WAV and measures **peak amplitude** and the
   **ratio of silent buffers**.
5. **Play the recording back inside the app and say who you hear** — BOTH
   voices / only ME / only the OTHER side / nothing. This is the M0 answer.
   Amplitude can prove that sound arrived; only the owner's ear can say whose
   voice it was, and that distinction is the entire question.

### The measurement protocol

Two people, one real call, one recording per row:

| | earpiece | loudspeaker |
|---|---|---|
| MIC | | |
| VOICE_RECOGNITION | | |
| VOICE_COMMUNICATION | | |
| UNPROCESSED | | |
| CAMCORDER | | |
| VOICE_CALL *(privileged)* | | |
| VOICE_UPLINK *(privileged)* | | |
| VOICE_DOWNLINK *(privileged)* | | |
| REMOTE_SUBMIX *(privileged)* | | |

During each recording: the far side speaks alone for ~10 seconds, then the
owner speaks alone for ~10 seconds. Play it back afterwards and tap the
verdict — that ordering makes "only me" and "only them" unmistakable.

### Listen mode (M0.5)

The **Listen mode** card arms an hours-long microphone foreground service
(`listen/ListenService.kt`) that writes NO audio — it keeps the mic open
levels-only, logs a heartbeat every minute (battery %, charging, peak,
loud-event count) to an app-private JSONL, and the card turns the last
session into a verdict: battery drain per hour, and heartbeat gaps = every
moment the OS suspended the listener. Procedure: arm, grant the battery
exemption, unplug, leave overnight, read the verdict; a clap test checks
the loud-event counter, and the status bar must show the green mic dot the
whole time (it cannot be hidden — a product design fact, not a bug).

## Files

| File | Role |
|------|------|
| `MainActivity.kt` | Single-screen host; requests RECORD_AUDIO + READ_PHONE_STATE, shows ProbeScreen. |
| `ui/ProbeScreen.kt` | Compose UI: source chips (ordinary + privileged), route switch, live level meter, record/auto controls, recording list with playback and the by-ear verdict. |
| `ui/ProbeViewModel.kt` | Starts/stops the service, polls the live level, toggles call-auto mode and the speaker route, drives playback, stores verdicts. |
| `service/RecorderService.kt` | Foreground service (type=microphone) owning one WavRecorder; the only place the mic opens and the only place the audio route is set. |
| `audio/WavRecorder.kt` | Captures PCM and measures peak / RMS / silence ratio; writes WAV when given a file, levels-only when not. |
| `audio/RecordingStore.kt` | App-private WAV + JSON-sidecar storage; the AudioSource registry; per-file stats and the owner's by-ear verdict. |
| `audio/Playback.kt` | Plays one recording back so the owner can judge whose voice was captured. |
| `call/CallMonitor.kt` | TelephonyCallback (API 31+) / PhoneStateListener wrapper that fires on call start/end. |
| [`probe-tools/`](probe-tools/___probe-tools.md) | Shell-identity call-audio probe (CallCap.java) — run via ADB, not installed as an app; measures the privileged VOICE_CALL path. |
| `listen/ListenService.kt` | M0.5: hours-long mic foreground service — minute heartbeats, battery state, loud-event counter. |
| `listen/ListenLog.kt` | M0.5: JSONL heartbeat log + last-session summary (drain %/h, OS-suspension gaps). |

## Privacy note

Even as a throwaway, it obeys the project laws: recordings live only in
`filesDir/recordings/`, never shared storage; nothing leaves the device.
