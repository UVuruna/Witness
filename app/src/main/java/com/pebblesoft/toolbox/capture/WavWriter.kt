package com.pebblesoft.toolbox.capture

import java.io.FileOutputStream

/**
 * Writes 16-bit PCM into a WAV file, and nothing else.
 *
 * Both recorders produce the same bytes in the same container — the privileged
 * one inside Shizuku's shell process, the microphone one inside the app — so the
 * header arithmetic lives here once. ONE KIND, ONE CLASS: a second recorder is a
 * second host for this writer, never a second copy of the header code.
 *
 * The size fields cannot be known until the call ends, so a 44-byte header goes
 * down first with 0xFFFFFFFF in both length slots and is patched on [finish].
 * When the destination is not seekable (a pipe), the patch is skipped and the
 * 0xFFFFFFFF stays — which is exactly what streaming WAV writers emit for
 * "length unknown", and what players accept.
 */
class WavWriter(
    private val out: FileOutputStream,
    private val sampleRate: Int,
    private val channels: Int,
) {
    private var dataBytes = 0L

    init {
        writeHeader()
    }

    fun write(buffer: ByteArray, length: Int) {
        out.write(buffer, 0, length)
        dataBytes += length
    }

    /** Bytes of audio written so far — 0 means the source never produced a sample. */
    fun bytesWritten(): Long = dataBytes

    fun finish() {
        patchSizes()
        out.flush()
        out.close()
    }

    private fun writeHeader() {
        val bytesPerFrame = channels * BYTES_PER_SAMPLE
        val header = ByteArray(HEADER_BYTES)
        "RIFF".toByteArray().copyInto(header, 0)
        writeIntLE(header, 4, UNKNOWN_LENGTH)
        "WAVE".toByteArray().copyInto(header, 8)
        "fmt ".toByteArray().copyInto(header, 12)
        writeIntLE(header, 16, 16)                      // fmt chunk size
        writeShortLE(header, 20, 1)                     // PCM
        writeShortLE(header, 22, channels)
        writeIntLE(header, 24, sampleRate)
        writeIntLE(header, 28, sampleRate * bytesPerFrame)
        writeShortLE(header, 32, bytesPerFrame)
        writeShortLE(header, 34, BYTES_PER_SAMPLE * 8)
        "data".toByteArray().copyInto(header, 36)
        writeIntLE(header, 40, UNKNOWN_LENGTH)
        out.write(header)
    }

    private fun patchSizes() {
        val channel = out.channel
        if (!channel.isOpen) return
        val riff = ByteArray(4).also { writeIntLE(it, 0, (HEADER_BYTES - 8 + dataBytes).toInt()) }
        val data = ByteArray(4).also { writeIntLE(it, 0, dataBytes.toInt()) }
        try {
            channel.position(4).write(java.nio.ByteBuffer.wrap(riff))
            channel.position(40).write(java.nio.ByteBuffer.wrap(data))
        } catch (e: java.io.IOException) {
            // A pipe cannot seek. The 0xFFFFFFFF placeholders stay and remain
            // valid; anything else thrown here is a real failure and propagates.
        }
    }

    private companion object {
        const val HEADER_BYTES = 44
        const val BYTES_PER_SAMPLE = 2
        const val UNKNOWN_LENGTH = -1

        fun writeIntLE(b: ByteArray, at: Int, v: Int) {
            b[at] = (v and 0xFF).toByte()
            b[at + 1] = (v shr 8 and 0xFF).toByte()
            b[at + 2] = (v shr 16 and 0xFF).toByte()
            b[at + 3] = (v shr 24 and 0xFF).toByte()
        }

        fun writeShortLE(b: ByteArray, at: Int, v: Int) {
            b[at] = (v and 0xFF).toByte()
            b[at + 1] = (v shr 8 and 0xFF).toByte()
        }
    }
}
