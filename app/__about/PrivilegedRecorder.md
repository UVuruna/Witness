# PrivilegedRecorder (Shizuku)

The one process that can actually hear the call.

## Responsibility

Run inside the process Shizuku spawns as the ADB shell, open the call's own
audio (`VOICE_CALL`), and write it as WAV into a file descriptor the app handed
in.

## Decisions that outlive the code

- **It exists ONLY because it runs as shell.** uid 2000 holds
  `CAPTURE_AUDIO_OUTPUT`; the app's own uid does not, and an ordinary
  `AudioRecord(VOICE_CALL)` in the app is handed silence. Everything here is
  written to run in that other process, reached over AIDL — never called
  in-process.
- **It writes to a descriptor the APP opened, not to a path of its own.** The
  shell process cannot write into app-private storage, and we do not want it
  scattering plaintext into `/data/local/tmp`. The app opens a cache file, wraps
  it as a `ParcelFileDescriptor`, and passes it in; the privileged process only
  fills a pipe the app already owns.
- **Every method is defensive.** This runs with elevated privilege — a thrown
  exception must return as a string the app can show, never a crash that takes
  the shell process down in the middle of a call.
- **`probe()` measures, it does not assume.** It opens every source and reports
  OPENED / REFUSED / ERR per source, so "does VOICE_CALL work on this phone" is
  answered by evidence from the device, not by a guess in a doc.
- **Peak amplitude is returned from `stop()`** so the app can tell "sound
  arrived" from "silence" without decoding the WAV — the first, cheap half of
  the both-voices judgement.

## Unproven until measured on a real phone

That `VOICE_CALL` as shell carries BOTH voices during a LIVE call. The channel
is known to open; the contents are not yet confirmed. This is the project's one
remaining measurement.

## Connections

- Declared by: `capture/shizuku/IRecorderService.aidl`.
- Spawned and reached through: `capture/shizuku/ShizukuManager`.
- Driven by: `capture/RecordingCoordinator`.
