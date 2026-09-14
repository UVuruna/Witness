package com.pebblesoft.toolbox.capture

import com.pebblesoft.toolbox.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What this phone has PROVEN about each way of recording a call, and which of
 * them the user has switched on.
 *
 * EVERY PHONE, OR IT DOES NOT COUNT (CLAUDE.md) cannot be satisfied by a
 * promise, because whether the far party reaches the file is decided by a
 * manufacturer's audio driver that no app can inspect. It can be satisfied by a
 * measurement: the guided test call runs once per route, and what it found is
 * remembered here for the life of the install.
 *
 * The verdict has to be readable synchronously — a screen asking "is this phone
 * protected right now?" cannot await a preference read — so the stored value is
 * mirrored in memory and kept current from the same flow the rest of the app
 * observes. [changes] lets the UI recompose when a test finishes.
 */
class RouteMemory(private val prefs: Prefs, private val scope: CoroutineScope) {

    @Volatile private var cache: Map<String, VoiceCheck.Route> = emptyMap()

    /** The loudspeaker route is audible, so it is never on unless she turned it on. */
    @Volatile var speakerphoneForCalls: Boolean = false
        private set

    @Volatile var speakerphoneForVoip: Boolean = false
        private set

    private val _changes = MutableStateFlow(0L)

    /** Bumped whenever a verdict or a switch changes, so state holders can recompute. */
    val changes: StateFlow<Long> = _changes

    fun start() {
        scope.launch {
            prefs.routeVerdicts.collect { stored ->
                cache = parse(stored)
                bump()
            }
        }
        scope.launch {
            prefs.speakerphoneCalls.collect { speakerphoneForCalls = it; bump() }
        }
        scope.launch {
            prefs.speakerphoneVoip.collect { speakerphoneForVoip = it; bump() }
        }
    }

    fun verdict(routeId: String): VoiceCheck.Route = cache[routeId] ?: VoiceCheck.Route.UNTESTED

    /** True when a recording made by this route may be called evidence. */
    fun carriesBothPeople(routeId: String): Boolean =
        verdict(routeId) == VoiceCheck.Route.BOTH_PEOPLE

    suspend fun remember(routeId: String, route: VoiceCheck.Route) {
        prefs.setRouteVerdict(routeId, route.name)
        cache = cache + (routeId to route)
        bump()
    }

    /** Read straight from storage — used once at start-up, before the flow arrives. */
    suspend fun prime() {
        cache = parse(prefs.routeVerdicts.first())
        speakerphoneForCalls = prefs.speakerphoneCalls.first()
        speakerphoneForVoip = prefs.speakerphoneVoip.first()
        bump()
    }

    private fun parse(stored: Map<String, String>): Map<String, VoiceCheck.Route> =
        stored.mapNotNull { (id, word) ->
            runCatching { VoiceCheck.Route.valueOf(word) }.getOrNull()?.let { id to it }
        }.toMap()

    private fun bump() {
        _changes.value = _changes.value + 1
    }
}
