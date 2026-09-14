package com.pebblesoft.toolbox.capture

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Notices a conversation happening inside another app.
 *
 * Calls made inside other apps are invisible to call-state listening — Android's
 * own reference says the call-state stream considers telephony calls only — so
 * an app that watches only telephony believes nothing is happening while its
 * user is being threatened. That silence was the most dangerous thing in the
 * previous build: not a missing feature, a false sense of cover.
 *
 * **This watcher knows nothing about apps, and that is the design.** It holds no
 * package list to keep up to date; the whole test is that the phone's audio
 * system is in communication mode while telephony is idle. WhatsApp, Viber,
 * Messenger, Signal, Telegram, Teams, Meet and anything written next all set
 * that mode, so all of them are covered by the same line — and none of them is
 * named. Naming the app would need notification access, a switch that stands out
 * to anyone inspecting the phone, and THE INSPECTION TEST is worth more.
 *
 * The cost of being generic is that the signal is broader than "a call": a
 * headset connecting or an assistant turn also raises the mode. The coordinator
 * refuses to file anything under [RecordingCoordinator.VOIP_FLOOR_MS], which is
 * what keeps her list free of conversations that never happened.
 *
 * **Not measured.** That every one of those apps really raises this mode on a
 * real handset is a platform expectation, not something this project has
 * observed. Until it is, the feature is written down as unproven.
 */
class VoipWatcher(
    private val context: Context,
    private val coordinator: RecordingCoordinator,
    private val scope: CoroutineScope,
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var listener: AudioManager.OnModeChangedListener? = null
    private var poller: Job? = null
    private var inVoipCall = false

    /** @return "" when the watcher is listening, otherwise why it is not. */
    fun start(): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val modeListener = AudioManager.OnModeChangedListener { mode -> onMode(mode) }
            listener = modeListener
            audio.addOnModeChangedListener(ContextCompat.getMainExecutor(context), modeListener)
            // The callback only ever reports a CHANGE. A conversation already in
            // progress when the service starts — after a reboot, after a sticky
            // restart, or the moment permissions are granted — would otherwise
            // never be noticed at all.
            onMode(audio.mode)
        } else {
            poller = scope.launch {
                while (isActive) {
                    onMode(audio.mode)
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
        ""
    } catch (e: SecurityException) {
        // Thrown bare inside a service's onCreate, this is the same failure that
        // killed the ear on every phone last round, one class over.
        stop()
        "the system refused the audio-mode listener: ${e.message}"
    } catch (e: IllegalStateException) {
        stop()
        "the audio-mode listener could not be registered: ${e.message}"
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
            if (voipNow) coordinator.onVoipStarted() else coordinator.onVoipEnded()
        }
    }

    /**
     * A cellular call belongs to [CallWatcher], and the app's own eyes are the
     * only trustworthy way to know.
     *
     * The obvious check — `TelephonyManager.getCallState()` — is served by
     * Telecom, which on modern Android also reports the self-managed connections
     * that WhatsApp, Signal, Telegram and Teams register so their calls appear
     * in the system call UI. Gating on it could therefore go permanently silent
     * for precisely the apps this watcher exists for, with nothing to show that
     * it had. [RecordingCoordinator.carrierCallInProgress] is set by the
     * telephony watcher itself and cannot be confused that way.
     */
    private fun onACellularCall(): Boolean = coordinator.carrierCallInProgress

    private companion object {
        /** Below Android 12 there is no mode callback; two seconds is a quiet poll. */
        const val POLL_INTERVAL_MS = 2_000L
    }
}
