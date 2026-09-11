package com.pebblesoft.toolbox

import android.app.Application
import android.content.Context
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
 */
class ToolboxApp : Application() {

    val db: Db by lazy { Db.get(this) }
    val vault: Vault by lazy { Vault(this) }
    val prefs: Prefs by lazy { Prefs(this) }
    val policy: RecordingPolicy by lazy { RecordingPolicy(db.rules(), prefs) }
}

/** Reach the container from anywhere that has a Context. */
val Context.app: ToolboxApp
    get() = applicationContext as ToolboxApp
