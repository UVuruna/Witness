# Features

The product's main functionalities, written for the future user and grouped by
kinship. Ledger tasks tag the feature they serve with its `#slug`.

Navigation: [README](../README.md) · [PLAN](PLAN.md) · [ARCHITECTURE](ARCHITECTURE.md)

---

## Recording

### Automatic call recording · `call-recording`

Every phone call is recorded on the device, start to finish, without the user
touching anything — the recording simply exists after the call ends. Recording
uses only what Android officially allows (the microphone, with the call on
speakerphone where the device requires it); on devices where the platform
limits what the microphone can hear during a call, the app says so honestly
instead of recording silence.

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
