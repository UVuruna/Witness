package com.pebblesoft.toolbox.capture.shizuku

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * The privileged recorder — the one process that can hear the call.
 *
 * Shizuku spawns this class in a SEPARATE process running as the ADB shell
 * (uid 2000), which holds `CAPTURE_AUDIO_OUTPUT` and `CALL_AUDIO_INTERCEPTION`.
 * That is the whole reason it exists: an ordinary app process is refused
 * `VOICE_CALL` and handed silence, and no amount of app-side code changes that.
 * Here, as shell, the same `AudioRecord` call succeeds.
 *
 * It writes 16-bit mono PCM into a WAV whose bytes go to a file descriptor the
 * APP opened and passed in. So the privileged process never writes into
 * app-private storage itself — it only fills a pipe the app already owns. When
 * recording stops the app reads that file, encrypts it into the vault, and
 * deletes the plaintext.
 *
 * Every method is defensive: this runs with elevated privilege, so a thrown
 * exception must come back as a string the app can show, never a crash that
 * takes the shell process down mid-call.
 */
class PrivilegedRecorder : IRecorderService.Stub() {

    private companion object {
        const val SAMPLE_RATE = 16_000
        val ORDINARY_SOURCES = linkedMapOf(
            "MIC" to MediaRecorder.AudioSource.MIC,
            "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
            "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        )
        val PRIVILEGED_SOURCES = linkedMapOf(
            "VOICE_CALL" to MediaRecorder.AudioSource.VOICE_CALL,
            "VOICE_UPLINK" to MediaRecorder.AudioSource.VOICE_UPLINK,
            "VOICE_DOWNLINK" to MediaRecorder.AudioSource.VOICE_DOWNLINK,
        )
    }

    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var writer: FileOutputStream? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var running = false
    @Volatile private var peak = 0
    @Volatile private var bytesWritten = 0L

    override fun probe(): String {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        val report = StringBuilder()
        for ((name, source) in ORDINARY_SOURCES + PRIVILEGED_SOURCES) {
            report.append(name).append('=')
            try {
                val test = AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, minBuf)
                report.append(if (test.state == AudioRecord.STATE_INITIALIZED) "OPENED" else "REFUSED")
                test.release()
            } catch (e: Throwable) {
                report.append("ERR:").append(e.javaClass.simpleName)
            }
            report.append('\n')
        }
        return report.toString()
    }

    override fun start(audioSource: Int, sink: ParcelFileDescriptor): String {
        if (running) return "already recording"
        return try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val record = AudioRecord(audioSource, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuf)
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return "source refused (state ${record.state})"
            }
            val out = FileOutputStream(sink.fileDescriptor)
            writeWavHeaderPlaceholder(out)
            record.startRecording()

            recorder = record
            writer = out
            running = true
            peak = 0
            bytesWritten = 0L

            worker = thread(name = "call-capture") {
                val buffer = ByteArray(minBuf)
                while (running) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        out.write(buffer, 0, read)
                        bytesWritten += read
                        peak = maxOf(peak, peakOf(buffer, read))
                    }
                }
            }
            ""
        } catch (e: Throwable) {
            running = false
            "start failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    override fun stop(): Int {
        running = false
        worker?.join(2_000)
        worker = null
        try { recorder?.stop() } catch (_: Throwable) {}
        recorder?.release()
        recorder = null
        try {
            writer?.let { patchWavSizes(it, bytesWritten) }
            writer?.flush()
            writer?.close()
        } catch (_: Throwable) {}
        writer = null
        return peak
    }

    override fun isRecording(): Boolean = running

    override fun destroy() {
        if (running) stop()
    }

    // ── WAV plumbing ────────────────────────────────────────────────────────
    // A 44-byte header is written first with zeroed sizes, then patched on stop.
    // The sink fd is not guaranteed seekable (it may be a pipe), so we only
    // patch when it is a real file — the header stays valid either way because
    // 0xFFFFFFFF is what streaming WAV writers use for "length unknown".

    private fun writeWavHeaderPlaceholder(out: FileOutputStream) {
        val byteRate = SAMPLE_RATE * 2
        val header = ByteArray(44)
        "RIFF".toByteArray().copyInto(header, 0)
        writeIntLE(header, 4, -1)
        "WAVE".toByteArray().copyInto(header, 8)
        "fmt ".toByteArray().copyInto(header, 12)
        writeIntLE(header, 16, 16)
        writeShortLE(header, 20, 1)          // PCM
        writeShortLE(header, 22, 1)          // mono
        writeIntLE(header, 24, SAMPLE_RATE)
        writeIntLE(header, 28, byteRate)
        writeShortLE(header, 32, 2)          // block align
        writeShortLE(header, 34, 16)         // bits per sample
        "data".toByteArray().copyInto(header, 36)
        writeIntLE(header, 40, -1)
        out.write(header)
    }

    private fun patchWavSizes(out: FileOutputStream, dataBytes: Long) {
        try {
            val channel = out.channel
            val riff = ByteArray(4); writeIntLE(riff, 0, (36 + dataBytes).toInt())
            val data = ByteArray(4); writeIntLE(data, 0, dataBytes.toInt())
            channel.position(4).write(java.nio.ByteBuffer.wrap(riff))
            channel.position(40).write(java.nio.ByteBuffer.wrap(data))
        } catch (_: Throwable) {
            // Not seekable (a pipe) — the -1 sizes remain, which players accept.
        }
    }

    private fun peakOf(buffer: ByteArray, length: Int): Int {
        var max = 0
        var i = 0
        while (i + 1 < length) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val abs = if (sample < 0) -sample else sample
            if (abs > max) max = abs
            i += 2
        }
        return max
    }

    private fun writeIntLE(b: ByteArray, at: Int, v: Int) {
        b[at] = (v and 0xFF).toByte()
        b[at + 1] = (v shr 8 and 0xFF).toByte()
        b[at + 2] = (v shr 16 and 0xFF).toByte()
        b[at + 3] = (v shr 24 and 0xFF).toByte()
    }

    private fun writeShortLE(b: ByteArray, at: Int, v: Int) {
        b[at] = (v and 0xFF).toByte()
        b[at + 1] = (v shr 8 and 0xFF).toByte()
    }
}
