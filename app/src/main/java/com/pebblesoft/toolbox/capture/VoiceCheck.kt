package com.pebblesoft.toolbox.capture

import com.pebblesoft.toolbox.data.Quality

/**
 * THE one place that answers "is the other person in this file?".
 *
 * A RECORDING WITHOUT BOTH VOICES IS NOT A RESULT (CLAUDE.md), clarified by the
 * owner on 2026-09-14: the law counts two PEOPLE, not two audio paths — a file
 * made through the microphone with the speaker on satisfies it exactly as a
 * privileged tap does. So this class never asks where the audio came from. It
 * asks only what can be proven about who is in it, and it has exactly two ways
 * to prove it:
 *
 *  1. **Two channels that differ.** A device that hands back uplink and downlink
 *     as the two channels of one stream has already separated the speakers for
 *     us: both carry speech, and the difference between them is large. One
 *     signal copied into both channels fails the difference test, and a channel
 *     that never carried speech fails the activity test.
 *  2. **The silent window of the guided test call.** In a single-channel
 *     recording no amount of energy analysis separates two people — but if the
 *     user was asked to stay quiet and sound arrived anyway, the sound was the
 *     other person. That test is run once per phone, per route, during setup,
 *     and [verdict] trusts its result for later calls on the same route.
 *
 * Anything else is [Quality.UNVERIFIED]. Guessing from loudness is what the
 * previous round did, and it stamped one-sided files as evidence.
 */
object VoiceCheck {

    /** What the guided test call established about a capture route on this phone. */
    enum class Route {
        /** Never tested here — the app may not claim anything about it. */
        UNTESTED,

        /** Sound arrived while the user stayed silent: the far party reaches the file. */
        BOTH_PEOPLE,

        /** The user was heard, the far party never was. */
        ONE_PERSON_ONLY,

        /** The route produced no usable audio at all on this phone. */
        NOTHING,
    }

    /**
     * The verdict for one finished recording.
     *
     * [routeProven] is the stored result of the guided test call for the route
     * that made this file — the per-phone half of the proof.
     */
    fun verdict(outcome: CaptureOutcome, routeProven: Boolean): Quality {
        if (outcome.error.isNotEmpty() || outcome.isEmpty()) return Quality.FAILED
        if (isSilent(outcome)) return Quality.FAILED

        if (outcome.channels >= 2) {
            return if (channelsHoldTwoPeople(outcome)) Quality.BOTH_VOICES else Quality.ONE_VOICE
        }
        return if (routeProven) Quality.BOTH_VOICES else Quality.UNVERIFIED
    }

    /**
     * Read the guided test call: the user was asked to stay quiet between
     * [quietFromSlot] and [quietToSlot] of the timeline while the far side kept
     * talking. Sound in that window is the far side, and nothing else.
     */
    fun readTestCall(outcome: CaptureOutcome, quietFromSlot: Int, quietToSlot: Int): Route {
        if (outcome.error.isNotEmpty() || outcome.isEmpty() || isSilent(outcome)) return Route.NOTHING

        // A stereo device proves itself without the silent window at all.
        if (outcome.channels >= 2 && channelsHoldTwoPeople(outcome)) return Route.BOTH_PEOPLE

        val window = outcome.timeline.drop(quietFromSlot).take(quietToSlot - quietFromSlot)
        if (window.isEmpty()) return Route.NOTHING
        val loudSlots = window.count { it > SPEECH_FLOOR }
        return if (loudSlots >= (window.size * QUIET_WINDOW_SHARE).toInt().coerceAtLeast(1)) {
            Route.BOTH_PEOPLE
        } else {
            Route.ONE_PERSON_ONLY
        }
    }

    private fun isSilent(outcome: CaptureOutcome): Boolean =
        outcome.energy.none { it.peak > SILENCE_PEAK }

    /**
     * Two channels hold two people when BOTH carried speech for a real share of
     * the call AND the channels are genuinely different from one another.
     *
     * The proof is only valid for the CALL's own audio, where the two channels
     * are uplink and downlink. Two microphone channels are two points in one
     * room: they also differ, and they are also both active, while one person
     * shouts. Applying this test there would stamp evidence on a one-person
     * file — so the stream has to say what it is before the test may run.
     */
    private fun channelsHoldTwoPeople(outcome: CaptureOutcome): Boolean {
        if (!outcome.callAudio) return false
        val both = outcome.energy.take(2)
        if (both.size < 2) return false
        val everyChannelSpoke = both.all { it.activeShare(outcome.frames) >= MIN_ACTIVE_SHARE }
        val channelsDiffer = outcome.differenceRms > MIN_DIFFERENCE_RMS
        return everyChannelSpoke && channelsDiffer
    }

    // ── Thresholds ──────────────────────────────────────────────────────────
    // 16-bit sample magnitudes (0..32767) and plain shares of the call.

    /** Under this peak the whole file is effectively silence. */
    private const val SILENCE_PEAK = 150

    /** Above this magnitude a slot or frame is somebody talking, not room noise. */
    private const val SPEECH_FLOOR = 600

    /** A channel must carry speech for at least this share of the call to count. */
    private const val MIN_ACTIVE_SHARE = 0.02

    /** Below this, the two channels are the same signal twice over. */
    private const val MIN_DIFFERENCE_RMS = 200

    /** This share of the silent window must hold speech for the far side to count. */
    private const val QUIET_WINDOW_SHARE = 0.15
}
