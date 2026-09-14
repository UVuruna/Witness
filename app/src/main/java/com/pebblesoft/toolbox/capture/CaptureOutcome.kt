package com.pebblesoft.toolbox.capture

/**
 * What one recording actually contained, measured while it was being written.
 *
 * This is the evidence behind the evidence. THE HALF-RECORDING LAW (CLAUDE.md)
 * forbids handing over a file in which the other person is missing, and the only
 * way to obey it without guessing is to measure: how many channels the device
 * gave us, how loud each one was, how much of the time each one carried speech,
 * and — the part that separates two people from one person duplicated — how
 * different the two channels are from each other.
 *
 * The privileged recorder runs in Shizuku's shell process, so this crosses a
 * process boundary through AIDL. It travels as one flat line of `key=value`
 * pairs rather than a Parcelable: the AIDL surface stays one string wide, and a
 * version of the app that gains a field can still read one that lacks it.
 */
data class ChannelEnergy(
    /** Loudest sample seen on this channel, 0..32767. */
    val peak: Int,
    /** Root-mean-square level over the whole recording, 0..32767. */
    val rms: Int,
    /** Frames on this channel loud enough to be someone talking. */
    val activeFrames: Long,
) {
    fun activeShare(totalFrames: Long): Double =
        if (totalFrames <= 0) 0.0 else activeFrames.toDouble() / totalFrames
}

data class CaptureOutcome(
    /** The audio source that actually opened — the ladder may have stepped down. */
    val source: String = "",
    val channels: Int = 0,
    val frames: Long = 0,
    val energy: List<ChannelEnergy> = emptyList(),
    /**
     * RMS of the difference between the two channels. Near zero means the device
     * handed us one signal twice, which proves nothing about the second person;
     * a large value means the channels carry genuinely different audio.
     */
    val differenceRms: Int = 0,
    /**
     * Loudest sample in each quarter-second of the first minute. The guided test
     * call reads this to answer the one question energy alone cannot: was there
     * sound while the user was asked to stay silent?
     */
    val timeline: List<Int> = emptyList(),
    /**
     * True only when the channels came from the CALL's own audio, where two
     * channels mean uplink and downlink — two people. A microphone recording
     * two channels is two points in one room, and two room microphones hearing
     * one shouting person also differ, so the same test there would stamp
     * evidence on a file holding one person. The flag exists so that test can
     * never be applied to the wrong kind of stream.
     */
    val callAudio: Boolean = false,
    /** Empty when the recording ran; otherwise why it did not. */
    val error: String = "",
) {
    fun isEmpty(): Boolean = frames <= 0L

    /** The flat wire form sent back across AIDL. */
    fun encode(): String = buildString {
        append("source=").append(source)
        append("|channels=").append(channels)
        append("|frames=").append(frames)
        append("|diff=").append(differenceRms)
        energy.forEachIndexed { index, e ->
            append("|ch").append(index).append('=')
            append(e.peak).append(',').append(e.rms).append(',').append(e.activeFrames)
        }
        append("|timeline=").append(timeline.joinToString(","))
        append("|callaudio=").append(if (callAudio) "1" else "0")
        append("|error=").append(error.replace('|', '/'))
    }

    companion object {
        /** A recording that never started, carrying the reason in [error]. */
        fun failed(reason: String): CaptureOutcome = CaptureOutcome(error = reason)

        fun decode(wire: String): CaptureOutcome {
            val fields = wire.split('|')
                .mapNotNull { part ->
                    val at = part.indexOf('=')
                    if (at <= 0) null else part.substring(0, at) to part.substring(at + 1)
                }
                .toMap()
            val channels = fields["channels"]?.toIntOrNull() ?: 0
            return CaptureOutcome(
                source = fields["source"].orEmpty(),
                channels = channels,
                frames = fields["frames"]?.toLongOrNull() ?: 0L,
                energy = (0 until channels).mapNotNull { index ->
                    fields["ch$index"]?.split(',')?.let { parts ->
                        if (parts.size < 3) null
                        else ChannelEnergy(
                            peak = parts[0].toIntOrNull() ?: 0,
                            rms = parts[1].toIntOrNull() ?: 0,
                            activeFrames = parts[2].toLongOrNull() ?: 0L,
                        )
                    }
                },
                differenceRms = fields["diff"]?.toIntOrNull() ?: 0,
                timeline = fields["timeline"].orEmpty()
                    .split(',').mapNotNull(String::toIntOrNull),
                callAudio = fields["callaudio"] == "1",
                error = fields["error"].orEmpty(),
            )
        }
    }
}
