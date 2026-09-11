# CaptureSource

The boundary between "a conversation happened" and "the vault has it".

## Responsibility

Describe one way of obtaining a recording: whether this phone can use it, what
the user must do once to enable it, and whether it delivers both voices.

## Decisions that outlive the code

- **Every mechanism is an entry in `CaptureRegistry`, never a second code path.**
  Whatever the platform ends up allowing plugs in here (ONE KIND, ONE CLASS). The
  screens ask the registry what is possible; they never learn which mechanism
  answered.
- **`register()` REFUSES a source that cannot deliver both voices.** This is THE
  HALF-RECORDING LAW (CLAUDE.md) written as code rather than as a good intention:
  a half source cannot even be added to the list, so it cannot reach the UI
  through carelessness later.
- **The registry ships EMPTY.** An entry here is a promise to a person in danger,
  and no promise is made before a mechanism is proven on real phones. An empty
  registry is what makes the setup screen say so honestly instead of walking her
  through steps that will not work.
- **Instructions are data, not prose buried in a layout.** `Guide`/`Step` carry
  the numbered steps and, where possible, the `Intent` that opens the exact
  Settings screen in question — because ONLY STEPS AN ORDINARY USER CAN DO
  (CLAUDE.md) means she must never have to hunt for anything.

## Connections

- Used by: `ui/setup/SetupScreen` (draws the guide), `ui/AppViewModel` (reports
  READY / NEEDS_SETUP to the home screen).
- Feeds: `vault/Vault` with finished recordings, and `data/CallRecord.capturedBy`
  with the id of whichever source produced them.
