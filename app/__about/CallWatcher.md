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
- **Registering is not free, and failing to register is not fatal.** On Android
  12 and later `registerTelephonyCallback` throws `SecurityException` unless
  `READ_PHONE_STATE` has actually been granted. The previous round called it
  unconditionally inside a service's `onCreate`, so the service died on the spot,
  on every phone. `start()` checks first and RETURNS the reason instead of
  throwing into a lifecycle callback nobody is catching.
- **Direction is no longer inferred here.** It used to be guessed from whether a
  number had been seen ringing — and since Android 12 never supplies one, every
  call was guessed OUTGOING. The direction now comes from the call log through
  `capture/CallIdentity`, which is the only place the platform still states it.
- **The broadcast is harvested for the number only.** `ACTION_PHONE_STATE` still
  carries the ringing number to an app that may read the call log, which is the
  one chance to apply the lists BEFORE recording rather than after. It never
  drives the call state — one source of truth for that, not two.

## Connections

- Drives: `capture/RecordingCoordinator`.
- Hosted by: `capture/CaptureService` (the foreground service that keeps it
  alive between calls).
