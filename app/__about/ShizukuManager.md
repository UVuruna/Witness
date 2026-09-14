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
  polling: while the process is alive, the flow updates itself when the helper
  starts or dies. It cannot help after a reboot, because nothing of this app is
  running then — that case is the boot notification and the re-arm button.
- **`isInstalled()` depends on the manifest's `<queries>` entry.** Android 11
  hides other packages unless they are named, and the state machine is built on
  a `getPackageInfo` call for Shizuku's package. Without that entry the call
  throws for a Shizuku that has not yet talked to this app — precisely the state
  after a reboot — and the app would tell her to install what she already has.

## Connections

- Talks to: `capture/shizuku/PrivilegedRecorder` over `IRecorderService`, in the
  process Shizuku spawns as the ADB shell.
- Read by: `ui/AppViewModel` (rearm), `capture/shizuku/ShizukuCaptureSource`
  (status + guide), `capture/RecordingCoordinator` (through the source's
  recorder handle).
