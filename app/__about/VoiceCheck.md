# VoiceCheck

The one place that answers "is the other person in this file?".

## Responsibility

Turn a finished recording's measurement into a verdict, and read the guided test
call. Nothing else in the app may write a `Quality`.

## Decisions that outlive the code

- **Loudness is not evidence.** The previous round decided a recording held both
  voices when its peak amplitude exceeded 150 — which only ever says the file is
  not silent. One person shouting clears that bar comfortably, so one-sided
  recordings were sealed and offered as evidence. That rule is gone and may not
  come back in any form.
- **Two proofs, both measurements, nothing else.** A stereo stream whose two
  channels both carried speech AND differ from one another holds two people. A
  single-channel recording proves nothing by itself — but if the user was asked
  to stay silent and sound arrived anyway, the sound was the other person. Those
  are the only two ways to reach `BOTH_VOICES`.
- **The difference test is what stops the obvious lie.** Several devices hand
  back one microphone signal duplicated into both channels. Both channels are
  then "active", and a naive two-channel check would call that two people. The
  RMS of the channel difference separates the two cases and is the reason the
  stereo branch can be trusted at all.
- **The route's proof is per phone, not per call.** What the test call
  establishes is a fact about this handset and this route, so a later mono
  recording on the same route inherits it. That is a real measurement, not an
  assumption — but it is also why a phone that is never tested keeps producing
  `UNVERIFIED` rather than being quietly promoted.
- **TWO VOICES MEANS TWO PEOPLE, BY ANY ROUTE** (owner, 2026-09-14). This class
  never asks which audio path produced the file. A recording made through the
  microphone with the loudspeaker on satisfies the law exactly as a privileged
  tap does, and the code has no way to express a preference between them.

## Connections

- Reads: `capture/CaptureOutcome`, produced by `capture/ChannelMeter`.
- Writes: `data/Quality` on every `data/CallRecord`, through
  `capture/RecordingCoordinator`.
- Remembered by: `capture/RouteMemory`, which stores the per-phone verdict.
- Flow: [VoiceCheck flow](../__flow/VoiceCheck.md)
