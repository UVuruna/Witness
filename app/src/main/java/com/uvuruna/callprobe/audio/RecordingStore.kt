package com.uvuruna.callprobe.audio

import android.content.Context
import android.media.MediaRecorder
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One finished recording: the WAV file plus the probe stats measured while
 *  it was captured (read back from the .json sidecar). */
data class RecordingEntry(
    val wav: File,
    val name: String,
    val kind: String,          // "call" or "manual"
    val sourceName: String,    // AudioSource used
    val durationMs: Long,
    val sizeBytes: Long,
    val peak: Int,             // 0..32767
    val silentRatio: Double,   // 0.0..1.0
    val error: String?,
)

/**
 * App-private storage of probe recordings: a WAV per call under
 * `filesDir/recordings`, each with a `.json` stats sidecar. Nothing ever
 * touches shared storage — the
 * INSPECTION TEST applies to the probe too.
 */
object RecordingStore {

    val SOURCES: List<Pair<String, Int>> = listOf(
        "MIC" to MediaRecorder.AudioSource.MIC,
        "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        "UNPROCESSED" to MediaRecorder.AudioSource.UNPROCESSED,
        "CAMCORDER" to MediaRecorder.AudioSource.CAMCORDER,
    )

    private val STAMP = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    private fun dir(context: Context): File =
        File(context.filesDir, "recordings").apply { mkdirs() }

    fun newFile(context: Context, kind: String): File =
        File(dir(context), "${STAMP.format(Date())}-$kind.wav")

    fun sourceName(source: Int): String =
        SOURCES.firstOrNull { it.second == source }?.first ?: "SOURCE_$source"

    /** Writes the stats sidecar for a recording the given recorder just made. */
    fun writeStats(wav: File, kind: String, source: Int, rec: WavRecorder) {
        val json = JSONObject()
            .put("kind", kind)
            .put("source", sourceName(source))
            .put("durationMs", rec.durationMs)
            .put("peak", rec.peak)
            .put("silentRatio", rec.silentRatio)
            .putOpt("error", rec.error)
        sidecar(wav).writeText(json.toString(2))
    }

    fun list(context: Context): List<RecordingEntry> =
        dir(context).listFiles { f -> f.extension == "wav" }
            ?.sortedByDescending { it.name }
            ?.map { wav ->
                val stats = runCatching { JSONObject(sidecar(wav).readText()) }
                    .getOrElse { JSONObject() }
                RecordingEntry(
                    wav = wav,
                    name = wav.nameWithoutExtension,
                    kind = stats.optString("kind", "?"),
                    sourceName = stats.optString("source", "?"),
                    durationMs = stats.optLong("durationMs", 0),
                    sizeBytes = wav.length(),
                    peak = stats.optInt("peak", 0),
                    silentRatio = stats.optDouble("silentRatio", 0.0),
                    error = stats.optString("error", "").ifEmpty { null },
                )
            } ?: emptyList()

    fun delete(entry: RecordingEntry) {
        entry.wav.delete()
        sidecar(entry.wav).delete()
    }

    private fun sidecar(wav: File): File =
        File(wav.parentFile, wav.nameWithoutExtension + ".json")
}
