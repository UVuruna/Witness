# CallRecord

One recorded call, as the vault indexes it.

## Responsibility

Be the row that points at a piece of evidence: who, when, how long, which file,
what hash — and, the field that matters most, what the recording is worth.

## Decisions that outlive the code

- **`Quality` is a first-class field, not a detail.** THE HALF-RECORDING LAW
  (CLAUDE.md) says a file holding one side of a conversation is not a lesser
  product, it is not the product. So every row carries `BOTH_VOICES`,
  `UNVERIFIED` or `FAILED`, and every screen that shows a recording shows this
  beside it. Nobody may carry a `FAILED` file to a lawyer believing it is proof.
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
