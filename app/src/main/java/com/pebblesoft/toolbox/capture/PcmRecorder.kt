package com.pebblesoft.toolbox.capture

import android.media.AudioFormat
import android.media.AudioRecord
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * The one recording engine, hosted by two different processes.
 *
 * ONE KIND, ONE CLASS: the privileged recorder inside Shizuku's shell process
 * and the microphone recorder inside the app are not two recorders — they are
 * two hosts of this one, differing only in which audio sources they are allowed
 * to name. Everything that decides whether the file is worth anything (stereo
 * first, the source ladder, the per-channel measurement, the WAV container)
 * happens here, once.
 *
 * **Stereo first, always.** A class of devices exposes the two directions of a
 * call as the two channels of one stream. Asking for mono on those devices gets
 * one leg of the conversation and silently discards the other — which is how a
 * recording ends up holding one person while looking perfectly healthy. So every
 * source is tried in stereo before it is tried in mono, and the channel count
 * that actually opened travels out with the result.
 *
 * **The ladder.** A source that a device refuses is not a failure of the app; it
 * is one rung. The caller passes the sources it is entitled to, best first, and
 * the first combination that opens wins. What opened is reported, never assumed.
 */
class PcmRecorder {

    @Volatile private var record: AudioRecord? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var running = false
    @Volatile private var meter: ChannelMeter? = null
    @Volatile private var writer: WavWriter? = null
    @Volatile private var openedSource = ""
    @Volatile private var readFailure = ""
    @Volatile private var callAudio = false

    fun isRecording(): Boolean = running

    /**
     * Open the best available source and start writing into [sink].
     *
     * @param ladder candidate sources, best first, as (name, MediaRecorder.AudioSource).
     * @param stereoFirst try two channels before one. TRUE only for the call's
     * own audio, where the two channels are the two directions of the call;
     * FALSE for a microphone, where two channels are two points in one room and
     * would let the both-voices test fire on one person talking loudly.
     * @param callAudio whether this stream is the call's own audio, carried out
     * with the measurement so the verdict can never be reached the wrong way.
     * @return "" when recording started, otherwise why every rung failed.
     */
    fun start(
        ladder: List<Pair<String, Int>>,
        sink: FileOutputStream,
        stereoFirst: Boolean,
        callAudio: Boolean,
    ): String {
        if (running) return "already recording"
        this.callAudio = callAudio

        val preference = if (stereoFirst) STEREO_THEN_MONO else MONO_ONLY
        val refusals = mutableListOf<String>()
        for ((name, source) in ladder) {
            for (channels in preference) {
                val opened = open(source, channels)
                if (opened == null) {
                    refusals += "$name/${channels}ch"
                    continue
                }
                return begin(opened, "$name/${channels}ch", channels, sink)
            }
        }
        return "no audio source opened (tried ${refusals.joinToString(", ")})"
    }

    /** Stop, finalise the file, and hand back what was measured while it ran. */
    fun stop(): CaptureOutcome {
        if (!running && meter == null) return CaptureOutcome.failed("was not recording")
        running = false
        worker?.join(WORKER_JOIN_MS)
        worker = null

        record?.let { open ->
            runCatching { open.stop() }
            open.release()
        }
        record = null

        val measured = meter?.outcome(openedSource, callAudio, readFailure)
            ?: CaptureOutcome.failed("no meter")
        meter = null

        try {
            writer?.finish()
        } catch (e: java.io.IOException) {
            writer = null
            return measured.copy(error = measured.error.ifEmpty { "closing the file failed: ${e.message}" })
        }
        writer = null
        return measured
    }

    private fun open(source: Int, channels: Int): AudioRecord? {
        val mask = if (channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, mask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return null
        val candidate = try {
            AudioRecord(source, SAMPLE_RATE, mask, AudioFormat.ENCODING_PCM_16BIT, minBuffer * BUFFER_FACTOR)
        } catch (e: IllegalArgumentException) {
            return null
        } catch (e: SecurityException) {
            return null
        }
        if (candidate.state != AudioRecord.STATE_INITIALIZED) {
            candidate.release()
            return null
        }
        return candidate
    }

    private fun begin(open: AudioRecord, label: String, channels: Int, sink: FileOutputStream): String {
        val bufferBytes = (AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            if (channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ) * BUFFER_FACTOR).coerceAtLeast(MIN_BUFFER_BYTES)

        val wav = WavWriter(sink, SAMPLE_RATE, channels)
        val instrument = ChannelMeter(channels, FRAMES_PER_TIMELINE_SLOT)

        try {
            open.startRecording()
        } catch (e: IllegalStateException) {
            open.release()
            return "source opened but refused to start: ${e.message}"
        }

        record = open
        writer = wav
        meter = instrument
        openedSource = label
        readFailure = ""
        running = true

        worker = thread(name = "call-capture") {
            val buffer = ByteArray(bufferBytes)
            while (running) {
                val read = open.read(buffer, 0, buffer.size)
                if (read > 0) {
                    try {
                        wav.write(buffer, read)
                    } catch (e: java.io.IOException) {
                        readFailure = "writing the file failed: ${e.message}"
                        running = false
                        break
                    }
                    instrument.feed(buffer, read)
                } else if (read < 0) {
                    readFailure = "the audio source stopped delivering (code $read)"
                    running = false
                }
            }
        }
        return ""
    }

    private companion object {
        const val SAMPLE_RATE = 16_000

        /** Call audio: on the devices that split the call, mono throws half of it away. */
        val STEREO_THEN_MONO = intArrayOf(2, 1)

        /** A microphone: a second channel is a second point in one room, never a second person. */
        val MONO_ONLY = intArrayOf(1)

        const val BUFFER_FACTOR = 4
        const val MIN_BUFFER_BYTES = 8192
        const val WORKER_JOIN_MS = 2_000L

        /** A quarter second at [SAMPLE_RATE], which is one timeline slot. */
        const val FRAMES_PER_TIMELINE_SLOT = 4_000L
    }
}
