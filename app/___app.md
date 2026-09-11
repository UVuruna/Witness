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

- **A RECORDING WITHOUT BOTH VOICES IS NOT A RESULT** — `capture.CaptureRegistry`
  physically refuses to register a source that cannot deliver both voices, and
  `data.Quality` rides along with every recording so no screen can quietly show
  a half file as evidence.
- **ONLY STEPS AN ORDINARY USER CAN DO** — every setup instruction is a numbered
  `capture.Step` with, wherever possible, the intent that opens the exact screen
  it talks about.

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
| `capture/CaptureSource.kt` | The boundary every recording mechanism plugs into. | [Standard](__about/CaptureSource.md) |
| `capture/RecordingCoordinator.kt` | Decide → record → seal → file; one call in, one row out. | [Standard](__about/RecordingCoordinator.md) |
| `capture/CallWatcher.kt` | The eyes: call start/end, and the caller's number. | [Standard](__about/CallWatcher.md) |
| `capture/CaptureService.kt` | Foreground service (`microphone`) that keeps the watcher alive. | Trivial |
| `capture/BootReceiver.kt` | Restarts the watcher after a reboot. | Trivial |
| `capture/shizuku/IRecorderService.aidl` | The AIDL contract to the privileged process. | Trivial |
| `capture/shizuku/PrivilegedRecorder.kt` | The shell-side recorder — the one process that hears the call. | [Standard](__about/PrivilegedRecorder.md) |
| `capture/shizuku/ShizukuManager.kt` | The one gatekeeper to the borrowed privilege. | [Standard](__about/ShizukuManager.md) |
| `capture/shizuku/ShizukuCaptureSource.kt` | The Shizuku mechanism + its numbered setup guide. | Trivial |
| `ui/AppNav.kt` | The shell: four tabs and the setup flow. | [Standard](__about/AppNav.md) |
| `ui/AppViewModel.kt` | The single state holder behind every screen. | [Standard](__about/AppViewModel.md) |
| `ui/theme/Theme.kt` | The calm palette and the slightly larger body type. | Trivial |
| `ui/components/Pieces.kt` | Shared card, section, empty state and pill. | Trivial |
| `ui/home/HomeScreen.kt` | One glance: is the phone protecting me right now. | Trivial |
| `ui/recordings/RecordingsScreen.kt` | The data section — grouped by person, then time, with search. | Trivial |
| `ui/numbers/NumbersScreen.kt` | The two lists and the two defaults. | Trivial |
| `ui/settings/SettingsScreen.kt` | Lock, how the app looks, storage, version. | Trivial |
| `ui/setup/SetupScreen.kt` | The numbered instructions, and the honest empty state. | Trivial |

User-facing copy lives in `res/values/strings.xml` (English) and
`res/values-sr/strings.xml` (Serbian) — never hard-coded in a composable.

### The capture mechanism — Shizuku (owner's word, 2026-09-11)

`CaptureRegistry` now holds one entry: `ShizukuCaptureSource`. The chain is
`CallWatcher` (notices the call) → `RecordingCoordinator` (applies the lists) →
`ShizukuManager` → `PrivilegedRecorder` (records `VOICE_CALL` as the ADB shell) →
`Vault` (sealed). The user pairs Shizuku once, guided by the setup screen; no
computer, no root. Two things stay unproven until measured on a real phone:
that `VOICE_CALL` as shell carries BOTH voices during a live call, and that the
app re-arms itself cleanly after a reboot.

---

## The M0/M0.5 probe (throwaway, `com.uvuruna.callprobe`)

The feasibility harness from [PLAN](../docs/PLAN.md). It measured what each audio
source delivers during a call, and what an hours-long microphone service costs in
battery. It is no longer in the manifest and ships to nobody.

**What it measured** on one Samsung device (2026-09-02): the ADB shell identity
opens the privileged sources, the microphone pipe writes real audio, and the
phone's own call recording is disabled by its regional firmware. What it never
measured: a live call.

That path is now closed by decree anyway — ONLY STEPS AN ORDINARY USER CAN DO
rules out shell identity, Shizuku, ADB and root, whatever they can technically do.
The probe stays on disk only until the owner says it may be deleted.

| File | Role |
|------|------|
| `src/main/java/com/uvuruna/callprobe/` | The probe app: one screen, a recorder service, a listen service, playback. |
| [`probe-tools/`](probe-tools/___probe-tools.md) | Shell-identity call-audio probe, run over ADB. |

## Privacy note

Even the throwaway obeyed the project laws: recordings stayed in app-private
storage, nothing left the device. The product tightens that further — everything
is encrypted at rest and nothing is exposed beyond a temporary shared copy the
user explicitly asks for.
