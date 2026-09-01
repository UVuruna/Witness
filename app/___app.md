# app/ — M0/M0.5 feasibility probe (throwaway)

Not the product. This is the **feasibility harness** from
[docs/PLAN.md](../docs/PLAN.md). It answers the questions the project hinges
on, one throwaway screen, two probes:

> **M0 — call audio:** when a phone call is active, what does the microphone
> actually deliver to a third-party app on THIS device? *(Answered 2026-09-01:
> silence — the OS holds the call mic exclusively; ambient capture outside
> calls is perfect.)*
>
> **M0.5 — continuous listening:** can a microphone foreground service run for
> hours, the way the SOS wake-phrase listener must — at what battery cost, and
> does the OS suspend it?

It is documented here as one module (its files are Trivial per
[DOCS.md](../../../rules/DOCS.md)); at **M1** the real subsystems replace it and
graduate to their own `__about/` docs. Nothing here ships to Play.

## What it does

1. Pick an `AudioSource` (MIC, VOICE_RECOGNITION, VOICE_COMMUNICATION,
   UNPROCESSED, CAMCORDER) — different devices expose different ones during a
   call, so the probe tries them all.
2. Record either manually ("Record now") or automatically when a call goes
   off-hook ("Auto on call").
3. While recording, a foreground service (`type=microphone`) captures 16 kHz
   mono PCM to an app-private WAV and measures **peak amplitude** and the
   **ratio of silent buffers**.
4. Each finished recording shows a plain verdict: *AUDIO CAPTURED* /
   *MOSTLY SILENT (likely muted during call)* / *SILENT (device gave the mic
   nothing)* / *FAILED (source unavailable)*.

The test procedure on a real device: put a call on speakerphone, arm auto mode
with each source in turn, speak from both ends, then read the verdicts.

### Listen mode (M0.5)

5. The **Listen mode** card arms an hours-long microphone foreground service
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
| `ui/ProbeScreen.kt` | Compose UI: source chips, live level meter, record/auto controls, recording list with verdicts. |
| `ui/ProbeViewModel.kt` | Starts/stops the service, polls the live level, toggles call-auto mode, lists recordings. |
| `service/RecorderService.kt` | Foreground service (type=microphone) owning one WavRecorder; the only place the mic opens. |
| `audio/WavRecorder.kt` | Captures PCM and measures peak / RMS / silence ratio; writes WAV when given a file, levels-only when not. |
| `audio/RecordingStore.kt` | App-private WAV + JSON-sidecar storage; the AudioSource list; per-file stats. |
| `call/CallMonitor.kt` | TelephonyCallback (API 31+) / PhoneStateListener wrapper that fires on call start/end. |
| `listen/ListenService.kt` | M0.5: hours-long mic foreground service — minute heartbeats, battery state, loud-event counter. |
| `listen/ListenLog.kt` | M0.5: JSONL heartbeat log + last-session summary (drain %/h, OS-suspension gaps). |

## Privacy note

Even as a throwaway, it obeys the project laws: recordings live only in
`filesDir/recordings/`, never shared storage; nothing leaves the device.
