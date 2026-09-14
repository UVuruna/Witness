package com.pebblesoft.toolbox.capture.shizuku

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.Settings
import com.pebblesoft.toolbox.R
import com.pebblesoft.toolbox.capture.CaptureOutcome
import com.pebblesoft.toolbox.capture.CaptureSource
import com.pebblesoft.toolbox.capture.Guide
import com.pebblesoft.toolbox.capture.RouteMemory
import com.pebblesoft.toolbox.capture.RouteRecorder
import com.pebblesoft.toolbox.capture.Step
import com.pebblesoft.toolbox.capture.VoiceCheck
import com.pebblesoft.toolbox.permissions.RuntimePermissions

/**
 * The quiet route: the call's own audio, recorded by a borrowed privilege.
 *
 * This is the one the product prefers, and the reason is not sound quality — it
 * is that nobody in the room hears anything happen. For someone living with the
 * person on the other end of the call, silence is the feature.
 *
 * What it cannot do is promise itself. Whether the far party actually reaches
 * the file is decided below the app by the manufacturer's audio driver, and no
 * API reports it. So [tested] returns what the guided test call measured here,
 * [status] refuses to say READY until that measurement exists, and a phone where
 * the measurement came back one-sided reports [CaptureSource.Status.PROVEN_HALF]
 * — which ranks this route below the loudspeaker one that always works.
 */
class ShizukuCaptureSource(
    private val manager: ShizukuManager,
    private val routes: RouteMemory,
) : CaptureSource {

    override val id = ID

    override fun label(context: Context): String = context.getString(R.string.route_shizuku_label)

    override fun tested(context: Context): VoiceCheck.Route = routes.verdict(id)

    override fun status(context: Context): CaptureSource.Status {
        if (!RuntimePermissions.allRequiredGranted(context)) return CaptureSource.Status.NEEDS_SETUP
        if (manager.state.value != ShizukuState.READY) return CaptureSource.Status.NEEDS_SETUP
        return when (routes.verdict(id)) {
            VoiceCheck.Route.BOTH_PEOPLE -> CaptureSource.Status.READY
            VoiceCheck.Route.UNTESTED -> CaptureSource.Status.NEEDS_TEST
            VoiceCheck.Route.ONE_PERSON_ONLY -> CaptureSource.Status.PROVEN_HALF
            VoiceCheck.Route.NOTHING -> CaptureSource.Status.UNAVAILABLE
        }
    }

    override fun recorder(context: Context): RouteRecorder? {
        val privileged = manager.recorder ?: return null
        if (!manager.isReady()) return null
        return object : RouteRecorder {
            override fun start(sink: ParcelFileDescriptor): String = privileged.start(sink)
            override fun stop(): CaptureOutcome = CaptureOutcome.decode(privileged.stop())
            override fun isRecording(): Boolean = privileged.isRecording
        }
    }

    override fun guide(context: Context): Guide = Guide(
        headline = context.getString(R.string.guide_shizuku_headline),
        steps = RuntimePermissions.steps(context) + listOf(
            Step(
                title = context.getString(R.string.guide_shizuku_1_title),
                detail = context.getString(R.string.guide_shizuku_1_detail),
                openScreen = playStore("moe.shizuku.privileged.api"),
                isDone = { manager.isInstalled() },
            ),
            Step(
                title = context.getString(R.string.guide_shizuku_2_title),
                detail = context.getString(R.string.guide_shizuku_2_detail),
                openScreen = developerOptions(),
            ),
            Step(
                title = context.getString(R.string.guide_shizuku_3_title),
                detail = context.getString(R.string.guide_shizuku_3_detail),
                openScreen = launch(context, "moe.shizuku.privileged.api"),
            ),
            Step(
                title = context.getString(R.string.guide_shizuku_4_title),
                detail = context.getString(R.string.guide_shizuku_4_detail),
                isDone = { manager.state.value == ShizukuState.READY },
            ),
        ),
        keepInMind = listOf(
            context.getString(R.string.guide_shizuku_keep_reboot),
        ),
    )

    private fun playStore(pkg: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun developerOptions() =
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun launch(context: Context, pkg: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        const val ID = "shizuku-voicecall"
    }
}
