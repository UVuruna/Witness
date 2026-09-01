package com.uvuruna.callprobe.call

import android.content.Context
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission

/**
 * Wraps the phone-state callback so the probe can auto-start a recording when
 * a call goes OFF-HOOK and stop it when the call ends. Uses the modern
 * TelephonyCallback on API 31+, the legacy PhoneStateListener below it —
 * min SDK is 29, so both paths are real.
 *
 * The probe uses this ONLY in "auto" mode; manual recording never touches it.
 */
class CallMonitor(
    private val context: Context,
    private val onCallStart: () -> Unit,
    private val onCallEnd: () -> Unit,
) {
    private val telephony =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var offHook = false
    private var legacy: Any? = null
    private var modern: Any? = null

    @RequiresPermission(android.Manifest.permission.READ_PHONE_STATE)
    fun start() {
        if (android.os.Build.VERSION.SDK_INT >= 31) startModern() else startLegacy()
    }

    fun stop() {
        (modern as? android.telephony.TelephonyCallback)?.let {
            if (android.os.Build.VERSION.SDK_INT >= 31) telephony.unregisterTelephonyCallback(it)
        }
        @Suppress("DEPRECATION")
        (legacy as? android.telephony.PhoneStateListener)?.let {
            telephony.listen(it, android.telephony.PhoneStateListener.LISTEN_NONE)
        }
        modern = null
        legacy = null
    }

    private fun onState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK ->
                if (!offHook) { offHook = true; onCallStart() }
            TelephonyManager.CALL_STATE_IDLE ->
                if (offHook) { offHook = false; onCallEnd() }
        }
    }

    @RequiresPermission(android.Manifest.permission.READ_PHONE_STATE)
    private fun startModern() {
        val cb = object : android.telephony.TelephonyCallback(),
            android.telephony.TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) = onState(state)
        }
        modern = cb
        telephony.registerTelephonyCallback(context.mainExecutor, cb)
    }

    @Suppress("DEPRECATION")
    private fun startLegacy() {
        val listener = object : android.telephony.PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) = onState(state)
        }
        legacy = listener
        telephony.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
    }
}
