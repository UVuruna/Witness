# AppViewModel

The single state holder behind every screen.

## Responsibility

Combine the vault index, the two number lists, the user's defaults and what this
phone is capable of into one `AppState`, and expose the few actions a screen may
take.

## Decisions that outlive the code

- **It holds no rules of its own.** Whether a call is recorded is decided by
  `rules/RecordingPolicy`; what the phone can do is decided by
  `capture/CaptureRegistry`. This class only shapes those answers for drawing.
  The moment a decision starts living here, two places decide the same thing.
- **One state object, not five flows per screen.** Screens receive `AppState` and
  callbacks, which keeps them free of Android types and readable top to bottom.
- **Liveness is part of the combine, not computed inside it.** The previous round
  derived the capture status inside a `combine` of four database flows, so the
  home screen could only change its mind when a record or a rule changed.
  Granting a permission, arming Shizuku or finishing the test call left it saying
  the opposite of the truth until something unrelated happened. The route memory,
  the Shizuku state and the service's own state are sources now.
- **`refresh()` covers what Android never streams.** A permission result and a
  system settings change arrive as nothing at all, so every return to the app
  re-asks — and takes the opportunity to make sure the always-on ear is running,
  which is the only thing that starts it on an ordinary launch.
- **Deleting a record deletes its bytes first, then the row.** A row without a
  file is a confusing gap; a file without a row is invisible evidence left on the
  device. The order is deliberate.
- **`closeOpenCopies()` exists so the UI can wipe decrypted copies** the instant a
  detail screen goes away, rather than trusting the cache to be cleaned later.

## Connections

- Reads: `data/Db` (records and rules), `data/Prefs`, `capture/CaptureRegistry`.
- Used by: `ui/AppNav`, which hands the state down to every screen.
