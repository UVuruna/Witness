# RecordingCoordinator

The conductor: decide, record, seal, file — in that order.

## Responsibility

Turn "a call happened" into "a row in the vault, with an honest quality" — or
into nothing at all when the lists say skip.

## Decisions that outlive the code

- **One call in, one outcome out.** A recorded call becomes a sealed
  `CallRecord`; a skipped call leaves no file and no row (scenario 2). There is
  no third, half-written state left lying around.
- **It owns no privilege and no policy.** Whether to record is
  `rules/RecordingPolicy`'s answer; the capture is the shell-side recorder
  reached through `ShizukuManager`. This class only sequences them.
- **"Asked to record but could not" is a FAILED row, not a silent gap.** If the
  privilege is missing when a recorded call ends, a `FAILED` record is still
  written, so the user sees the app tried and was blocked — never an
  unexplained hole where a call should be. This is THE HALF-RECORDING LAW facing
  its own failure honestly.
- **Peak amplitude decides BOTH_VOICES vs FAILED at seal time.** Amplitude can
  only prove sound arrived, so it can only ever DOWNGRADE to FAILED, never fake
  evidence; the owner's ear or the transcript refines it upward later.
- **Plaintext is sealed then wiped.** The WAV lands in the app cache, is copied
  into the encrypted vault, sealed, and the cache copy is deleted in the same
  breath — the unencrypted file never outlives the call.

## Connections

- Reads: `rules/RecordingPolicy`. Uses: `capture/shizuku/ShizukuManager`,
  `vault/Vault`, `data/CallRecordDao`.
- Driven by: `capture/CallWatcher`.
