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
- **It contributes a ladder, not an engine.** The recording itself is
  `capture/PcmRecorder`, shared with the app-side microphone recorder (ONE KIND,
  ONE CLASS). The one thing only this class can supply is the list of sources a
  privileged uid may name: `VOICE_CALL` first, then `VOICE_DOWNLINK` — the far
  party alone, which is the half the victim cannot produce herself and therefore
  the half worth having.
- **The app never names a source.** Which sources exist and which may be opened
  is knowledge of the privileged side, so the AIDL takes only a descriptor and
  reports back what actually opened.
- **`probe()` measures, it does not assume.** It opens every source in both
  stereo and mono and reports OPENED / REFUSED / ERR, so "does VOICE_CALL work on
  this phone" is answered by evidence from the device, not by a guess in a doc.
- **`stop()` returns a measurement, not a number.** It hands back the encoded
  `capture/CaptureOutcome`: the source that opened, the channel count, and the
  per-channel energy. The previous `int` peak could only say "not silent", which
  is what let a one-sided recording pass as evidence.

## Unproven until measured on a real phone

That `VOICE_CALL` as shell carries BOTH voices during a LIVE call. The channel
is known to open; the contents are decided by the device's audio driver. That is
no longer a gap in the design — the guided test call answers it per phone, and
until it has, recordings from this route are `UNVERIFIED` rather than evidence.

## Connections

- Declared by: `capture/shizuku/IRecorderService.aidl`.
- Engine: `capture/PcmRecorder`.
- Spawned and reached through: `capture/shizuku/ShizukuManager`.
- Offered by: `capture/shizuku/ShizukuCaptureSource`.
