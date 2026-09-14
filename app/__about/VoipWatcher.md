# VoipWatcher

Notices a conversation happening inside another app.

## Responsibility

Detect that a call is in progress in WhatsApp, Viber, Messenger or their kind,
and hand it to the coordinator — to be recorded on the loudspeaker if the user
enabled that, and otherwise to be written down as a gap she can see.

## Decisions that outlive the code

- **The silence was the danger, not the missing feature.** Android's call-state
  stream considers telephony calls only, so an app watching telephony alone
  believes nothing is happening while its user is being threatened. Abusers
  increasingly call over these apps because it is free and leaves no carrier
  record. A gap the user can see beats a gap she cannot.
- **The audio mode is the signal.** Every one of these apps puts the phone into
  `MODE_IN_COMMUNICATION`. It costs no permission, behaves the same on every
  handset, and needs no list of package names to keep up to date.
- **Which app it was is deliberately not asked.** Naming the app needs
  notification access — a switch that stands out to anyone inspecting the phone.
  THE INSPECTION TEST is worth more than an app name.
- **Telephony is consulted first.** Cellular calls move the audio mode too, so
  without that check an ordinary call would be filed twice and a recorder started
  twice. Without permission to ask, the watcher assumes telephony is busy: a
  missed row is a smaller harm than a double recording.

## Connections

- Drives: `capture/RecordingCoordinator` through `onVoipStarted` / `onCallEnded`.
- Hosted by: `capture/CaptureService`.
- Recorded by: `capture/SpeakerphoneCaptureSource` in its VOIP variant, the only
  route that can hear these calls at all.
