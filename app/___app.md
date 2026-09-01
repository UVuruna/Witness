# app/ — M0 call-audio probe (throwaway)

Not the product. This is the **M0 feasibility harness** from
[docs/PLAN.md](../docs/PLAN.md): a single-screen Android app whose only job is
to answer the question the whole project hinges on —

> **When a phone call is active, what does the microphone actually deliver to a
> third-party app on THIS device?** Both voices, own voice only, or silence?

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

## Files

| File | Role |
|------|------|
| `MainActivity.kt` | Single-screen host; requests RECORD_AUDIO + READ_PHONE_STATE, shows ProbeScreen. |
| `ui/ProbeScreen.kt` | Compose UI: source chips, live level meter, record/auto controls, recording list with verdicts. |
| `ui/ProbeViewModel.kt` | Starts/stops the service, polls the live level, toggles call-auto mode, lists recordings. |
| `service/RecorderService.kt` | Foreground service (type=microphone) owning one WavRecorder; the only place the mic opens. |
| `audio/WavRecorder.kt` | Captures PCM to a WAV file and measures peak / RMS / silence ratio. |
| `audio/RecordingStore.kt` | App-private WAV + JSON-sidecar storage; the AudioSource list; per-file stats. |
| `call/CallMonitor.kt` | TelephonyCallback (API 31+) / PhoneStateListener wrapper that fires on call start/end. |

## Privacy note

Even as a throwaway, it obeys the project laws: recordings live only in
`filesDir/recordings/`, never shared storage; nothing leaves the device.
