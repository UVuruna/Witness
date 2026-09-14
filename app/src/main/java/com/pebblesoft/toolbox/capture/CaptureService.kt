package com.pebblesoft.toolbox.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.pebblesoft.toolbox.R
import com.pebblesoft.toolbox.app
import com.pebblesoft.toolbox.permissions.RuntimePermissions
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * The always-on ear that must stay alive between calls.
 *
 * Watching call state is cheap, but Android only keeps a background process
 * around reliably if it is a foreground service — so this is one, with the
 * `microphone` type Android requires of anything that will open an audio source.
 * Its notification wears the neutral identity and never says "recording",
 * because it runs whether or not the current call is one the lists record.
 *
 * **The microphone type is a claim the system audits.** From Android 14 a
 * service that declares it without `RECORD_AUDIO` actually granted is killed
 * with a `SecurityException` the moment it calls `startForeground`. The previous
 * round declared it unconditionally in `onCreate`, so the ear died on every
 * phone within milliseconds of being asked to listen — and nothing reported it.
 * Here the permission is checked first, the service refuses to pretend, and
 * [problem] carries the reason to the screen that can fix it.
 */
class CaptureService : Service() {

    /**
     * Nothing thrown on the recording path may take the process down with it.
     *
     * The coordinator already files a row rather than letting a failure vanish;
     * this is the outer net for everything else that runs here, because a
     * crashed process during a call is the one failure the user can neither see
     * nor recover from.
     */
    private val crashes = CoroutineExceptionHandler { _, thrown ->
        Log.e(TAG, "capture coroutine failed", thrown)
        problem.value = getString(R.string.service_problem_start)
    }

    private val scope = CoroutineScope(SupervisorJob() + crashes)
    private var watcher: CallWatcher? = null
    private var voip: VoipWatcher? = null

    override fun onCreate() {
        super.onCreate()

        val missing = RuntimePermissions.missingRequired(this)
        if (missing.isNotEmpty()) {
            problem.value = getString(R.string.service_problem_permissions)
            running.value = false
            stopSelf()
            return
        }

        try {
            startInForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: SecurityException) {
            problem.value = getString(R.string.service_problem_permissions)
            running.value = false
            stopSelf()
            return
        } catch (e: IllegalStateException) {
            problem.value = getString(R.string.service_problem_start)
            running.value = false
            stopSelf()
            return
        }

        // The conductor belongs to the app, not to this service: the setup
        // screen watches its test-call state while the service is being started,
        // and a restart of the service must not lose an armed test.
        val coordinator = app.coordinator

        val calls = CallWatcher(this, coordinator, scope)
        val callFailure = calls.start()
        watcher = calls

        val other = VoipWatcher(this, coordinator, scope)
        val voipFailure = other.start()
        voip = other

        // Telephony is the ear that matters; a VoIP watcher that could not
        // register is reported but does not make the service a failure.
        problem.value = callFailure.ifEmpty { voipFailure }
        running.value = callFailure.isEmpty()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        watcher?.stop()
        voip?.stop()
        watcher = null
        voip = null
        running.value = false

        // The conductor outlives this service, so a session left open here stays
        // open: a worker thread would keep reading the room with the loudspeaker
        // on, the WAV would never be finalised, and every later call would be
        // dropped because a session already exists. Teardown has to close it,
        // and it is worth blocking a moment for — the alternative is a recording
        // nobody can hear and a phone that silently stops protecting her.
        runCatching {
            runBlocking { withTimeout(TEARDOWN_MS) { app.coordinator.abandon() } }
        }.onFailure { Log.e(TAG, "closing the open session on shutdown failed", it) }

        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun startInForeground(id: Int, notification: Notification) {
        startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    companion object {
        private const val CHANNEL_ID = "capture"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "Capture"

        /** How long teardown may block to close an open recording cleanly. */
        private const val TEARDOWN_MS = 3_000L

        private val _problem = MutableStateFlow("")
        private val _running = MutableStateFlow(false)

        /** Empty while the ear is listening; otherwise why it is not, in her words. */
        val problem: MutableStateFlow<String> get() = _problem

        /**
         * Whether the ear is listening. Folded into the view model's liveness
         * sources, so a dead ear is never drawn as a live one.
         */
        val running: MutableStateFlow<Boolean> get() = _running

        /**
         * Start the ear. Safe to call repeatedly — a running service ignores it.
         *
         * @return "" when the service was asked to start, otherwise why it was not.
         */
        fun ensureRunning(context: Context): String {
            val missing = RuntimePermissions.missingRequired(context)
            if (missing.isNotEmpty()) {
                _problem.value = context.getString(R.string.service_problem_permissions)
                return _problem.value
            }
            val intent = Intent(context, CaptureService::class.java)
            return try {
                context.startForegroundService(intent)
                ""
            } catch (e: IllegalStateException) {
                _problem.value = context.getString(R.string.service_problem_start)
                _problem.value
            }
        }
    }
}
