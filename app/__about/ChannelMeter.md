# ChannelMeter

Measures a live PCM stream while it is being written.

## Responsibility

Produce the numbers `VoiceCheck` needs: per channel loudness and speech
activity, how different the channels are from one another, and a coarse loudness
timeline.

## Decisions that outlive the code

- **Measure while writing, never afterwards.** Re-reading and decoding a sealed
  recording to judge it would mean decrypting evidence to ask a question that
  could have been answered for free as the bytes went past.
- **The channel difference is a first-class output.** It is what separates two
  people from one microphone signal copied into two channels, and it costs one
  multiply per frame.
- **A timeline, not a waveform.** One peak per quarter second for the first
  minute is enough to answer "was there sound while she was told to be quiet"
  and small enough to cross a process boundary as text.
- **No Android API, no per-buffer allocation.** This runs inside Shizuku's shell
  process as well as the app's, and inside the recording thread of a live call.

## Connections

- Fed by: `capture/PcmRecorder`, one buffer at a time.
- Produces: `capture/CaptureOutcome`.
- Judged by: `capture/VoiceCheck`.
