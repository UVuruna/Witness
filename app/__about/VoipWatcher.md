# VoipWatcher

Notices a conversation happening inside another app.

## Responsibility

Detect that the phone is carrying a conversation no telephony API reports, and
hand it to the coordinator — to be recorded on the loudspeaker if the user
enabled that, and otherwise to be written down as a gap she can see.

## Decisions that outlive the code

- **The silence was the danger, not the missing feature.** Android's call-state
  stream considers telephony calls only, so an app watching telephony alone
  believes nothing is happening while its user is being threatened. Abusers
  increasingly call over these apps because it is free and leaves no carrier
  record. A gap the user can see beats a gap she cannot.
- **The class knows nothing about apps, and that is the point.** The whole test
  is `MODE_IN_COMMUNICATION` while telephony is idle. There is no package list
  to fall behind: WhatsApp, Viber, Messenger, Signal, Telegram, Teams, Meet and
  whatever ships next are covered by the same line, and none of them is named.
  Naming one would need notification access — a switch that stands out to anyone
  inspecting the phone — and THE INSPECTION TEST is worth more than an app name.
- **Generic cuts both ways, so the floor exists.** A headset connecting or an
  assistant turn raises the same mode. `RecordingCoordinator.VOIP_FLOOR_MS`
  refuses to file anything shorter than a conversation, which is what keeps the
  evidence list free of calls that never happened.
- **"Is this a carrier call?" is asked of the app, not of the platform.** The
  obvious check, `TelephonyManager.getCallState()`, is served by Telecom — and
  on modern Android several of these apps register self-managed connections so
  their calls appear in the system call UI. Gating on it could therefore go
  permanently silent for exactly the apps this watcher exists for, with nothing
  to show that it had. `RecordingCoordinator.carrierCallInProgress` is set by
  the telephony watcher itself and cannot be confused that way.
- **The mode callback reports changes only.** API 31+ also reads the mode once at
  registration, or a conversation already running when the service starts — after
  a reboot, after a sticky restart, the moment permissions are granted — would
  never be noticed at all.

## Not measured

That these apps really raise this mode on a real handset is a platform
expectation this project has never observed, for any app. Until a real call
proves it, the feature is written down as unproven — see
[STATUS](../../docs/STATUS.md).

## Connections

- Drives: `capture/RecordingCoordinator` through `onVoipStarted` / `onCallEnded`.
- Hosted by: `capture/CaptureService`.
- Recorded by: `capture/SpeakerphoneCaptureSource` in its VOIP variant, the only
  route that can hear these calls at all.
