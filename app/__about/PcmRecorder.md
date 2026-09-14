# PcmRecorder

The one recording engine, hosted by two different processes.

## Responsibility

Open the best audio source a caller is entitled to, write 16-bit PCM into a
descriptor the app owns, and measure the stream while it goes past.

## Decisions that outlive the code

- **Stereo before mono, on every source.** A class of devices exposes the two
  directions of a call as the two channels of one stream. Asking for mono there
  takes one leg of the conversation and silently discards the other — a
  one-sided recording that looks perfectly healthy. The previous round asked for
  `CHANNEL_IN_MONO` everywhere and would have thrown the far party away on
  exactly the devices where the privileged route works best.
- **A ladder, not a single source.** A refused source is a rung, not a failure.
  The caller passes what it may name, best first, and what actually opened is
  reported back rather than assumed.
- **Two hosts, one engine.** The privileged recorder in Shizuku's shell process
  and the microphone recorder in the app differ only in which sources they may
  name (ONE KIND, ONE CLASS). Everything that decides whether the file is worth
  anything lives here, once.
- **Errors become results, not crashes.** This runs with borrowed privilege and
  inside a live call; a thrown exception would take down the shell process
  mid-conversation. Failures are recorded into the outcome and travel back as
  data.

## Connections

- Hosted by: `capture/shizuku/PrivilegedRecorder` and `capture/MicRecorder`.
- Writes through: `capture/WavWriter`.
- Measures with: `capture/ChannelMeter`.
