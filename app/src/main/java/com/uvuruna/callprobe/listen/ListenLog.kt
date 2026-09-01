package com.uvuruna.callprobe.listen

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Append-only JSONL log of one continuous-listening session and the summary
 * computed from it. Three event kinds: "start", "beat" (one per minute),
 * "stop". The summary answers the M0.5 questions from the log alone:
 * battery drain per hour, and heartbeat gaps = moments the OS suspended or
 * killed the listener.
 */
object ListenLog {

    private const val FILE_NAME = "listen_log.jsonl"

    /** A beat this much later than the previous one means the OS paused us. */
    private const val GAP_MS = 90_000L

    data class Summary(
        val durationMin: Long,
        val heartbeats: Int,
        val gaps: Int,
        val batteryStart: Int,
        val batteryEnd: Int,
        val drainPerHour: Double?,   // null while the session is too short
        val charged: Boolean,        // charger seen during session → drain invalid
        val loudEvents: Int,
    )

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun append(context: Context, event: String, fields: Map<String, Any>) {
        val obj = JSONObject()
        obj.put("e", event)
        obj.put("t", System.currentTimeMillis())
        fields.forEach { (k, v) -> obj.put(k, v) }
        file(context).appendText(obj.toString() + "\n")
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    /** Summary of the LAST session in the log, or null when there is none. */
    fun summary(context: Context): Summary? {
        val f = file(context)
        if (!f.exists()) return null
        val lines = f.readLines().mapNotNull {
            try { JSONObject(it) } catch (_: Exception) { null }
        }
        val startIdx = lines.indexOfLast { it.optString("e") == "start" }
        if (startIdx < 0) return null
        val session = lines.subList(startIdx, lines.size)

        val start = session.first()
        val last = session.last()
        val batteryStart = start.optInt("battery", -1)
        val batteryEnd = last.optInt("battery", batteryStart)
        val durationMs = last.optLong("t") - start.optLong("t")
        val beats = session.filter { it.optString("e") == "beat" }

        var gaps = 0
        var prevT = start.optLong("t")
        for (b in beats) {
            if (b.optLong("t") - prevT > GAP_MS) gaps++
            prevT = b.optLong("t")
        }

        val charged = session.any { it.optBoolean("charging", false) }
        val hours = durationMs / 3_600_000.0
        val drain = if (!charged && hours >= 0.5 && batteryStart >= 0) {
            (batteryStart - batteryEnd) / hours
        } else null

        return Summary(
            durationMin = durationMs / 60_000,
            heartbeats = beats.size,
            gaps = gaps,
            batteryStart = batteryStart,
            batteryEnd = batteryEnd,
            drainPerHour = drain,
            charged = charged,
            loudEvents = (beats.lastOrNull() ?: last).optInt("loud", 0),
        )
    }
}
