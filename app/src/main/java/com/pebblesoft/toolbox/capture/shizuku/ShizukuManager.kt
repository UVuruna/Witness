package com.pebblesoft.toolbox.capture.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.pebblesoft.toolbox.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

/**
 * The one gatekeeper to Shizuku.
 *
 * Everything the app knows about "can we borrow the privilege, and is the
 * privileged recorder connected" lives here, exposed as one [state] flow the UI
 * observes. Nothing else in the app talks to the Shizuku SDK directly.
 *
 * The states map exactly to what the user sees and does:
 *  - NOT_INSTALLED  → the Shizuku app is not on the phone (send her to install)
 *  - NOT_RUNNING    → installed but the service is not started (the reboot case)
 *  - DENIED         → running, but she has not granted us permission yet
 *  - READY          → permission held and the recorder bound; we can capture
 */
enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, DENIED, READY }

class ShizukuManager(private val context: Context) {

    private val _state = MutableStateFlow(ShizukuState.NOT_RUNNING)
    val state: StateFlow<ShizukuState> = _state

    @Volatile var recorder: IRecorderService? = null
        private set

    private val PERMISSION_REQUEST = 4010

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, PrivilegedRecorder::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("recorder")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            recorder = if (binder != null && binder.pingBinder()) {
                IRecorderService.Stub.asInterface(binder)
            } else null
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recorder = null
            refresh()
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == PERMISSION_REQUEST) {
            if (result == PackageManager.PERMISSION_GRANTED) bindRecorder()
            refresh()
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh(); if (hasPermission()) bindRecorder() }
    private val binderDead = Shizuku.OnBinderDeadListener { recorder = null; refresh() }

    fun onStart() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refresh()
    }

    fun onStop() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    /** True once the privileged recorder is bound and ready to capture. */
    fun isReady(): Boolean = _state.value == ShizukuState.READY && recorder != null

    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    }.getOrDefault(false)

    /** Ask the user for permission (opens Shizuku's own grant dialog). */
    fun requestPermission() {
        if (!pingBinder()) { refresh(); return }
        if (hasPermission()) { bindRecorder(); return }
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST) }
    }

    /** (Re)bind the privileged recorder; safe to call repeatedly. */
    fun bindRecorder() {
        if (!hasPermission()) return
        runCatching { Shizuku.bindUserService(serviceArgs, connection) }
    }

    fun refresh() {
        _state.value = when {
            !isInstalled() -> ShizukuState.NOT_INSTALLED
            !pingBinder() -> ShizukuState.NOT_RUNNING
            !hasPermission() -> ShizukuState.DENIED
            recorder == null -> { bindRecorder(); ShizukuState.DENIED }
            else -> ShizukuState.READY
        }
    }

    private fun pingBinder(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    private fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
}
