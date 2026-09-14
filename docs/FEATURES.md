# Features

The product's main functionalities, written for the future user and grouped by
kinship. Ledger tasks tag the feature they serve with its `#slug`.

What is BUILT today: `call-recording`, `voip`, `record-lists`, `vault`. What is
designed and not yet written: `transcript`, `disguise` (and the PIN gate beside
it), `backup`, `sos`. [PLAN](PLAN.md) carries the order.

Navigation: [README](../README.md) · [PLAN](PLAN.md) · [ARCHITECTURE](ARCHITECTURE.md)

---

## Recording

### Automatic call recording · `call-recording`

Every phone call is recorded on the device, start to finish, without the user
touching anything — the recording simply exists after the call ends.

There are two ways to get both people into that file, and the app uses whichever
this particular phone can actually deliver. **Quietly:** the call's own audio,
opened through a privilege Shizuku lends the app after one guided pairing — no
root, no computer, and nobody in the room notices. **Out loud:** the call on the
loudspeaker with the microphone recording the room, which works on every handset
ever made and costs the user the privacy of the room.

Which one works here is not promised, it is **measured**. Once, during setup, the
app asks for twenty seconds of a real call: talk for five seconds, then stay
silent for ten and let the other side talk. Anything recorded during that silence
can only be the other person — which is the only honest proof that both voices
reach the file. Until that proof exists the app does not claim to be protecting
anyone, and a phone where the quiet route records the user alone is told so, and
pointed at the route that works.

### Calls in other apps · `voip`

Calls through WhatsApp, Viber, Messenger and their kind are invisible to
Android's call-state signal, and their audio never passes through anything the
privileged route can reach. The app notices them anyway — every one of those apps
puts the phone into communication mode, which costs no permission to observe.
With loudspeaker recording switched on, such a call is saved like any other. With
it off, the call still becomes a line saying it happened and was not saved: a gap
the user can see beats a gap she cannot.

### Record and skip lists · `record-lists`

The user decides who gets recorded: everyone by default, or their own lists —
"always record these numbers" and "never record these". The default is
record-everything, because a victim must not lose the one call that mattered
to a list they forgot to update.

## Understanding

### Speaker-labeled transcript · `transcript`

Every recording becomes text, on the device, with no internet needed. The
transcript is a timeline: each line carries a timestamp, who was speaking, and
what was said — so a two-hour recording can be read, searched, and quoted in
minutes.

Where the phone hands back the two directions of a call as two separate channels,
the speaker labels are not guessed at all: the device has already separated the
two people, and the app only has to name them.

## Evidence

### Encrypted vault · `vault`

Recordings and transcripts live encrypted inside the app, invisible to the
gallery, the file manager, and anyone scrolling through the phone. Each file
is sealed with a hash and timestamp at the moment it is created, so it can
later prove it was never edited — evidence, not just audio.

### Off-phone backup · `backup`

Optionally, the user connects their own cloud storage and the vault keeps a
copy there — so the evidence survives even if the phone is destroyed, reset,
or taken away.

## Protection

### Hidden in plain sight · `disguise`

The app offers a set of alternative names and icons — a calculator, a notes
app, a unit converter — and the user switches to one with a single tap, for
exactly the situation where the abuser controls or inspects the phone (owner
decree 2026-09-01: this option is core, never dropped). The app opens only
past a PIN or fingerprint; someone inspecting the phone sees nothing worth
opening, and someone forcing the wrong PIN sees an empty, boring app.

## Emergency

### Danger phrase SOS · `sos`

The user rehearses a phrase of their own choosing — any words that can be said
naturally in a dangerous moment. When the app hears that phrase, it acts by
itself: the user's live location goes silently to one or more trusted contacts
they picked in advance. No tap, no unlock, no confirmation — by the time the
phrase is spoken, hands may not be free. A short cancel window guards against
a false alarm.
