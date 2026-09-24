# VoiceCheck — flow

How a pile of samples becomes a verdict, and what each branch means to the user.

## Judging a finished recording

```
  CaptureOutcome (measured while the bytes were written)
        │
        ├── error set, or no frames at all ─────────────────► FAILED
        │
        ├── every channel below the silence peak (150) ─────► FAILED
        │
        ├── two channels?
        │      │
        │      ├── yes ── both carried speech for ≥ 2% of the call
        │      │          AND difference RMS > 200 ──────────► BOTH_VOICES
        │      │          otherwise ─────────────────────────► ONE_VOICE
        │      │
        │      └── no ─── this route proved BOTH_PEOPLE here ► BOTH_VOICES
        │                 otherwise ─────────────────────────► UNVERIFIED
```

The difference test is the load-bearing one. Without it, a device that copies a
single microphone signal into both channels reads as two active channels, and a
one-sided recording would be stamped as evidence — the exact failure the
previous round shipped, arrived at by a different road.

## Reading the guided test call

The user is asked to talk for five seconds and then stay silent for ten while
the other side keeps talking. Timeline slots are a quarter second each, so her
silence is slots 20 to 60.

```
  talk           stay quiet, let them talk
  ├────────────┤ ├──────────────────────────────┤
  slot 0      20                              60
                 │
                 └── any sound here can only be the far party
```

```
  test outcome
        │
        ├── error, no frames, or silence throughout ────────► NOTHING
        │
        ├── two channels that already prove two people ─────► BOTH_PEOPLE
        │
        ├── ≥ 15% of the silent window carried speech ──────► BOTH_PEOPLE
        │
        └── otherwise ─────────────────────────────────────► ONE_PERSON_ONLY
```

`NOTHING` and `ONE_PERSON_ONLY` are not dead ends: the test screen then offers
the Wi-Fi calling switch, which is the single most common reason a call records
as silence, and points at the loudspeaker route — which, measured on 2026-09-24,
Android silences for the whole call ([STATUS](../../docs/STATUS.md)).

## Why the thresholds are where they are

| Threshold | Value | Why |
|---|---|---|
| silence peak | 150 | below this a 16-bit stream is line noise, not a room |
| speech floor | 600 | above this a frame is a voice rather than ambience |
| min active share | 2% | a channel that never carries speech is not a person |
| min difference RMS | 200 | below this the two channels are one signal twice |
| quiet-window share | 15% | the far side pauses too; a strict rule would fail honest calls |

Every one of these is a measurement threshold, not a policy. Changing one
changes what the app is willing to call evidence, so each change belongs in the
same commit as the recording that justified it.
