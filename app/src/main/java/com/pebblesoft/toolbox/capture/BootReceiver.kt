package com.pebblesoft.toolbox.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pebblesoft.toolbox.MainActivity
import com.pebblesoft.toolbox.R

/**
 * Brings the ear back after a reboot — and says so honestly when it cannot.
 *
 * A restart tears down the foreground service, so without this the phone would
 * quietly stop watching for calls until the user next opened the app.
 *
 * **What this cannot do, and why it must not pretend.** Since Android 12 a
 * foreground service started from the background is restricted, and since
 * Android 14 a `BOOT_COMPLETED` receiver may not start one of the `microphone`
 * type at all — which is exactly the type the ear needs. On those versions the
 * start throws and the service stops itself. The previous shape called
 * `ensureRunning` and considered the job done, so after every restart the app
 * was deaf and the only sign of it was a string nobody reads unless they open
 * the app. Shizuku has to be restarted by hand after a reboot anyway, so the
 * honest move is the same in both cases: put ONE notification in front of her
 * that opens the app at the button that fixes it.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val started = CaptureService.ensureRunning(context).isEmpty() &&
            CaptureService.running.value
        if (!started) askHerToOpenIt(context)
    }

    /**
     * One notification, in the neutral voice the rest of the app uses. It says
     * nothing about calls or recording — THE INSPECTION TEST holds on the lock
     * screen too — and it opens the app, where the home screen carries the
     * button that re-arms capture.
     */
    private fun askHerToOpenIt(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { setShowBadge(false) }
        )

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notice = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.boot_reopen_title))
            .setContentText(context.getString(R.string.boot_reopen_detail))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        // Without POST_NOTIFICATIONS this is a no-op rather than a crash: the
        // permission is optional by design, and a boot receiver is the worst
        // possible place to throw.
        runCatching { manager.notify(NOTIFICATION_ID, notice) }
    }

    private companion object {
        const val CHANNEL_ID = "rearm"
        const val NOTIFICATION_ID = 2
    }
}
