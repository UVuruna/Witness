# Safety

Android app that protects victims of violence: it records their phone calls, turns them into speaker-labeled transcripts, and locks everything in an encrypted, tamper-evident vault — with a voice-triggered SOS that silently alerts trusted contacts the moment a rehearsed danger phrase is spoken.

*Why "Safety"* — working title, final name pending the owner's pick. The app is installed for exactly one reason: so that the person holding the phone is safer with it than without it — every feature answers to that sentence.

## The mission

A victim of domestic violence or abuse needs three things their phone can give
them: **proof** of what was said to them, **protection** of that proof from the
abuser who may inspect or destroy the phone, and a **lifeline** for the moment
things turn dangerous. Safety is those three things in one app — no root, no
firmware change, no computer, and nothing the user has to remember to do while
the phone is ringing.

## What it does

- **Records calls** automatically (all numbers, or filtered by the user's own
  record/skip lists) as a single audio file per call, quietly, from the call's
  own audio via Shizuku — whether both people reach the file on a given phone is
  MEASURED with a twenty-second test call rather than promised. (The loudspeaker
  route beside it records silence: Android mutes an app's microphone during a
  call — measured 2026-09-24, see [STATUS](docs/STATUS.md).)
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
scenario matrix and risk register in [PLAN](docs/PLAN.md); the module map and
technology decisions in [ARCHITECTURE](docs/ARCHITECTURE.md); where the work
actually stands, right now, in [STATUS](docs/STATUS.md).

## Status

Founded 2026-09-01. The owner has picked the final name — **Witness** — and the
pending folder rename is fully specified in [RENAME](RENAME.md) (this session
could not run it while live inside the folder).

The product exists and builds. The capture chain records, measures what it
recorded, and labels it honestly; the number lists, the encrypted vault and its
seal, the single recording screen (player, seal check, share, delete) and the
whole interface in English and Serbian are in place. **Not yet built:** the PIN
and fingerprint gate, the disguised name and icon, the speaker-labeled
transcript, the SOS and the off-phone backup.

Measured on an Android 16 emulator on 2026-09-24, **the app does not record a
call yet**: the quiet route is refused by the audio server (an identity bug with
a proven fix), the loudspeaker route is silenced by Android for the whole call
(policy, not a bug), and the call log is never read, so no call gets a
direction. What a real handset still has to answer is listed in
[STATUS](docs/STATUS.md), the running record of what is done, what is left and
what is still unproven. How a user will be guided through setup is specified in
[SETUP GUIDE](docs/SETUP_GUIDE.md).

Distribution is a signed APK from GitHub Releases — the capture mechanism cannot
pass Play review, and the owner chose the mechanism (see [CLAUDE.md](CLAUDE.md)).

## Project layout

```
📁 Applications/Safety/
  📝 README.md          ← you are here
  📝 CLAUDE.md          ← project rules: stack, laws, how to run/test
  📁 docs/              ← PLAN, FEATURES, ARCHITECTURE
  📁 app/               ← the Android app — see app/___app.md
  📁 assets/            ← logo
  📁 tests/             ← guard tests (structure, config, docs, links)
  📁 UV/                ← owner's inbox (untracked)
```

The app — its modules, the two capture routes and the throwaway M0 probe that
still sits beside them — is documented in [app/___app.md](app/___app.md).

Guard tests are documented in [tests](tests/___tests.md).
