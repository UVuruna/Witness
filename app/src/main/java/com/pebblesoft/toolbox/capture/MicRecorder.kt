package com.pebblesoft.toolbox.capture

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileOutputStream

/**
 * The route that works on every phone: speaker on, microphone recording.
 *
 * TWO VOICES MEANS TWO PEOPLE, BY ANY ROUTE (owner, 2026-09-14). The privileged
 * path is silent but depends on a manufacturer's audio driver cooperating. This
 * one depends on physics: the far party's voice leaves the loudspeaker into the
 * room, and the microphone picks up the room. Both people land in the file on
 * any handset ever made. It costs the one thing the privileged path does not —
 * everyone nearby hears the call — so it is offered, never imposed.
 *
 * **Echo cancellation is the enemy here.** `VOICE_COMMUNICATION` exists to
 * remove exactly what we came for: the sound of the far party coming back out of
 * the speaker. So the ladder starts at the least-processed source the device
 * offers and only falls back to the processed ones when nothing else opens.
 *
 * The engine is the shared [PcmRecorder]; this class contributes the ladder and
 * the audio routing, and puts the routing back the way it found it.
 */
class MicRecorder(private val context: Context) : RouteRecorder {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val engine = PcmRecorder()

    private var previousSpeakerphone: Boolean? = null
    private var routingChanged = false

    /**
     * The descriptor is held for the life of the recording. This host runs in
     * the app's own process, where the coordinator also keeps a reference — but
     * a recorder that depends on its CALLER keeping its file open is a recorder
     * waiting to be broken by a refactor.
     */
    @Suppress("unused")
    private var held: ParcelFileDescriptor? = null

    override fun isRecording(): Boolean = engine.isRecording()

    /**
     * Turn the speaker on and start recording the room.
     *
     * @return "" when recording started, otherwise why it did not.
     */
    override fun start(sink: ParcelFileDescriptor): String {
        held = sink
        routeToSpeaker()
        val error = engine.start(
            ladder = LADDER,
            sink = FileOutputStream(sink.fileDescriptor),
            // A microphone's second channel is a second point in the same room,
            // never a second person — recording it would let the both-voices
            // test fire on one person talking loudly. This route proves itself
            // through the test call's silent window instead.
            stereoFirst = false,
            callAudio = false,
        )
        if (error.isNotEmpty()) {
            restoreRoute()
            held = null
        }
        return error
    }

    override fun stop(): CaptureOutcome {
        val outcome = engine.stop()
        restoreRoute()
        held = null
        return outcome
    }

    /** True when this phone can put the call on its own loudspeaker. */
    fun hasLoudspeaker(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.availableCommunicationDevices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        } else {
            true // every pre-12 handset exposes setSpeakerphoneOn
        }

    private fun routeToSpeaker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audio.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } ?: return
            routingChanged = audio.setCommunicationDevice(speaker)
        } else {
            @Suppress("DEPRECATION")
            previousSpeakerphone = audio.isSpeakerphoneOn
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = true
            routingChanged = true
        }
    }

    private fun restoreRoute() {
        if (!routingChanged) return
        routingChanged = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = previousSpeakerphone ?: false
            previousSpeakerphone = null
        }
    }

    private companion object {
        /**
         * Least-processed first. UNPROCESSED and MIC leave the loudspeaker's
         * output in the signal; VOICE_COMMUNICATION would strip it out, which is
         * why it sits last and is a rung of last resort rather than a choice.
         */
        val LADDER = listOf(
            "UNPROCESSED" to MediaRecorder.AudioSource.UNPROCESSED,
            "MIC" to MediaRecorder.AudioSource.MIC,
            "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
            "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        )
    }
}
