# SpeakerphoneCaptureSource

The capture route that satisfies EVERY PHONE, OR IT DOES NOT COUNT.

## Responsibility

Describe the loudspeaker route to the registry and the setup screen, in two
variants — ordinary calls and calls inside other apps — and hand over the
recorder that performs it.

## Decisions that outlive the code

- **TWO VOICES MEANS TWO PEOPLE, BY ANY ROUTE** (owner, 2026-09-14). This route
  puts both people in the file without asking the phone for anything unusual, so
  it satisfies the half-recording law in full. It is the only mechanism in the
  app that no manufacturer can close.
- **Two entries, one class.** Carrier calls and calls inside other apps are the
  same mechanism behind two different switches, so they are two entries of this
  class rather than two classes (ONE KIND, ONE CLASS). `Variant` is the whole
  difference — plus one asymmetry worth naming: the VOIP entry can only be
  measured by a test that NAMES it, because such a call never reaches the
  telephony path and so is never chosen as "the best available route". Without
  that, everything it recorded would stay `UNVERIFIED` for the life of the
  install however well it worked.
- **It records mono, on purpose.** `PcmRecorder` asks for stereo first for the
  call's own audio, where two channels are the two directions. Two microphone
  channels are two points in one room: they differ, and both are active, while
  one person talks — which would let the both-voices test pass on a file holding
  one person. This route proves itself through the test call's silent window
  instead, and the stream is flagged as not being call audio so the wrong proof
  cannot be reached at all.
- **Off until she turns it on.** The cost — everyone nearby hears the call — is
  real and is hers to weigh, so `status()` reports `UNAVAILABLE` until the switch
  is on, and the guide states the cost before the switch appears.
- **It still has to prove itself.** Being physically sound is not the same as
  working on a given handset, so this route takes the guided test call like any
  other and reports `NEEDS_TEST` until it has.
- **The Wi-Fi calling deep link lives here.** Turning Wi-Fi calling off is the
  one system setting the owner allowed the app to ask for (2026-09-14), and only
  because on many phones it is the difference between a recording and silence.
  Where a phone hides that screen differs by manufacturer, so the app offers the
  most specific screen the device answers to and falls back rather than
  presenting a dead button.

## Connections

- Records with: `capture/MicRecorder`.
- Registered by: `ToolboxApp`, after the quiet route.
- Chosen by: `capture/RecordingCoordinator`; the VoIP variant is the only
  candidate for a call inside another app.
