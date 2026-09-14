package com.pebblesoft.toolbox.capture.shizuku

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import com.pebblesoft.toolbox.capture.CaptureOutcome
import com.pebblesoft.toolbox.capture.PcmRecorder
import java.io.FileOutputStream

/**
 * The privileged recorder — the one process that may open the call's own audio.
 *
 * Shizuku spawns this class in a SEPARATE process running as the ADB shell
 * (uid 2000), which holds `CAPTURE_AUDIO_OUTPUT` and `CALL_AUDIO_INTERCEPTION`.
 * That is the whole reason it exists: an ordinary app process is refused
 * `VOICE_CALL`, and no amount of app-side code changes that.
 *
 * It owns no recording logic of its own. The engine is [PcmRecorder], shared
 * with the app-side microphone recorder; this class contributes exactly one
 * thing the app side cannot — the ladder of sources only a privileged uid may
 * name, best first:
 *
 *  1. `VOICE_CALL` — both directions mixed, or split across two channels
 *  2. `VOICE_DOWNLINK` — the far party alone, the half the victim cannot
 *     produce herself and therefore the half worth having
 *  3. `VOICE_COMMUNICATION` / `MIC` — last rungs, so a device that refuses
 *     every privileged source still records something rather than nothing
 *
 * What opened is measured and reported, never assumed: the app decides what the
 * file is worth from [CaptureOutcome], not from which rung was tried.
 */
class PrivilegedRecorder : IRecorderService.Stub() {

    private val engine = PcmRecorder()

    override fun probe(): String {
        val report = StringBuilder()
        for ((name, source) in PROBE_SOURCES) {
            for (channels in intArrayOf(2, 1)) {
                val mask = if (channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
                report.append(name).append('/').append(channels).append("ch=")
                val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, mask, AudioFormat.ENCODING_PCM_16BIT)
                if (minBuffer <= 0) {
                    report.append("UNSUPPORTED\n")
                    continue
                }
                try {
                    val test = AudioRecord(source, SAMPLE_RATE, mask, AudioFormat.ENCODING_PCM_16BIT, minBuffer)
                    report.append(if (test.state == AudioRecord.STATE_INITIALIZED) "OPENED" else "REFUSED")
                    test.release()
                } catch (e: IllegalArgumentException) {
                    report.append("ERR:").append(e.javaClass.simpleName)
                } catch (e: SecurityException) {
                    report.append("ERR:").append(e.javaClass.simpleName)
                }
                report.append('\n')
            }
        }
        return report.toString()
    }

    override fun start(sink: ParcelFileDescriptor): String =
        engine.start(LADDER, FileOutputStream(sink.fileDescriptor))

    override fun stop(): String = engine.stop().encode()

    override fun isRecording(): Boolean = engine.isRecording()

    override fun destroy() {
        if (engine.isRecording()) engine.stop()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000

        /**
         * The sources this process may name, best first. VOICE_CALL can carry
         * both directions; VOICE_DOWNLINK carries the far party alone, which is
         * the leg an ordinary microphone can never reach.
         */
        val LADDER = listOf(
            "VOICE_CALL" to MediaRecorder.AudioSource.VOICE_CALL,
            "VOICE_DOWNLINK" to MediaRecorder.AudioSource.VOICE_DOWNLINK,
            "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            "MIC" to MediaRecorder.AudioSource.MIC,
        )

        /** Everything worth reporting from a diagnostic sweep, privileged or not. */
        val PROBE_SOURCES = listOf(
            "VOICE_CALL" to MediaRecorder.AudioSource.VOICE_CALL,
            "VOICE_UPLINK" to MediaRecorder.AudioSource.VOICE_UPLINK,
            "VOICE_DOWNLINK" to MediaRecorder.AudioSource.VOICE_DOWNLINK,
            "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
            "MIC" to MediaRecorder.AudioSource.MIC,
        )
    }
}
