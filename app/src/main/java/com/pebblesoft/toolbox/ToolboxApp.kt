package com.pebblesoft.toolbox

import android.app.Application
import android.content.Context
import com.pebblesoft.toolbox.capture.CaptureRegistry
import com.pebblesoft.toolbox.capture.shizuku.ShizukuCaptureSource
import com.pebblesoft.toolbox.capture.shizuku.ShizukuManager
import com.pebblesoft.toolbox.data.Db
import com.pebblesoft.toolbox.data.Prefs
import com.pebblesoft.toolbox.rules.RecordingPolicy
import com.pebblesoft.toolbox.vault.Vault

/**
 * The one place the app's parts are built.
 *
 * Small enough not to need a dependency-injection framework, explicit enough
 * that every collaborator is visible in one screen of code. Everything is lazy:
 * opening the vault touches the keystore, and that should not happen while the
 * app is merely starting.
 *
 * On create it registers the capture mechanism the owner chose (Shizuku). The
 * registry stays the gate — it accepts the source only because the source
 * declares it captures both voices — but the app is what puts it there.
 */
class ToolboxApp : Application() {

    val db: Db by lazy { Db.get(this) }
    val vault: Vault by lazy { Vault(this) }
    val prefs: Prefs by lazy { Prefs(this) }
    val policy: RecordingPolicy by lazy { RecordingPolicy(db.rules(), prefs) }
    val shizuku: ShizukuManager by lazy { ShizukuManager(this) }

    override fun onCreate() {
        super.onCreate()
        CaptureRegistry.register(ShizukuCaptureSource(shizuku))
        shizuku.onStart()
    }
}

/** Reach the container from anywhere that has a Context. */
val Context.app: ToolboxApp
    get() = applicationContext as ToolboxApp
