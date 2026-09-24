# Setup guide

How the user is led through the one setup she performs herself — what the app
shows her, in what order, and where today's honest limits are. This is the
specification behind the in-app flow, not a separate manual: everything here is
either already on screen or named as the copy that must change before it is true.

Navigation: [README](../README.md) · [STATUS](STATUS.md) · [PLAN](PLAN.md) ·
[FEATURES](FEATURES.md) · [ARCHITECTURE](ARCHITECTURE.md)

The rule this serves — **ONE SETUP, GUIDED, THEN NOTHING** (CLAUDE.md): the user
may be asked for ONE setup on her own phone, with the app leading her step by
step and no computer involved. Everything after it is automatic. If a mechanism
cannot be written as a short numbered list she can follow alone, it does not ship.

---

## Where the setup lives in the app

Two doors, one screen (`ui/setup/SetupScreen.kt`):

1. **First launch.** A phone with nothing set up shows the guided screen straight
   away; the home screen reads "not saving yet" until she finishes.
2. **A button she can always find.** The home screen carries
   **"Show me what to do"** (`home_setup_button`), so she can reopen the guide at
   any time — after a phone change, after a reboot, or just to check.

The screen asks the phone what it can do (`CaptureRegistry.candidate`) and shows
the matching guide. It never invents steps for a phone that cannot record.

## What she sees, step by step

The headline is **"Turn on call saving."** Under it, one intro line:
*"Do these steps once. After that every call is saved by itself — you never
touch the app while the phone rings."* Then a numbered card per step, each with
its own button that jumps straight to the screen it talks about, and a tick that
replaces the number once the step is done and stops asking.

**Permissions first** (from `RuntimePermissions.steps`): microphone, phone state,
call log, notifications, and battery exemption — each its own card with the one
sentence that says why, and an **Allow** button. Asking here, inside the list,
is deliberate: the earlier build declared these permissions and requested none,
which is the whole reason nothing recorded.

**Then the four Shizuku steps** — Shizuku is the small free helper that lets the
app save the call quietly, during the call:

| # | Title | What she does |
|---|-------|---------------|
| 1 | **Install the helper** | Tap **Open** to get Shizuku, then come back. |
| 2 | **Turn on wireless debugging** | Tap **Open**. If there is no "Developer options", tap Settings → About phone → "Build number" seven times first, then turn on "Wireless debugging". |
| 3 | **Start the helper** | Tap **Open** to go to Shizuku, press **Pairing**, enter the code, press **Start**. It says "Shizuku is running". |
| 4 | **Allow this app** | Come back; when Shizuku asks, tap **Allow**. |

A closing **"I have finished"** button returns her to the home screen.

## The twenty-second test call

Setup does not end at "allowed" — it ends at *proven*. The app never says a phone
is protected on faith; it measures (`ui/setup/TestCallScreen.kt`). The user places
one short guided call; the app records it, checks that **both people** are in the
file, and only then lets the home screen read READY. Whether the far party
actually reaches the recording is decided below the app by the manufacturer's
audio driver, so it is measured per phone rather than promised. A phone that
yields one voice only is told so plainly, not walked into a false sense of safety.

## After a reboot

The current copy (`guide_shizuku_keep_reboot`): *"If you restart your phone, open
this app once and tap the button on the home screen to turn saving back on."*
The listening service returns when she opens the app; Shizuku, by Android's own
design, does not survive a reboot and has to be started again. See the honest
limits below — this line promises more than the button can deliver today.

## When the phone cannot do it

If no route fits the phone, the screen shows a plain empty state
(`setup_none_title` / `setup_none_detail`): *"Not ready on this phone yet — this
phone cannot save calls in a way that keeps both voices. The app will not pretend
otherwise."* An honest dead end beats a setup that ends in silence.

## Is this legal?

The app records **the user's own conversation** — her voice and the voice of the
person she is speaking with — and never a conversation she is not part of. That
is participant recording, which is a different matter from the unauthorized
interception of other people's conversations. Whether such a recording is
*admissible as evidence* in a particular proceeding is a question for a lawyer,
not for the app; the app states what it does plainly and gives no legal advice.

## What still has to be true before this guide ships

Measured on an Android 16 emulator on 2026-09-24 (see [STATUS](STATUS.md)). The
flow above is the intended experience; these are the gaps between it and today's
build, none yet fixed because a build needs the owner's word:

- **Step 4 says "you are protected" before any test has run.** Protection is only
  ever true after the test call measures both voices. The last step must hand off
  to the test, not declare victory.
- **The reboot line promises a button that cannot restart Shizuku.** After a
  reboot the app must offer the honest action — walk her back through starting
  Shizuku — not a button that silently fails.
- **Step 1 links Shizuku on Google Play**, which carries an older version; the
  build with the self-start on a trusted Wi-Fi is on GitHub. The link should
  point where the working version actually is.
- **The pairing wording** ("enter the code it shows") should name where the code
  appears — Android's own Wireless debugging screen — and that Wireless debugging
  needs Wi-Fi to switch on at all.
- **The recording itself does not work yet on the emulator**: the quiet route is
  refused by Android's audio server (a construction fix, proven in a harness, not
  yet in the app), and the loudspeaker fallback is silenced by the system for the
  whole call. Until the quiet route is fixed, finishing this setup does not yet
  produce a recording — [STATUS](STATUS.md) tracks it.

The cost this setup carries for the user — developer options and wireless
debugging left on, a visible system notice, some banking apps that refuse to run
alongside them — is set out in [STATUS](STATUS.md) so no one is surprised by it.
