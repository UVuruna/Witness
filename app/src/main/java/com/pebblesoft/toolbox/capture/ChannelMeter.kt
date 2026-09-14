package com.pebblesoft.toolbox.capture

/**
 * Measures a live PCM stream while it is being written, one channel at a time.
 *
 * The previous round decided a recording held both voices from its peak
 * amplitude alone, which is only ever a claim that the file is not silent — a
 * recording of one person shouting has a very high peak. This class exists so
 * the verdict rests on something that can actually tell two people apart:
 *
 *  - per channel: how loud, and for how much of the call it carried speech
 *  - across channels: how DIFFERENT they are, which is what separates a real
 *    uplink/downlink pair from one microphone signal copied into both channels
 *  - across time: a coarse loudness timeline, so the guided test call can ask
 *    "was there sound while she was told to stay quiet?" — the one question that
 *    proves the far party is present even in a single-channel recording
 *
 * It runs inside Shizuku's shell process as well as the app's, so it touches no
 * Android API and allocates nothing per buffer.
 */
class ChannelMeter(
    private val channels: Int,
    /** How many frames make one timeline slot — the caller knows the sample rate. */
    private val framesPerSlot: Long = DEFAULT_FRAMES_PER_SLOT,
) {

    private val peak = IntArray(channels)
    private val squareSum = DoubleArray(channels)
    private val active = LongArray(channels)
    private var frames = 0L

    private var differenceSquareSum = 0.0

    private val timeline = mutableListOf<Int>()
    private var slotPeak = 0
    private var slotFrames = 0L

    /** Feed the same bytes that go into the file: interleaved 16-bit little-endian. */
    fun feed(buffer: ByteArray, length: Int) {
        val bytesPerFrame = channels * 2
        var offset = 0
        while (offset + bytesPerFrame <= length) {
            var first = 0
            var frameLoudest = 0
            for (channel in 0 until channels) {
                val at = offset + channel * 2
                val sample = ((buffer[at].toInt() and 0xFF) or (buffer[at + 1].toInt() shl 8)).toShort().toInt()
                val magnitude = if (sample < 0) -sample else sample

                if (magnitude > peak[channel]) peak[channel] = magnitude
                squareSum[channel] += sample.toDouble() * sample
                if (magnitude > SPEECH_FLOOR) active[channel]++
                if (magnitude > frameLoudest) frameLoudest = magnitude

                if (channel == 0) first = sample
                else if (channel == 1) {
                    val difference = (first - sample).toDouble()
                    differenceSquareSum += difference * difference
                }
            }
            frames++
            if (frameLoudest > slotPeak) slotPeak = frameLoudest
            if (++slotFrames >= framesPerSlot) {
                if (timeline.size < TIMELINE_SLOTS) timeline += slotPeak
                slotPeak = 0
                slotFrames = 0
            }
            offset += bytesPerFrame
        }
    }

    fun outcome(source: String, callAudio: Boolean, error: String = ""): CaptureOutcome = CaptureOutcome(
        source = source,
        callAudio = callAudio,
        channels = channels,
        frames = frames,
        energy = (0 until channels).map { channel ->
            ChannelEnergy(
                peak = peak[channel],
                rms = rootMeanSquare(squareSum[channel]),
                activeFrames = active[channel],
            )
        },
        differenceRms = if (channels >= 2) rootMeanSquare(differenceSquareSum) else 0,
        timeline = timeline.toList(),
        error = error,
    )

    private fun rootMeanSquare(sumOfSquares: Double): Int =
        if (frames <= 0L) 0 else Math.sqrt(sumOfSquares / frames).toInt()

    private companion object {
        /** Above this magnitude a frame counts as somebody talking, not room noise. */
        const val SPEECH_FLOOR = 600

        /** One minute of quarter-second slots — enough for the guided test call. */
        const val TIMELINE_SLOTS = 240

        /** A quarter second at the capture rate the recorders use. */
        const val DEFAULT_FRAMES_PER_SLOT = 4_000L
    }
}
