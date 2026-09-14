# CaptureService

The always-on ear that must stay alive between calls.

## Responsibility

Keep the watchers running as a foreground service, and refuse — visibly — when
it is not allowed to.

## Decisions that outlive the code

- **The microphone type is a claim the system audits.** From Android 14 a
  service declaring `FOREGROUND_SERVICE_TYPE_MICROPHONE` without `RECORD_AUDIO`
  actually granted is killed with a `SecurityException` at `startForeground`. The
  previous round declared it unconditionally in `onCreate`, so the ear died on
  every phone within milliseconds of being asked to listen. The permission is
  checked before the claim is made, and the failure is surfaced rather than
  swallowed.
- **A dead ear says so.** `problem` and `running` carry the reason to the home
  screen. A service that quietly is not there is indistinguishable, to the user,
  from one that is — and she is relying on it.
- **It borrows the app's parts, it does not build its own.** The previous round
  constructed a second `ShizukuManager` here, so the app and the service each
  held a different view of the same privilege and the second one's listeners were
  never removed. The gatekeeper and the conductor live in `ToolboxApp`.
- **Both watchers, one service.** Telephony and the audio mode are two different
  signals for the same question — is a conversation happening — so they share one
  host and one lifetime.

## Connections

- Hosts: `capture/CallWatcher`, `capture/VoipWatcher`.
- Uses: `ToolboxApp.coordinator`, `ToolboxApp.routes`.
- Started by: `capture/BootReceiver` after a restart and `ui/AppViewModel` on
  every return to the app.
