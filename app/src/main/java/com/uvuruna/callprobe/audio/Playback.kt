package com.uvuruna.callprobe.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

/**
 * Plays one probe recording back through the loudspeaker so the owner can
 * answer the only question the numbers cannot: WHOSE voice is on the file.
 *
 * A peak of 9000 proves the microphone was not muted; it says nothing about
 * whether the other side of the call was captured. That verdict is made by
 * ear, and stored via [RecordingStore.writeHeard].
 */
object Playback {

    @Volatile var playingPath: String? = null; private set

    private var player: MediaPlayer? = null

    /** Starts (or restarts) playback of [wav]; playing the file that is
     *  already playing stops it, so one button serves both ways. */
    fun toggle(wav: File, onFinished: () -> Unit) {
        if (playingPath == wav.absolutePath) { stop(); return }
        stop()
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(wav.absolutePath)
            setOnCompletionListener { stop(); onFinished() }
            prepare()
            start()
        }
        player = mp
        playingPath = wav.absolutePath
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playingPath = null
    }
}
