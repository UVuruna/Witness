# CaptureSource

The boundary between "a conversation happened" and "the vault has it".

## Responsibility

Describe one way of obtaining a recording: whether this phone can use it, what
the user must do once to enable it, what the phone PROVED about it, and — since
a source both describes a way of recording and performs it — hand over the
recorder that does the work.

## Decisions that outlive the code

- **Every mechanism is an entry in `CaptureRegistry`, never a second code path.**
  Whatever the platform ends up allowing plugs in here (ONE KIND, ONE CLASS). The
  screens ask the registry what is possible; they never learn which mechanism
  answered.
- **The law is kept by measurement, not by a boolean.** `register()` used to
  refuse any source whose `deliversBothVoices` was false — which sounds like THE
  HALF-RECORDING LAW written as code, but a compile-time constant cannot know
  what a manufacturer's audio driver does during a live call. The one source that
  declared `true` was never measured, and the gate passed it. `tested()` now
  returns what the guided test call found ON THIS PHONE, and `status()` refuses
  to say READY without it.
- **`PROVEN_HALF` is a status, not a rejection.** A route measured to carry the
  user alone keeps recording, and what it records is labelled for what it is —
  but it ranks below every other route and nothing in the app may present it as
  protection. Removing it outright would leave a phone with nothing while a
  working route sits one switch away.
- **Status order is the fallback order.** `usable()` sorts by status rather than
  registration order, so a proven route always outranks an untried one, and both
  outrank one already caught delivering half a conversation.
- **Instructions are data, not prose buried in a layout.** `Guide`/`Step` carry
  the numbered steps and, where possible, the `Intent` that opens the exact
  Settings screen in question — because ONLY STEPS AN ORDINARY USER CAN DO
  (CLAUDE.md) means she must never have to hunt for anything.

## Connections

- Used by: `ui/setup/SetupScreen` (draws the guide), `ui/AppViewModel` (reports
  READY / NEEDS_SETUP to the home screen).
- Feeds: `vault/Vault` with finished recordings, and `data/CallRecord.capturedBy`
  with the id of whichever source produced them.
