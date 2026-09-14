# MicRecorder

Speaker on, microphone recording the room.

## Responsibility

Record a conversation through the phone's own loudspeaker and microphone, and
put the audio routing back the way it was found.

## Decisions that outlive the code

- **This is the route that always works.** The privileged route depends on a
  manufacturer's audio driver cooperating; this one depends on physics. The far
  party's voice leaves the loudspeaker into the room and the microphone hears the
  room, so both people land in the file on any handset ever made.
- **Echo cancellation is the enemy here.** `VOICE_COMMUNICATION` exists to
  remove exactly what this route came for — the far party's voice coming back out
  of the speaker. So the ladder starts at `UNPROCESSED`, then `MIC`, and only
  reaches the processed sources when nothing else opens.
- **The routing is borrowed, not taken.** Whatever the phone was doing with its
  audio output is restored on stop, including when the recorder fails to start.
- **It is never on by default.** The call is audible to everyone nearby, which
  for someone living with the person on the other end can be the more dangerous
  option. The user switches it on knowingly, and the guide says what she is
  agreeing to.

## Connections

- Engine: `capture/PcmRecorder`.
- Offered by: `capture/SpeakerphoneCaptureSource`, in two variants — ordinary
  calls and calls inside other apps.
