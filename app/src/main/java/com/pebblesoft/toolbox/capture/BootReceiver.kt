package com.pebblesoft.toolbox.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Brings the ear back after a reboot.
 *
 * A restart tears down the foreground service, so without this the phone would
 * quietly stop watching for calls until the user next opened the app. It cannot,
 * by itself, restore the Shizuku privilege — that is Android's limit, not ours —
 * but it restarts the watcher so the app knows a call is happening and can tell
 * the user honestly if the privilege still needs re-arming.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            CaptureService.ensureRunning(context)
        }
    }
}
