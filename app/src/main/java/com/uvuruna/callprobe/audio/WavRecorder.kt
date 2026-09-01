package com.uvuruna.callprobe.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Records PCM 16-bit mono audio from a chosen AudioSource into a WAV file,
 * while measuring what the microphone ACTUALLY delivers — peak amplitude,
 * average RMS and the ratio of silent buffers. Those numbers are the whole
 * point of the M0 probe: they tell us whether the device lets the mic hear
 * a phone call at all.
 */
class WavRecorder(
    private val source: Int,
    private val outFile: File,
    private val onLevel: (Int) -> Unit,
) {
    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val WAV_HEADER_SIZE = 44L

        /** A buffer whose peak stays under this is counted as silent. */
        private const val SILENCE_PEAK = 60
    }

    @Volatile private var running = false
    private var thread: Thread? = null

    var peak: Int = 0; private set
    var rmsAvg: Double = 0.0; private set
    var silentRatio: Double = 0.0; private set
    var durationMs: Long = 0; private set
    var error: String? = null; private set

    /** Starts the capture thread. Returns false (with `error` set) when the
     *  device refuses to open this AudioSource. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) { error = "getMinBufferSize=$minBuf"; return false }
        val record = try {
            AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, minBuf * 2)
        } catch (e: Exception) {
            error = "AudioRecord: ${e.message}"; return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release(); error = "source not available on this device"; return false
        }
        running = true
        thread = Thread { captureLoop(record, minBuf) }.also { it.start() }
        return true
    }

    fun stop() {
        running = false
        thread?.join(3_000)
        thread = null
    }

    private fun captureLoop(record: AudioRecord, minBuf: Int) {
        val samples = ShortArray(minBuf)
        val bytes = ByteBuffer.allocate(minBuf * 2).order(ByteOrder.LITTLE_ENDIAN)
        var totalSamples = 0L
        var silentBuffers = 0L
        var totalBuffers = 0L
        var rmsSum = 0.0
        outFile.parentFile?.mkdirs()
        val raf = RandomAccessFile(outFile, "rw")
        try {
            raf.setLength(0)
            raf.write(ByteArray(WAV_HEADER_SIZE.toInt()))  // placeholder header
            record.startRecording()
            while (running) {
                val n = record.read(samples, 0, samples.size)
                if (n <= 0) continue
                var bufPeak = 0
                var sumSq = 0.0
                bytes.clear()
                for (i in 0 until n) {
                    val s = samples[i]
                    bytes.putShort(s)
                    val a = abs(s.toInt())
                    if (a > bufPeak) bufPeak = a
                    sumSq += s.toDouble() * s.toDouble()
                }
                raf.write(bytes.array(), 0, n * 2)
                totalSamples += n
                totalBuffers++
                if (bufPeak < SILENCE_PEAK) silentBuffers++
                rmsSum += sqrt(sumSq / n)
                if (bufPeak > peak) peak = bufPeak
                onLevel(bufPeak)
            }
        } catch (e: Exception) {
            error = "capture: ${e.message}"
        } finally {
            try { record.stop() } catch (_: Exception) {}
            record.release()
            durationMs = totalSamples * 1000 / SAMPLE_RATE
            if (totalBuffers > 0) {
                silentRatio = silentBuffers.toDouble() / totalBuffers
                rmsAvg = rmsSum / totalBuffers
            }
            writeWavHeader(raf, totalSamples * 2)
            raf.close()
        }
    }

    private fun writeWavHeader(raf: RandomAccessFile, dataBytes: Long) {
        val header = ByteBuffer.allocate(WAV_HEADER_SIZE.toInt()).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt((dataBytes + WAV_HEADER_SIZE - 8).toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)                     // fmt chunk size
        header.putShort(1)                    // PCM
        header.putShort(1)                    // mono
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2)        // byte rate
        header.putShort(2)                    // block align
        header.putShort(16)                   // bits per sample
        header.put("data".toByteArray())
        header.putInt(dataBytes.toInt())
        raf.seek(0)
        raf.write(header.array())
    }
}
