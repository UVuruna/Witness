package com.uvuruna.callprobe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.uvuruna.callprobe.audio.RecordingStore
import com.uvuruna.callprobe.audio.WavRecorder
import java.io.File

/**
 * Foreground service (type=microphone) that owns one WavRecorder for the
 * lifetime of a recording. It is the ONLY place the mic is opened, so the
 * OS grants the mic-while-in-call access to a properly typed FGS.
 *
 * The live peak level is published to a static holder so the UI can show a
 * meter without binding — this is a probe, not a product.
 */
class RecorderService : Service() {

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_KIND = "kind"
        private const val CHANNEL_ID = "probe_recorder"
        private const val NOTE_ID = 1

        @Volatile var recording: Boolean = false; private set
        @Volatile var level: Int = 0; private set
        @Volatile var currentSource: String = ""; private set

        private var instance: RecorderService? = null

        fun start(context: Context, source: Int, kind: String) {
            val i = Intent(context, RecorderService::class.java)
                .putExtra(EXTRA_SOURCE, source)
                .putExtra(EXTRA_KIND, kind)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecorderService::class.java))
        }
    }

    private var recorder: WavRecorder? = null
    private var wav: File? = null
    private var source: Int = 0
    private var kind: String = "manual"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        source = intent?.getIntExtra(EXTRA_SOURCE, RecordingStore.SOURCES.first().second)
            ?: RecordingStore.SOURCES.first().second
        kind = intent?.getStringExtra(EXTRA_KIND) ?: "manual"
        startForeground(NOTE_ID, buildNotification())

        val file = RecordingStore.newFile(this, kind)
        val rec = WavRecorder(source, file) { lvl -> level = lvl }
        if (!rec.start()) {
            level = 0
            recording = false
            // Persist the failure so the probe list shows WHY nothing recorded.
            RecordingStore.writeStats(file, kind, source, rec)
            stopSelf()
            return START_NOT_STICKY
        }
        recorder = rec
        wav = file
        currentSource = RecordingStore.sourceName(source)
        recording = true
        instance = this
        return START_STICKY
    }

    override fun onDestroy() {
        val rec = recorder
        val file = wav
        if (rec != null && file != null) {
            rec.stop()
            RecordingStore.writeStats(file, kind, source, rec)
        }
        recording = false
        level = 0
        instance = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Audio Probe", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Probe")
            .setContentText("Measuring microphone capture")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }
}
