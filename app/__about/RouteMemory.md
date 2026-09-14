# RouteMemory

What this phone has PROVEN about each way of recording, and which of them the
user switched on.

## Responsibility

Hold the verdict of the guided test call per route, readable without waiting,
and tell the app when it changes.

## Decisions that outlive the code

- **A measurement replaces a promise.** `CaptureSource` used to declare a
  compile-time `deliversBothVoices = true`, which is a claim about every handset
  on earth made by a constant. What a phone actually does is decided below the
  app by its audio driver, so the only honest answer comes from testing it here
  — and that answer belongs somewhere durable.
- **Readable synchronously, on purpose.** A screen asking "is this phone
  protected right now?" cannot await a preference read. The stored value is
  mirrored in memory and kept current from the same flow the rest of the app
  observes.
- **One preference key, not one per route.** THE CONFIG SECTION LAW asks that the
  key table be written once and whole; a key invented per route would grow the
  table from outside its own definition. The verdicts travel as one flat string.
- **The user's switches live here too.** Whether the loudspeaker route is on is
  the same kind of fact as whether it works — both decide what the registry may
  offer — and both are needed in the same synchronous breath.

## Connections

- Stores through: `data/Prefs`.
- Written by: `capture/RecordingCoordinator`, when a test call finishes.
- Read by: `capture/shizuku/ShizukuCaptureSource` and
  `capture/SpeakerphoneCaptureSource` for their status, and by
  `capture/VoiceCheck` through the coordinator when judging a mono recording.
