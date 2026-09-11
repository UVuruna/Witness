# CallWatcher

The eyes: notices a call start and end, and nothing else.

## Responsibility

Tell the coordinator when a call goes off-hook and when it returns to idle, and
carry the caller's number so the lists can be applied.

## Decisions that outlive the code

- **Eyes, not hands.** It never records; it only says "a call began / ended".
  Keeping detection apart from capture means the recording logic has one entry
  point and can be reasoned about without telephony noise.
- **One watcher, two backends.** `TelephonyCallback` on Android 12+ and the
  deprecated `PhoneStateListener` below it do the same job; the seam is hidden
  here so nothing else in the app branches on OS version (ONE KIND, ONE CLASS).
- **Direction is inferred from whether a number was seen ringing.** A call that
  went off-hook after a RINGING with a number is incoming; one that went
  off-hook cold is outgoing. Good enough to label the record, and it needs no
  extra permission.
- **Contact lookup fails closed.** Without `READ_CONTACTS`, or on any query
  error, the caller counts as "not in contacts" — so the unknown-caller rule
  applies rather than the app silently treating a stranger as a contact.

## Connections

- Drives: `capture/RecordingCoordinator`.
- Hosted by: `capture/CaptureService` (the foreground service that keeps it
  alive between calls).
