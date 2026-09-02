package com.uvuruna.callprobe.audio

import android.content.Context
import android.media.MediaRecorder
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One capture source the probe can try, and whether the platform reserves it
 *  for privileged callers. A privileged source is EXPECTED to be refused for
 *  an ordinary app — the probe tries it anyway, because "expected" is not a
 *  measurement. */
data class ProbeSource(
    val name: String,
    val id: Int,
    val privileged: Boolean,
)

/** What the OWNER heard when playing the recording back. Peak and silence
 *  ratio can prove that SOMETHING was captured; only a human ear can say
 *  WHOSE voice it was, and that is the whole M0 question. */
enum class Heard(val label: String) {
    UNJUDGED("not judged yet"),
    BOTH("BOTH voices"),
    ME("only ME"),
    THEM("only the OTHER side"),
    NOTHING("nothing audible"),
}

/** One finished recording: the WAV file plus the probe stats measured while
 *  it was captured (read back from the .json sidecar). */
data class RecordingEntry(
    val wav: File,
    val name: String,
    val kind: String,          // "call" or "manual"
    val sourceName: String,    // AudioSource used
    val route: String,         // "speaker" or "earpiece"
    val durationMs: Long,
    val sizeBytes: Long,
    val peak: Int,             // 0..32767
    val silentRatio: Double,   // 0.0..1.0
    val error: String?,
    val heard: Heard,
)

/**
 * App-private storage of probe recordings: a WAV per call under
 * `filesDir/recordings`, each with a `.json` stats sidecar. Nothing ever
 * touches shared storage — the
 * INSPECTION TEST applies to the probe too.
 */
object RecordingStore {

    /**
     * Every source worth trying during a call, ordinary ones first.
     *
     * The privileged four are the ones that actually carry telephony audio;
     * the platform gates them behind CAPTURE_AUDIO_OUTPUT, which no ordinary
     * app holds. They are on the list so the refusal is RECORDED, per device,
     * with the exact error — and so the same list can be re-run through a
     * privileged (shell-identity) bridge and compared row for row.
     */
    val SOURCES: List<ProbeSource> = listOf(
        ProbeSource("MIC", MediaRecorder.AudioSource.MIC, false),
        ProbeSource("VOICE_RECOGNITION", MediaRecorder.AudioSource.VOICE_RECOGNITION, false),
        ProbeSource("VOICE_COMMUNICATION", MediaRecorder.AudioSource.VOICE_COMMUNICATION, false),
        ProbeSource("UNPROCESSED", MediaRecorder.AudioSource.UNPROCESSED, false),
        ProbeSource("CAMCORDER", MediaRecorder.AudioSource.CAMCORDER, false),
        ProbeSource("VOICE_CALL", MediaRecorder.AudioSource.VOICE_CALL, true),
        ProbeSource("VOICE_UPLINK", MediaRecorder.AudioSource.VOICE_UPLINK, true),
        ProbeSource("VOICE_DOWNLINK", MediaRecorder.AudioSource.VOICE_DOWNLINK, true),
        // REMOTE_SUBMIX is @SystemApi — not a public constant, so it is named
        // by its stable platform value (AudioSource.REMOTE_SUBMIX = 8).
        ProbeSource("REMOTE_SUBMIX", 8, true),
    )

    private val STAMP = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    private fun dir(context: Context): File =
        File(context.filesDir, "recordings").apply { mkdirs() }

    fun newFile(context: Context, kind: String): File =
        File(dir(context), "${STAMP.format(Date())}-$kind.wav")

    fun sourceName(source: Int): String =
        SOURCES.firstOrNull { it.id == source }?.name ?: "SOURCE_$source"

    /** Writes the stats sidecar for a recording the given recorder just made. */
    fun writeStats(wav: File, kind: String, source: Int, route: String, rec: WavRecorder) {
        val json = JSONObject()
            .put("kind", kind)
            .put("source", sourceName(source))
            .put("route", route)
            .put("durationMs", rec.durationMs)
            .put("peak", rec.peak)
            .put("silentRatio", rec.silentRatio)
            .put("heard", Heard.UNJUDGED.name)
            .putOpt("error", rec.error)
        sidecar(wav).writeText(json.toString(2))
    }

    /** Records the owner's ear verdict for one recording — the answer no
     *  amplitude measurement can give. */
    fun writeHeard(entry: RecordingEntry, heard: Heard) {
        val file = sidecar(entry.wav)
        val json = runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
        file.writeText(json.put("heard", heard.name).toString(2))
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
                    route = stats.optString("route", "?"),
                    durationMs = stats.optLong("durationMs", 0),
                    sizeBytes = wav.length(),
                    peak = stats.optInt("peak", 0),
                    silentRatio = stats.optDouble("silentRatio", 0.0),
                    error = stats.optString("error", "").ifEmpty { null },
                    heard = runCatching { Heard.valueOf(stats.optString("heard", "UNJUDGED")) }
                        .getOrElse { Heard.UNJUDGED },
                )
            } ?: emptyList()

    fun delete(entry: RecordingEntry) {
        entry.wav.delete()
        sidecar(entry.wav).delete()
    }

    private fun sidecar(wav: File): File =
        File(wav.parentFile, wav.nameWithoutExtension + ".json")
}
