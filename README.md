# Safety

Android app that protects victims of violence: it records their phone calls, turns them into speaker-labeled transcripts, and locks everything in an encrypted, tamper-evident vault — with a voice-triggered SOS that silently alerts trusted contacts the moment a rehearsed danger phrase is spoken.

*Why "Safety"* — working title, final name pending the owner's pick. The app is installed for exactly one reason: so that the person holding the phone is safer with it than without it — every feature answers to that sentence.

## The mission

A victim of domestic violence or abuse needs three things their phone can give
them: **proof** of what was said to them, **protection** of that proof from the
abuser who may inspect or destroy the phone, and a **lifeline** for the moment
things turn dangerous. Safety is those three things in one app, built strictly
on what Google Play officially allows — no root, no tricks, nothing that a
policy change can take away from the people who depend on it.

## What it does

- **Records calls** automatically (all numbers, or filtered by the user's own
  record/skip lists) as a single audio file per call.
- **Transcribes on the device** — no internet needed — and labels the timeline
  by speaker: `[mm:ss] WHO: what was said`.
- **Guards the evidence**: encrypted storage, hash + timestamp per recording so
  a file can prove it was never altered, optional off-phone backup so the
  evidence survives a destroyed or confiscated phone.
- **Hides in plain sight**: neutral app name and icon, PIN/biometric lock,
  nothing visible in the gallery or file manager.
- **Answers a danger phrase**: the user rehearses a phrase of their own
  choosing; when the app hears it, it silently sends their location to one or
  more trusted contacts — fully automatic, because by the time the phrase is
  spoken the user may no longer have their hands free.

The full catalogue lives in [FEATURES](docs/FEATURES.md); the milestones,
scenario matrix and Play-policy risk register in [PLAN](docs/PLAN.md); the
module map and technology decisions in [ARCHITECTURE](docs/ARCHITECTURE.md).

## Status

Founded 2026-09-01. The owner has picked the final name — **Witness** — and
the pending folder rename is fully specified in [RENAME](RENAME.md) (this
session could not run it while live inside the folder). Milestone M0 — a throwaway feasibility probe of
microphone capture during a speakerphone call on real devices — is the next
step; no product code exists yet. Distribution target: Google Play
(official-APIs-only is a project law, see [CLAUDE.md](CLAUDE.md)).

## Project layout

```
📁 Applications/Safety/
  📝 README.md          ← you are here
  📝 CLAUDE.md          ← project rules: stack, laws, how to run/test
  📁 docs/              ← PLAN, FEATURES, ARCHITECTURE
  📁 app/               ← M0 call-audio probe (throwaway) — see app/___app.md
  📁 assets/            ← logo
  📁 tests/             ← guard tests (structure, config, docs, links)
  📁 UV/                ← owner's inbox (untracked)
```

The M0 feasibility probe — a throwaway app that measures what the microphone
actually captures during a call — is documented in [app/___app.md](app/___app.md).

Guard tests are documented in [tests](tests/___tests.md).
