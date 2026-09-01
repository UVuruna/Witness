package com.uvuruna.callprobe.listen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import com.uvuruna.callprobe.audio.WavRecorder

/**
 * The M0.5 probe: a microphone foreground service meant to run for HOURS —
 * exactly how the future SOS wake-phrase listener would. It writes no audio;
 * it keeps the mic open (levels only), appends a heartbeat to ListenLog every
 * minute with battery state, and counts loud events (a stand-in for the
 * wake-word engine's work). The log then shows battery cost per hour and
 * every moment the OS suspended the listener.
 */
class ListenService : Service() {

    companion object {
        private const val CHANNEL_ID = "probe_listener"
        private const val NOTE_ID = 2
        private const val BEAT_MS = 60_000L

        /** Peak above this counts as a loud event (clap-test threshold)... */
        private const val LOUD_PEAK = 8_000

        /** ...but no more often than this, so one clap is one event. */
        private const val LOUD_DEBOUNCE_MS = 2_000L

        @Volatile var listening: Boolean = false; private set
        @Volatile var level: Int = 0; private set
        @Volatile var startedAtMs: Long = 0; private set
        @Volatile var loudEvents: Int = 0; private set
        @Volatile var lastError: String? = null; private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ListenService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListenService::class.java))
        }
    }

    private var recorder: WavRecorder? = null
    private var beatThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var lastLoudAt = 0L
    @Volatile private var beatPeak = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (listening) return START_STICKY
        startForeground(NOTE_ID, buildNotification())

        val rec = WavRecorder(MediaRecorder.AudioSource.MIC, null) { lvl ->
            level = lvl
            if (lvl > beatPeak) beatPeak = lvl
            val now = System.currentTimeMillis()
            if (lvl > LOUD_PEAK && now - lastLoudAt > LOUD_DEBOUNCE_MS) {
                lastLoudAt = now
                loudEvents++
            }
        }
        if (!rec.start()) {
            lastError = rec.error
            ListenLog.append(this, "start", battery() + mapOf("error" to (rec.error ?: "?")))
            stopSelf()
            return START_NOT_STICKY
        }

        recorder = rec
        running = true
        listening = true
        startedAtMs = System.currentTimeMillis()
        loudEvents = 0
        lastError = null
        ListenLog.append(this, "start", battery())

        beatThread = Thread {
            while (running) {
                // Sleep in 1 s steps so a stop is honoured promptly.
                for (i in 0 until (BEAT_MS / 1_000)) {
                    if (!running) break
                    try { Thread.sleep(1_000) } catch (_: InterruptedException) { break }
                }
                if (!running) break
                ListenLog.append(
                    this, "beat",
                    battery() + mapOf("peak" to beatPeak, "loud" to loudEvents),
                )
                beatPeak = 0
            }
        }.also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        beatThread?.interrupt()
        beatThread = null
        recorder?.stop()
        recorder = null
        if (listening) ListenLog.append(this, "stop", battery())
        listening = false
        level = 0
        super.onDestroy()
    }

    private fun battery(): Map<String, Any> {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val pct = intent?.let {
            val lvl = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (lvl >= 0 && scale > 0) lvl * 100 / scale else -1
        } ?: -1
        val charging = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 } ?: false
        return mapOf("battery" to pct, "charging" to charging)
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Listen Probe", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Listen Probe")
            .setContentText("Measuring long-run microphone cost")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }
}
