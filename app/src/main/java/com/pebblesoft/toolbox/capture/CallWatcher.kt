package com.pebblesoft.toolbox.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.pebblesoft.toolbox.permissions.AppPermission
import com.pebblesoft.toolbox.permissions.RuntimePermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Notices a call starting and ending, and nothing else.
 *
 * It is the eyes, not the hands: on OFFHOOK it tells the coordinator a call
 * began, on IDLE that it ended. Two platform APIs do the same job either side of
 * Android 12 — the modern `TelephonyCallback` and the deprecated
 * `PhoneStateListener` — wrapped here so the rest of the app never sees the seam
 * (ONE KIND, ONE CLASS: one watcher, two backends).
 *
 * **Registering is not free.** On Android 12 and later `registerTelephonyCallback`
 * throws `SecurityException` unless `READ_PHONE_STATE` has actually been granted,
 * and the previous round called it unconditionally inside a service's `onCreate`.
 * The service died on the spot, every time, on every phone. So [start] checks
 * first and RETURNS the reason instead of throwing into a lifecycle callback
 * nobody is catching.
 *
 * The number no longer comes from the listener — Android 12 stopped supplying it.
 * The broadcast still carries it for a ringing call when the app may read the
 * call log, and everything else is resolved afterwards by [CallIdentity].
 */
class CallWatcher(
    private val context: Context,
    private val coordinator: RecordingCoordinator,
    private val scope: CoroutineScope,
) {
    private val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var ringingNumber: String? = null
    private var inCall = false

    private var callback: TelephonyCallback? = null
    private var legacy: PhoneStateListener? = null
    private var numberReceiver: BroadcastReceiver? = null

    /** @return "" when the watcher is listening, otherwise why it is not. */
    fun start(): String {
        if (!RuntimePermissions.isGranted(context, AppPermission.CALL_STATE)) {
            return "call state permission not granted"
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) startModern() else startLegacy()
            listenForRingingNumber()
            ""
        } catch (e: SecurityException) {
            stop()
            "the system refused the call-state listener: ${e.message}"
        }
    }

    fun stop() {
        callback?.let { active ->
            runCatching { telephony.unregisterTelephonyCallback(active) }
        }
        @Suppress("DEPRECATION")
        legacy?.let { active -> runCatching { telephony.listen(active, PhoneStateListener.LISTEN_NONE) } }
        numberReceiver?.let { receiver -> runCatching { context.unregisterReceiver(receiver) } }
        callback = null
        legacy = null
        numberReceiver = null
    }

    private fun onState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> if (!inCall) {
                inCall = true
                val hint = ringingNumber
                scope.launch(Dispatchers.IO) { coordinator.onCallStarted(hint) }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (inCall) {
                    inCall = false
                    scope.launch(Dispatchers.IO) { coordinator.onCallEnded() }
                }
                ringingNumber = null
            }
        }
    }

    private fun startModern() {
        val modern = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) = onState(state)
        }
        callback = modern
        telephony.registerTelephonyCallback(ContextCompat.getMainExecutor(context), modern)
    }

    @Suppress("DEPRECATION")
    private fun startLegacy() {
        val old = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                if (!phoneNumber.isNullOrBlank()) ringingNumber = phoneNumber
                onState(state)
            }
        }
        legacy = old
        telephony.listen(old, PhoneStateListener.LISTEN_CALL_STATE)
    }

    /**
     * The one place the caller's number still arrives while the phone is ringing
     * — and only for an app allowed to read the call log. Without it the lists
     * can still be applied, but only after the call, from [CallIdentity].
     */
    private fun listenForRingingNumber() {
        if (!CallIdentity.canRead(context)) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(received: Context?, intent: Intent?) {
                @Suppress("DEPRECATION")
                val number = intent?.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (!number.isNullOrBlank()) ringingNumber = number
            }
        }
        numberReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_PHONE_STATE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private companion object {
        const val ACTION_PHONE_STATE = "android.intent.action.PHONE_STATE"
    }
}
