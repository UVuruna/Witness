# CallIdentity

Who was on the other end, and who called whom.

## Responsibility

Read the number, the contact name and the direction of a call back from the
platform, and say plainly when it cannot.

## Decisions that outlive the code

- **The call log, not the listener.** Android 12 removed the caller's number
  from `TelephonyCallback.CallStateListener`. The previous round kept the old
  code shape around the new API, so the number was null forever: every call was
  filed as OUTGOING, no contact ever matched, and no whitelist or blacklist rule
  could fire on any phone newer than Android 11. The lists worked perfectly and
  were never given anything to match.
- **Read with a retry.** The log row is written when the call ENDS, sometimes a
  moment after. One read would miss it often enough to matter, so this reads four
  times over about three seconds.
- **A missing answer stays missing.** No row and no permission produce null, and
  the record is filed with `Direction.UNKNOWN`. Inventing a direction is what
  produced a database full of confident nonsense last time.
- **Contact lookup fails closed.** Without `READ_CONTACTS`, or on any query
  error, the caller counts as not in contacts — so the unknown-caller rule
  applies rather than a stranger being silently treated as a contact.

## Connections

- Used by: `capture/RecordingCoordinator` at the end of every carrier call, and
  by `capture/CallWatcher` to decide whether the ringing number is readable.
- Feeds: `rules/RecordingPolicy`, which needs a number to apply the lists.
