package com.pebblesoft.toolbox.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.pebblesoft.toolbox.data.Direction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Notices a call starting and ending, and nothing else.
 *
 * It is the eyes, not the hands: on OFFHOOK it tells the coordinator a call
 * began, on IDLE that it ended. It carries the last incoming number so the
 * coordinator can apply the lists. Two platform APIs do the same job either
 * side of Android 12 — the modern `TelephonyCallback` and the deprecated
 * `PhoneStateListener` — wrapped here so the rest of the app never sees the
 * seam (ONE KIND, ONE CLASS: one watcher, two backends).
 */
class CallWatcher(
    private val context: Context,
    private val coordinator: RecordingCoordinator,
    private val scope: CoroutineScope,
) {
    private val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var lastNumber: String? = null
    private var inCall = false

    private var callback: TelephonyCallback? = null
    private var legacy: PhoneStateListener? = null

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) startModern() else startLegacy()
    }

    fun stop() {
        callback?.let { telephony.unregisterTelephonyCallback(it) }
        @Suppress("DEPRECATION")
        legacy?.let { telephony.listen(it, PhoneStateListener.LISTEN_NONE) }
        callback = null
        legacy = null
    }

    private fun onState(state: Int, incomingNumber: String?) {
        if (!incomingNumber.isNullOrBlank()) lastNumber = incomingNumber
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                lastNumber = incomingNumber ?: lastNumber
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!inCall) {
                    inCall = true
                    val number = lastNumber
                    val direction = if (number.isNullOrBlank()) Direction.OUTGOING else Direction.INCOMING
                    scope.launch(Dispatchers.IO) {
                        coordinator.onCallStarted(number, direction, isInContacts(number))
                    }
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (inCall) {
                    inCall = false
                    scope.launch(Dispatchers.IO) { coordinator.onCallEnded() }
                }
                lastNumber = null
            }
        }
    }

    private fun startModern() {
        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) = onState(state, null)
        }
        callback = cb
        ContextCompat.getMainExecutor(context).let { telephony.registerTelephonyCallback(it, cb) }
    }

    @Suppress("DEPRECATION")
    private fun startLegacy() {
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) = onState(state, phoneNumber)
        }
        legacy = listener
        telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun isInContacts(number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        val uri = android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon().appendPath(number).build()
        return runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.BaseColumns._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }
}
