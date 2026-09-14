package com.pebblesoft.toolbox.capture

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.pebblesoft.toolbox.permissions.AppPermission
import com.pebblesoft.toolbox.permissions.RuntimePermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Notices a conversation happening inside another app.
 *
 * WhatsApp, Viber, Messenger and Signal are invisible to call-state listening —
 * Android's own reference says the call-state stream considers telephony calls
 * only — so the app that watches only telephony believes nothing is happening
 * while the user is being threatened. That silence was the most dangerous thing
 * in the previous build: not a missing feature, a false sense of cover.
 *
 * What every one of those apps DOES do is put the phone's audio system into
 * communication mode. That is observable, costs no permission at all, and is the
 * same signal on every handset. It does not say which app, and deliberately so:
 * naming the app needs notification access, a switch that stands out to anyone
 * inspecting the phone, and THE INSPECTION TEST is worth more than an app name.
 *
 * Cellular calls also move the audio mode, so the telephony state is consulted
 * before reacting — otherwise an ordinary call would be filed twice.
 */
class VoipWatcher(
    private val context: Context,
    private val coordinator: RecordingCoordinator,
    private val scope: CoroutineScope,
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var listener: AudioManager.OnModeChangedListener? = null
    private var poller: Job? = null
    private var inVoipCall = false

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val modeListener = AudioManager.OnModeChangedListener { mode -> onMode(mode) }
            listener = modeListener
            audio.addOnModeChangedListener(ContextCompat.getMainExecutor(context), modeListener)
        } else {
            poller = scope.launch {
                while (isActive) {
                    onMode(audio.mode)
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    fun stop() {
        listener?.let { active -> audio.removeOnModeChangedListener(active) }
        listener = null
        poller?.cancel()
        poller = null
    }

    private fun onMode(mode: Int) {
        val voipNow = mode == AudioManager.MODE_IN_COMMUNICATION && !onACellularCall()
        if (voipNow == inVoipCall) return
        inVoipCall = voipNow
        scope.launch {
            if (voipNow) coordinator.onVoipStarted() else coordinator.onCallEnded()
        }
    }

    /**
     * A cellular call belongs to [CallWatcher]. Without the permission to ask,
     * the safer assumption is that telephony is busy — a missed row beats two
     * rows for one conversation and a recorder started twice.
     */
    private fun onACellularCall(): Boolean {
        if (!RuntimePermissions.isGranted(context, AppPermission.CALL_STATE)) return true
        @Suppress("DEPRECATION")
        return runCatching { telephony.callState != TelephonyManager.CALL_STATE_IDLE }.getOrDefault(true)
    }

    private companion object {
        /** Below Android 12 there is no mode callback; two seconds is a quiet poll. */
        const val POLL_INTERVAL_MS = 2_000L
    }
}
