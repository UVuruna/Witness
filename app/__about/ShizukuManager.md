# ShizukuManager

The one gatekeeper to the borrowed privilege.

## Responsibility

Own everything the app knows about Shizuku — is it installed, running, permitted,
and is the privileged recorder bound — and expose it as one `state` flow the UI
watches.

## Decisions that outlive the code

- **Nothing else touches the Shizuku SDK.** Every call into `rikka.shizuku.*`
  lives here. The rest of the app sees a four-value enum and a `recorder`
  handle, never a binder or a permission code (ONE KIND, ONE CLASS).
- **The four states map exactly to what the user must DO**: NOT_INSTALLED (send
  her to install), NOT_RUNNING (the reboot case — restart the helper), DENIED
  (grant permission), READY (capture works). The setup guide and the home
  screen's re-arm button are built directly on these.
- **Every SDK call is wrapped in `runCatching`.** Shizuku may be absent, dead,
  or mid-restart at any moment; a binder call that throws must degrade to
  "not ready", never crash the app that is only trying to check status.
- **Binder-received and binder-dead listeners keep the state honest** without
  polling: when the helper starts or dies the flow updates itself, so the UI
  reflects reality after a reboot without the user reopening a screen.

## Connections

- Talks to: `capture/shizuku/RecorderService` over `IRecorderService`.
- Read by: `ui/AppViewModel` (rearm), `capture/shizuku/ShizukuCaptureSource`
  (status + guide), `capture/RecordingCoordinator` (the recorder handle).
