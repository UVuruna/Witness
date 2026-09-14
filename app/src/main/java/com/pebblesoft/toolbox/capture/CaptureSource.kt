package com.pebblesoft.toolbox.capture

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor

/**
 * How a finished conversation reaches the vault.
 *
 * ONE KIND, ONE CLASS: every way of obtaining a recording is an entry in
 * [CaptureRegistry], never a second code path bolted beside the first. A
 * source's whole contract is: say whether it is ready, say what the app has
 * PROVEN about it on this particular phone, and tell the user in plain words how
 * to make it ready.
 *
 * THE HALF-RECORDING LAW (CLAUDE.md) used to be enforced here by a boolean the
 * source declared about itself. That was a promise, not a fact — a compile-time
 * constant cannot know what a given manufacturer's audio driver does during a
 * live call, and the one source that declared `true` was never measured. The law
 * is now enforced where it can actually be kept: [tested] returns what the
 * guided test call MEASURED on this device, and a route that has not proven
 * itself is never presented as protection.
 */
interface CaptureSource {

    /** Stable id stored on every record, so a failure traces back to its path. */
    val id: String

    /** What the user is shown — plain words, no jargon, from resources. */
    fun label(context: Context): String

    fun status(context: Context): Status

    /** What the guided test call proved about this route on THIS phone. */
    fun tested(context: Context): VoiceCheck.Route

    /** The numbered instructions the setup screen shows, in the user's language. */
    fun guide(context: Context): Guide

    /**
     * The thing that actually records for this route, or null when the route is
     * not usable this second. A source describes a way of recording AND performs
     * it — splitting the two would put the same knowledge in two classes.
     */
    fun recorder(context: Context): RouteRecorder?

    /**
     * Declared best first. The order is what the app falls back through, so a
     * route proven to carry only one person always ranks below one that has not
     * been tried yet, and far below one that was proven whole.
     */
    enum class Status {
        /** Set up AND proven to carry both people: calls are covered from now on. */
        READY,

        /** Set up, but never proven on this phone — the test call is the last step. */
        NEEDS_TEST,

        /** The user must complete [guide] once. */
        NEEDS_SETUP,

        /**
         * Measured on this phone and found to carry the user alone. It still
         * records, and what it records is labelled for what it is, but nothing
         * in the app may present this route as protection.
         */
        PROVEN_HALF,

        /** This phone cannot offer this source at all. */
        UNAVAILABLE,
    }
}

/**
 * Records a call and reports what was actually in it.
 *
 * Both implementations write into a file descriptor the APP opened, so the one
 * that runs with borrowed privilege never touches app-private storage itself,
 * and the one that runs in the app uses the same door. The return of [stop] is
 * a measurement, never a claim: what source opened, how many channels, and how
 * much each channel carried — everything `VoiceCheck` needs and nothing it
 * would have to guess.
 */
interface RouteRecorder {
    fun start(sink: ParcelFileDescriptor): String
    fun stop(): CaptureOutcome
    fun isRecording(): Boolean
}

/**
 * One step the user performs, once.
 *
 * ONE SETUP, GUIDED, THEN NOTHING (CLAUDE.md): every step here must be something
 * a frightened, non-technical person can finish alone. [openScreen] carries the
 * intent that takes her straight there, so she never has to hunt through
 * Settings; [grant] names the runtime permissions the step asks Android for, so
 * a permission is requested from the same numbered list as everything else
 * rather than from a dialog that appears out of nowhere.
 */
data class Step(
    val title: String,
    val detail: String,
    val openScreen: Intent? = null,
    val grant: List<String> = emptyList(),
    val isDone: (Context) -> Boolean = { false },
)

/** The whole instruction set for one source, plus what it costs the user to keep. */
data class Guide(
    val headline: String,
    val steps: List<Step>,
    /** Anything that must stay true afterwards — shown as a standing reminder. */
    val keepInMind: List<String> = emptyList(),
)

/**
 * Every capture mechanism the app knows, in preference order.
 *
 * The order is the product's opinion about cost to the user, not about quality
 * of evidence: the silent route is offered before the audible one, because a
 * recording nobody in the room notices is worth more to someone living with the
 * person on the other end. Both are allowed to be here; which one a phone
 * actually gets is decided by [CaptureSource.status], and that in turn by what
 * the test call measured.
 */
object CaptureRegistry {

    private val sources = mutableListOf<CaptureSource>()

    fun register(source: CaptureSource) {
        require(sources.none { it.id == source.id }) { "duplicate capture source: ${source.id}" }
        sources += source
    }

    fun all(): List<CaptureSource> = sources.toList()

    fun byId(id: String): CaptureSource? = sources.firstOrNull { it.id == id }

    /** The best source this phone can actually use right now, or null. */
    fun active(context: Context): CaptureSource? =
        sources.firstOrNull { it.status(context) == CaptureSource.Status.READY }

    /** The best source this phone could use once the user finishes its guide. */
    fun candidate(context: Context): CaptureSource? =
        sources.firstOrNull { it.status(context) != CaptureSource.Status.UNAVAILABLE }

    /**
     * Every source worth using on this phone, best first.
     *
     * Sorted by what the phone PROVED, not by the order they were registered: a
     * route measured to carry both people outranks an untried one, and both
     * outrank a route already caught delivering half a conversation.
     */
    fun usable(context: Context): List<CaptureSource> =
        sources.filter { it.status(context) != CaptureSource.Status.UNAVAILABLE }
            .sortedBy { it.status(context).ordinal }
}
