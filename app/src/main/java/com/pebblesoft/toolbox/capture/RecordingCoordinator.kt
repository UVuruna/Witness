package com.pebblesoft.toolbox.capture

import android.content.Context
import android.os.ParcelFileDescriptor
import com.pebblesoft.toolbox.app
import com.pebblesoft.toolbox.data.CallRecord
import com.pebblesoft.toolbox.data.Direction
import com.pebblesoft.toolbox.data.Quality
import com.pebblesoft.toolbox.data.TranscriptState
import com.pebblesoft.toolbox.capture.shizuku.ShizukuManager
import java.io.File

/**
 * The conductor: it decides, records, seals, and files — in that order.
 *
 * One call in, one row out. When a call it should record ends, the WAV the
 * privileged recorder wrote is sealed into the vault and indexed; when a call
 * it should skip ends, nothing is kept and nothing is logged (scenario 2).
 *
 * It owns no privilege of its own: whether to record is [com.pebblesoft.toolbox.rules.RecordingPolicy]'s
 * answer, and the actual capture is the shell-side recorder reached through
 * [ShizukuManager]. This class only sequences them and turns the result into a
 * [CallRecord] carrying an honest [Quality].
 */
class RecordingCoordinator(
    private val context: Context,
    private val shizuku: ShizukuManager,
) {
    private var activeNumber: String? = null
    private var activeDirection: Direction = Direction.INCOMING
    private var startedAt: Long = 0
    private var sink: ParcelFileDescriptor? = null
    private var plaintext: File? = null
    private var recording = false

    /** VOICE_CALL — the source that, as shell, carries both legs of the call. */
    private val callSource = android.media.MediaRecorder.AudioSource.VOICE_CALL

    /**
     * A call has begun. Consult the lists; if this one is recorded and the
     * privileged recorder is ready, open a plaintext sink in the cache and
     * start capture. A failure here is reported through the resulting record's
     * quality, never as a crash on the telephony thread.
     */
    suspend fun onCallStarted(number: String?, direction: Direction, isInContacts: Boolean) {
        if (recording) return
        val decision = context.app.policy.decide(number, isInContacts)
        if (!decision.record) return

        activeNumber = number.orEmpty()
        activeDirection = direction
        startedAt = System.currentTimeMillis()

        val recorder = shizuku.recorder
        if (recorder == null || !shizuku.isReady()) {
            // We were asked to record and cannot — that is a FAILED row, written
            // at call end so the user sees the app tried and was blocked, never
            // a silent gap. Held here; committed in onCallEnded.
            recording = true
            return
        }

        val file = File(context.cacheDir, "cap-${startedAt}.wav")
        val pfd = ParcelFileDescriptor.open(
            file, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE
        )
        val error = runCatching { recorder.start(callSource, pfd) }.getOrElse { it.message ?: "bind lost" }
        sink = pfd
        plaintext = file
        recording = true
        if (error.isNotEmpty()) {
            // start refused — keep the state so onCallEnded files a FAILED row.
            plaintext = null
        }
    }

    /**
     * The call is over. Stop capture, judge what we got, seal it into the vault
     * and index it. Peak amplitude decides BOTH_VOICES vs FAILED at this stage;
     * the owner's ear (or, later, the transcript) can only refine it, never
     * fake it.
     */
    suspend fun onCallEnded() {
        if (!recording) return
        recording = false

        val recorder = shizuku.recorder
        val peak = if (recorder != null && plaintext != null) {
            runCatching { recorder.stop() }.getOrDefault(0)
        } else 0
        runCatching { sink?.close() }
        sink = null

        val number = activeNumber.orEmpty()
        val duration = System.currentTimeMillis() - startedAt
        val source = plaintext

        if (source == null || !source.exists() || source.length() <= 44L) {
            fileRecord(number, duration, bytes = 0, seal = null, quality = Quality.FAILED, file = "")
            source?.delete()
            plaintext = null
            return
        }

        // Seal the plaintext into the vault, then wipe the plaintext copy.
        val vault = context.app.vault
        val name = "rec-${startedAt}.wav"
        vault.openWrite(name).use { out -> source.inputStream().use { it.copyTo(out) } }
        val bytes = vault.sizeOf(name)
        val seal = vault.seal(name)
        source.delete()
        plaintext = null

        val quality = if (peak > SILENCE_PEAK) Quality.BOTH_VOICES else Quality.FAILED
        fileRecord(number, duration, bytes, seal.sha256, quality, name)
    }

    private suspend fun fileRecord(
        number: String, duration: Long, bytes: Long,
        seal: String?, quality: Quality, file: String,
    ) {
        val record = CallRecord(
            number = number,
            contactName = null,
            direction = activeDirection,
            startedAt = startedAt,
            durationMs = duration,
            audioFile = file,
            audioBytes = bytes,
            sha256 = seal.orEmpty(),
            sealedAt = System.currentTimeMillis(),
            quality = quality,
            capturedBy = "shizuku-voicecall",
            transcriptState = if (quality == Quality.BOTH_VOICES)
                TranscriptState.PENDING else TranscriptState.NONE,
        )
        context.app.db.records().insert(record)
    }

    private companion object {
        // Below this 16-bit peak the buffer is effectively silence — no usable
        // conversation arrived, so the file is not evidence.
        const val SILENCE_PEAK = 150
    }
}
