# app/ — the Android app

Two things live here: **the product** (`com.pebblesoft.toolbox`, the app a user
installs) and **the M0/M0.5 feasibility probe** (`com.uvuruna.callprobe`), which
is throwaway and no longer wired into the manifest.

Navigation: [README](../README.md) · [ARCHITECTURE](../docs/ARCHITECTURE.md) ·
[PLAN](../docs/PLAN.md)

---

## The product

A neutral-looking app that keeps recordings of calls as sealed, encrypted
evidence, decides what to keep from the user's own lists, and asks the user for
setup exactly once.

Two project laws shape almost every file here:

- **A RECORDING WITHOUT BOTH VOICES IS NOT A RESULT**, clarified 2026-09-14 as
  TWO VOICES MEANS TWO PEOPLE, BY ANY ROUTE — `capture.VoiceCheck` is the only
  thing allowed to decide whether a file holds two people, it decides from a
  measurement rather than from loudness, and `data.Quality` rides along with
  every recording so no screen can quietly show a half file as evidence.
- **ONE SETUP, GUIDED, THEN NOTHING** — every setup instruction, permission
  requests included, is a numbered `capture.Step` with, wherever possible, the
  intent that opens the exact screen it talks about.

### Files

| File | Role | Tier |
|------|------|------|
| `MainActivity.kt` | The single host. Sets `FLAG_SECURE` so the app never shows in recents or a screenshot. | Trivial |
| `ToolboxApp.kt` | Builds the app's parts once — database, vault, preferences, policy. | Trivial |
| `data/CallRecord.kt` | One recorded call, and what it is worth as evidence. | [Standard](__about/CallRecord.md) |
| `data/NumberRule.kt` | One number the user decided about; the whitelist and blacklist are one table. | Trivial |
| `data/Dao.kt` | Room queries for records and rules. | Trivial |
| `data/Db.kt` | The vault's index. | Trivial |
| `data/Prefs.kt` | The user's settings, declared whole in one place. | Trivial |
| `rules/RecordingPolicy.kt` | THE decision: is this call recorded, and why. | [Standard](__about/RecordingPolicy.md) |
| `vault/Vault.kt` | Encrypted storage, the seal, and the only door to the bytes. | [Standard](__about/Vault.md) |
| `permissions/RuntimePermissions.kt` | What the app must be GIVEN, and the sentence that explains each. | [Standard](__about/RuntimePermissions.md) |
| `capture/CaptureSource.kt` | The boundary every recording mechanism plugs into. | [Standard](__about/CaptureSource.md) |
| `capture/RecordingCoordinator.kt` | Decide → record → judge → seal → file; one call in, one row out. | [Standard](__about/RecordingCoordinator.md) |
| `capture/CallWatcher.kt` | The eyes: a carrier call starts and ends. | [Standard](__about/CallWatcher.md) |
| `capture/VoipWatcher.kt` | The other eyes: a conversation inside any other app, seen in the audio mode. | [Standard](__about/VoipWatcher.md) |
| `capture/CallIdentity.kt` | Who was on the other end, and who called whom. | [Standard](__about/CallIdentity.md) |
| `capture/PcmRecorder.kt` | The one recording engine, stereo first, source ladder. | [Standard](__about/PcmRecorder.md) |
| `capture/ChannelMeter.kt` | Measures the stream as it is written — per channel and over time. | [Standard](__about/ChannelMeter.md) |
| `capture/VoiceCheck.kt` | THE verdict: is the other person in this file. | [Algorithmic](__about/VoiceCheck.md) |
| `capture/RouteMemory.kt` | What this phone PROVED about each route, and what she switched on. | [Standard](__about/RouteMemory.md) |
| `capture/MicRecorder.kt` | Speaker on, microphone recording — the route that always works. | [Standard](__about/MicRecorder.md) |
| `capture/SpeakerphoneCaptureSource.kt` | That route described, in two variants: calls and other apps. | [Standard](__about/SpeakerphoneCaptureSource.md) |
| `capture/CaptureOutcome.kt` | What one recording contained, and its wire form across AIDL. | Trivial |
| `capture/WavWriter.kt` | 16-bit PCM into a WAV container, header patched on close. | Trivial |
| `capture/CaptureService.kt` | Foreground service (`microphone`) that keeps both watchers alive. | [Standard](__about/CaptureService.md) |
| `capture/BootReceiver.kt` | Tries to restart the ear after a reboot, and puts a notice in front of her when Android forbids it. | Trivial |
| `capture/shizuku/IRecorderService.aidl` | The AIDL contract to the privileged process. | Trivial |
| `capture/shizuku/PrivilegedRecorder.kt` | The shell-side host of the engine — the one process that hears the call. | [Standard](__about/PrivilegedRecorder.md) |
| `capture/shizuku/ShizukuManager.kt` | The one gatekeeper to the borrowed privilege. | [Standard](__about/ShizukuManager.md) |
| `capture/shizuku/ShizukuCaptureSource.kt` | The quiet mechanism + its numbered setup guide. | Trivial |
| `ui/AppNav.kt` | The shell: four tabs and the setup flow. | [Standard](__about/AppNav.md) |
| `ui/AppViewModel.kt` | The single state holder behind every screen. | [Standard](__about/AppViewModel.md) |
| `ui/theme/Theme.kt` | The calm palette and the slightly larger body type. | Trivial |
| `ui/components/Pieces.kt` | Shared card, section, empty state and pill. | Trivial |
| `ui/home/HomeScreen.kt` | One glance: is the phone protecting me right now. | Trivial |
| `ui/recordings/RecordingsScreen.kt` | The data section — grouped by person, then time, with search. | Trivial |
| `ui/recordings/RecordDetailScreen.kt` | One recording: worth, player, seal, transcript, share, delete. | [Standard](__about/RecordDetailScreen.md) |
| `ui/numbers/NumbersScreen.kt` | The two lists and the two defaults. | Trivial |
| `ui/settings/SettingsScreen.kt` | Lock, how the app looks, storage, version. | Trivial |
| `ui/setup/SetupScreen.kt` | The numbered instructions, permissions included, and the honest empty state. | Trivial |
| `ui/setup/TestCallScreen.kt` | Twenty seconds that replace a promise with a measurement. | Trivial |

User-facing copy lives in `res/values/strings.xml` (English) and
`res/values-sr/strings.xml` (Serbian) — never hard-coded in a composable.

### The capture mechanisms — quiet first, loudspeaker always (2026-09-14)

`CaptureRegistry` holds three entries, in preference order:

1. `ShizukuCaptureSource` — the call's own audio through borrowed ADB-shell
   privilege. Silent, which for someone living with the caller is the feature.
   Whether it carries both people is decided by the manufacturer's audio driver,
   so it is measured rather than promised.
2. `SpeakerphoneCaptureSource` (CARRIER) — speaker on, microphone recording the
   room. Designed as the fallback that works on every handset; **measured
   2026-09-24 on Android 16: the audio policy silences the app's microphone for
   the whole call**, so it records digital silence ([STATUS](../docs/STATUS.md)).
3. `SpeakerphoneCaptureSource` (VOIP) — the same mechanism for calls inside any
   other app, where there is no modem audio to tap at all.

The chain is `CallWatcher` / `VoipWatcher` (notice the conversation) →
`RecordingCoordinator` (choose a route, apply the lists) → `PcmRecorder` in one
of its two hosts → `ChannelMeter` (measure while writing) → `VoiceCheck` (judge)
→ `Vault` (sealed). The user pairs Shizuku once, guided by the setup screen; no
computer, no root.

**The twenty-second test call** is what turns EVERY PHONE, OR IT DOES NOT COUNT
from a promise into a fact: she talks for five seconds, then stays silent for ten
while the other side keeps talking, and sound arriving during her silence can
only be the far party. The verdict is stored per route in `RouteMemory`, and
until it exists the home screen does not say the phone is covered.

**Nothing in this chain has been measured on a real handset yet** — not the
privileged route, not the loudspeaker route, not the VoIP detection, and not the
re-arm after a reboot. [STATUS](../docs/STATUS.md) is the running record.

---

## The M0/M0.5 probe (throwaway, `com.uvuruna.callprobe`)

The feasibility harness from [PLAN](../docs/PLAN.md). It measured what each audio
source delivers during a call, and what an hours-long microphone service costs in
battery. It is out of the manifest AND out of the build: `app/build.gradle.kts`
excludes `com/uvuruna/**` from the Kotlin compile tasks, so none of it reaches
the APK. It stays on disk only until the owner says it may be deleted.

**What it measured** on one Samsung device (2026-09-02): the ADB shell identity
opens the privileged sources, the microphone pipe writes real audio, and the
phone's own call recording is disabled by its regional firmware. What it never
measured: a live call.

That path is now the product's own: the owner chose Shizuku on 2026-09-11, and
ONE SETUP, GUIDED, THEN NOTHING names its wireless-debugging pairing as the
approved case. What stays forbidden is root, a firmware change, and anything
needing a PC. The probe stays on disk only until the owner says it may be
deleted.

| File | Role |
|------|------|
| `src/main/java/com/uvuruna/callprobe/` | The probe app: one screen, a recorder service, a listen service, playback. |
| [`probe-tools/`](probe-tools/___probe-tools.md) | Shell-identity call-audio probe, run over ADB. |

## Privacy note

Even the throwaway obeyed the project laws: recordings stayed in app-private
storage, nothing left the device. The product tightens that further — everything
is encrypted at rest and nothing is exposed beyond a temporary shared copy the
user explicitly asks for.
