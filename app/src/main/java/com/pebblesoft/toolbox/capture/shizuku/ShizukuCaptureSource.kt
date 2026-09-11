package com.pebblesoft.toolbox.capture.shizuku

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.pebblesoft.toolbox.R
import com.pebblesoft.toolbox.capture.CaptureSource
import com.pebblesoft.toolbox.capture.Guide
import com.pebblesoft.toolbox.capture.Step

/**
 * The Shizuku capture mechanism, described for the registry and the setup screen.
 *
 * This is the one entry in [com.pebblesoft.toolbox.capture.CaptureRegistry]: it
 * records both voices (VOICE_CALL as shell), so it is allowed to register. Its
 * [guide] is the numbered script the user follows once — every step in her own
 * words, and each with the deep link that takes her straight to the screen it
 * names, because ONLY the pairing itself is asked of her and nothing may be
 * left to a hunt through Settings.
 */
class ShizukuCaptureSource(private val manager: ShizukuManager) : CaptureSource {

    override val id = "shizuku-voicecall"
    override val label = "Call audio (Shizuku)"
    override val deliversBothVoices = true

    override fun status(context: Context): CaptureSource.Status = when (manager.state.value) {
        ShizukuState.READY -> CaptureSource.Status.READY
        else -> CaptureSource.Status.NEEDS_SETUP
    }

    override fun guide(context: Context): Guide = Guide(
        headline = context.getString(R.string.guide_shizuku_headline),
        steps = listOf(
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
}
