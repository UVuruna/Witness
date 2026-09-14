package com.pebblesoft.toolbox.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.pebblesoft.toolbox.data.Direction
import kotlinx.coroutines.delay

/**
 * Who was on the other end, and who called whom.
 *
 * Android 12 removed the caller's number from the modern call-state callback:
 * `TelephonyCallback.CallStateListener` is handed a state and nothing else. The
 * previous round kept the old code shape around that new API, so `lastNumber`
 * stayed null forever — which meant every call was filed as OUTGOING, no
 * contact was ever matched, and no whitelist or blacklist rule could fire on
 * any phone newer than Android 11. The lists worked perfectly and were never
 * consulted with anything to match.
 *
 * The call log is where the platform still tells the truth, and it also carries
 * the direction, which no listener ever did. The catch is timing: the row is
 * written when the call ENDS, sometimes a moment after. So this reads with a
 * short retry rather than once, and says plainly when it found nothing instead
 * of inventing a direction.
 */
object CallIdentity {

    data class Party(
        val number: String,
        val name: String?,
        val direction: Direction,
    )

    /**
     * The call that was in progress at [startedAt], read back from the log.
     *
     * Returns null when the permission is missing or the platform never wrote a
     * row — a withheld number and a Wi-Fi call both land here.
     */
    suspend fun resolve(context: Context, startedAt: Long): Party? {
        if (!canRead(context)) return null
        repeat(ATTEMPTS) { attempt ->
            readLatest(context, startedAt)?.let { return it }
            delay(RETRY_DELAY_MS * (attempt + 1))
        }
        return readLatest(context, startedAt)
    }

    fun canRead(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    fun isInContacts(context: Context, number: String?): Boolean = contactName(context, number) != null

    /** The contact's name for [number], or null when it is unknown or unreadable. */
    fun contactName(context: Context, number: String?): String? {
        if (number.isNullOrBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(number)
            .build()
        return runCatching {
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun readLatest(context: Context, startedAt: Long): Party? = runCatching {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.CACHED_NAME),
            null,
            null,
            "${CallLog.Calls.DATE} DESC LIMIT 1",
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val loggedAt = cursor.getLong(2)
            if (loggedAt + LOG_SLACK_MS < startedAt) return@use null
            Party(
                number = cursor.getString(0).orEmpty(),
                name = cursor.getString(3),
                direction = when (cursor.getInt(1)) {
                    CallLog.Calls.OUTGOING_TYPE -> Direction.OUTGOING
                    CallLog.Calls.INCOMING_TYPE,
                    CallLog.Calls.MISSED_TYPE,
                    CallLog.Calls.REJECTED_TYPE -> Direction.INCOMING
                    else -> Direction.UNKNOWN
                },
            )
        }
    }.getOrNull()

    /** The log row can lag the end of the call; four tries cover the gap seen in practice. */
    private const val ATTEMPTS = 4
    private const val RETRY_DELAY_MS = 300L

    /** A row stamped slightly before our start still belongs to this call. */
    private const val LOG_SLACK_MS = 10_000L
}
