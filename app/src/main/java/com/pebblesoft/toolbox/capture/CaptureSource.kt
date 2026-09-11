package com.pebblesoft.toolbox.capture

import android.content.Context
import android.content.Intent

/**
 * How a finished conversation reaches the vault.
 *
 * ONE KIND, ONE CLASS: every way of obtaining a recording — whatever the
 * platform ends up allowing — is an entry in [CaptureRegistry], never a second
 * code path bolted beside the first. A source's whole contract is: say whether
 * it is ready, tell the user in plain words how to make it ready, and announce
 * finished recordings.
 *
 * THE HALF-RECORDING LAW (CLAUDE.md): a source that cannot deliver BOTH voices
 * is not a degraded source, it is not a source. [deliversBothVoices] exists so
 * the registry can refuse one outright rather than let it reach a user.
 */
interface CaptureSource {

    /** Stable id stored on every record, so a failure traces back to its path. */
    val id: String

    /** What the user is shown — plain words, no jargon. */
    val label: String

    /** Whether this source puts both people's voices in the file. */
    val deliversBothVoices: Boolean

    fun status(context: Context): Status

    /** The numbered instructions the setup screen shows, in the user's language. */
    fun guide(context: Context): Guide

    enum class Status {
        /** Ready: calls covered by the lists will be recorded from now on. */
        READY,

        /** The user must complete [guide] once. */
        NEEDS_SETUP,

        /** This phone cannot offer this source at all. */
        UNAVAILABLE,
    }
}

/**
 * One step the user performs, once.
 *
 * ONLY STEPS AN ORDINARY USER CAN DO (CLAUDE.md): every step here must be
 * something a frightened, non-technical person can finish alone. [openScreen]
 * carries the intent that takes her straight there, so she never has to hunt
 * through Settings — a step without a deep link must be one she can do from
 * the app itself.
 */
data class Step(
    val title: String,
    val detail: String,
    val openScreen: Intent? = null,
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
 * A mechanism only enters this list once it has been proven to deliver both
 * voices on real phones; the list is deliberately empty until then, because an
 * entry here is a promise to the user, not a hope.
 */
object CaptureRegistry {

    private val sources = mutableListOf<CaptureSource>()

    fun register(source: CaptureSource) {
        require(source.deliversBothVoices) {
            "A source that cannot capture both voices may never be registered: ${source.id}"
        }
        sources += source
    }

    fun all(): List<CaptureSource> = sources.toList()

    /** The best source this phone can actually use right now, or null. */
    fun active(context: Context): CaptureSource? =
        sources.firstOrNull { it.status(context) == CaptureSource.Status.READY }

    /** The best source this phone could use once the user finishes its guide. */
    fun candidate(context: Context): CaptureSource? =
        sources.firstOrNull { it.status(context) != CaptureSource.Status.UNAVAILABLE }
}
