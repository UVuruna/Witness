package com.pebblesoft.toolbox

import android.app.Application
import android.content.Context
import com.pebblesoft.toolbox.capture.CaptureRegistry
import com.pebblesoft.toolbox.capture.RecordingCoordinator
import com.pebblesoft.toolbox.capture.RouteMemory
import com.pebblesoft.toolbox.capture.SpeakerphoneCaptureSource
import com.pebblesoft.toolbox.capture.shizuku.ShizukuCaptureSource
import com.pebblesoft.toolbox.capture.shizuku.ShizukuManager
import com.pebblesoft.toolbox.data.Db
import com.pebblesoft.toolbox.data.Prefs
import com.pebblesoft.toolbox.rules.RecordingPolicy
import com.pebblesoft.toolbox.vault.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The one place the app's parts are built.
 *
 * Small enough not to need a dependency-injection framework, explicit enough
 * that every collaborator is visible in one screen of code. Everything is lazy:
 * opening the vault touches the keystore, and that should not happen while the
 * app is merely starting.
 *
 * **One gatekeeper, one conductor.** The previous round built a second
 * [ShizukuManager] inside the capture service, so the app and the service each
 * held a different view of the same privilege, and the listeners of the second
 * one were never removed. Both live here now and the service borrows them.
 *
 * On create it registers the capture mechanisms in preference order: the quiet
 * one first, then the loudspeaker one that works everywhere at the cost of being
 * heard. The registry no longer takes a source's own word about carrying both
 * voices — what a phone really does is measured by the guided test call and
 * remembered in [routes].
 */
class ToolboxApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: Db by lazy { Db.get(this) }
    val vault: Vault by lazy { Vault(this) }
    val prefs: Prefs by lazy { Prefs(this) }
    val policy: RecordingPolicy by lazy { RecordingPolicy(db.rules(), prefs) }
    val shizuku: ShizukuManager by lazy { ShizukuManager(this) }
    val routes: RouteMemory by lazy { RouteMemory(prefs, scope) }
    val coordinator: RecordingCoordinator by lazy { RecordingCoordinator(this, routes) }

    override fun onCreate() {
        super.onCreate()
        CaptureRegistry.register(ShizukuCaptureSource(shizuku, routes))
        CaptureRegistry.register(
            SpeakerphoneCaptureSource(routes, SpeakerphoneCaptureSource.Variant.CARRIER)
        )
        CaptureRegistry.register(
            SpeakerphoneCaptureSource(routes, SpeakerphoneCaptureSource.Variant.VOIP)
        )
        routes.start()
        scope.launch { routes.prime() }
        shizuku.onStart()
    }
}

/** Reach the container from anywhere that has a Context. */
val Context.app: ToolboxApp
    get() = applicationContext as ToolboxApp
