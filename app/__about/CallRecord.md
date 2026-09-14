# CallRecord

One recorded call, as the vault indexes it.

## Responsibility

Be the row that points at a piece of evidence: who, when, how long, which file,
what hash — and, the field that matters most, what the recording is worth.

## Decisions that outlive the code

- **`Quality` is a first-class field, not a detail.** THE HALF-RECORDING LAW
  (CLAUDE.md) says a file holding one side of a conversation is not a lesser
  product, it is not the product. So every row carries its verdict and every
  screen that shows a recording shows this beside it. Nobody may carry a file to
  a lawyer believing it is proof when it is not.
- **Every `Quality` is a measurement.** `capture/VoiceCheck` is the only thing
  allowed to write one, and it decides from two channels that differ or from the
  silent window of the guided test call. `ONE_VOICE` means the far party was
  measured absent; `UNVERIFIED` means nothing proved it either way;
  `NOT_CAPTURED` means the app saw the conversation and no route could record it,
  which is a gap the user can act on rather than one she cannot see.
- **`Direction.UNKNOWN` exists so the app can admit it does not know.** Android
  12 stopped supplying the number to call-state listeners and the call log is not
  always readable. The previous round had no way to say "unknown" and labelled
  every call OUTGOING instead.
- **The seal lives on the row, the bytes live in the vault.** `sha256` and
  `sealedAt` are what turn audio into evidence; keeping them beside the index
  means the check is one comparison away on any screen.
- **`capturedBy` records which mechanism produced the file**, so a systematic
  failure can be traced to its path instead of guessed at.
- **`contactName` is resolved AT RECORDING TIME and stored.** Contacts get
  renamed and deleted — sometimes by an abuser holding the phone — and the
  evidence must still say who the call was with on the day it happened.

## Connections

- Written by: the capture pipeline, through `data/CallRecordDao`.
- Read by: `ui/recordings/RecordingsScreen` (grouped by person, then time) and
  the home screen's latest list.
- Points at: files in `vault/Vault`.
