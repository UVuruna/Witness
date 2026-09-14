# RuntimePermissions

The permissions the app must be GIVEN, as opposed to the ones it declares.

## Responsibility

Name every runtime permission the app needs, why each one, whether it is granted
here, and turn them into steps the setup screen can draw.

## Decisions that outlive the code

- **The manifest is an announcement, not an answer.** The previous round listed
  five dangerous permissions and contained no line of code that ever asked for
  one. That single omission produced three separate failures: the microphone
  stayed shut, the call-state listener threw, and the foreground service was
  killed for claiming the microphone type it had no right to. Nothing recorded on
  any phone, and nothing reported why.
- **A permission is an entry, with its sentence attached.** Adding one is a new
  enum constant plus two strings — never a new dialog somewhere (ONE KIND, ONE
  CLASS). The reason the user is shown lives next to the permission it explains,
  so the two cannot drift apart.
- **Required and optional are different facts.** Without the microphone nothing
  is recorded at all; without contacts the app works with numbers instead of
  names. The setup screen is allowed to let her past the second kind, and the
  service refuses to start only for the first.
- **Version applicability belongs to the entry.** `POST_NOTIFICATIONS` exists
  from Android 13 and asking for it below that is an error, so each entry carries
  the version it starts at rather than the screens branching on it.

## Connections

- Rendered by: `ui/setup/SetupScreen`, as the first steps of the numbered guide.
- Gates: `capture/CaptureService` (refuses to start without the required ones),
  `capture/CallWatcher`, `capture/SpeakerphoneCaptureSource`.
- Reported by: `ui/AppViewModel` as `AppState.missingPermissions`.
