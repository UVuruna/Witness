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
import com.pebblesoft.toolbox.R
import com.pebblesoft.toolbox.capture.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The always-on ear that must stay alive between calls.
 *
 * Watching call state is cheap, but Android only keeps a background process
 * around reliably if it is a foreground service — so this is one, with the
 * `microphone` type Android requires for anything that will open an audio
 * source during a call. Its notification wears the neutral identity; it never
 * says "recording", because it is running whether or not the current call is
 * one the lists record.
 *
 * It owns the [CallWatcher] and, through it, the [RecordingCoordinator]. It does
 * not itself hold the privilege — that lives in the Shizuku process — it only
 * guarantees something is listening for the call that will use it.
 */
class CaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private lateinit var watcher: CallWatcher

    override fun onCreate() {
        super.onCreate()
        startInForeground(NOTIFICATION_ID, buildNotification())
        val shizuku = ShizukuManager(this).also { it.onStart() }
        val coordinator = RecordingCoordinator(this, shizuku)
        watcher = CallWatcher(this, coordinator, scope).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        watcher.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply { setShowBadge(false) }
            manager.createNotificationChannel(channel)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun startInForeground(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(id, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "capture"
        private const val NOTIFICATION_ID = 1

        /** Start the ear. Safe to call repeatedly — a running service ignores it. */
        fun ensureRunning(context: Context) {
            val intent = Intent(context, CaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
