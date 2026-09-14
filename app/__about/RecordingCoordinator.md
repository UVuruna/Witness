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
  `rules/RecordingPolicy`'s answer; which route records is
  `capture/CaptureRegistry`'s; what the file is worth is `capture/VoiceCheck`'s.
  This class only sequences them.
- **The lists are applied twice, and the second time is the real one.** Android
  12 stopped handing the caller's number to the call-state listener, so a
  decision taken at OFFHOOK is taken blind. Recording starts anyway and the lists
  are applied again at the end against the number read back from the call log. A
  number on the never-record list leaves nothing behind — file deleted, no row —
  but a call whose number only arrives late is no longer filed as an outgoing
  call to nobody.
- **"Asked to record but could not" is a row, not a silent gap.** If no route was
  ready when a call ended, the record is still written — `NOT_CAPTURED` when
  nothing could even try, `FAILED` when a route tried and produced nothing. The
  user sees the app was blocked, never an unexplained hole where a call should
  be.
- **The quality is never guessed.** `capture/VoiceCheck` decides from what was
  measured while the bytes were written. The previous round stamped
  `BOTH_VOICES` on anything louder than silence, which one person shouting
  satisfies; that rule is gone and may not return in any form.
- **One session at a time, under a lock.** Start and end arrive on different
  coroutines from a platform callback. The previous shape raced on a short call
  and could leave a recorder running with no row to show for it.
- **Plaintext is sealed then wiped.** The WAV lands in the app cache, is copied
  into the encrypted vault, sealed, and the cache copy is deleted in the same
  breath — the unencrypted file never outlives the call.

## The guided test call

The same path serves the setup measurement: the next call is recorded, read by
`VoiceCheck.readTestCall` against the silent window the screen asked her to
leave, the verdict is stored in `capture/RouteMemory`, and the audio is deleted.
Nothing reaches the vault and no row is written — a test is not evidence and
must not look like it.

## Connections

- Reads: `rules/RecordingPolicy`, `capture/CallIdentity`, `capture/RouteMemory`.
- Uses: `capture/CaptureRegistry` to choose a route, `vault/Vault`,
  `data/CallRecordDao`.
- Driven by: `capture/CallWatcher` for carrier calls and `capture/VoipWatcher`
  for calls inside other apps.
