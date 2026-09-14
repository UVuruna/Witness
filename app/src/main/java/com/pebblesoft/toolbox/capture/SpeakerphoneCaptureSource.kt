package com.pebblesoft.toolbox.capture

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.pebblesoft.toolbox.R
import com.pebblesoft.toolbox.permissions.AppPermission
import com.pebblesoft.toolbox.permissions.RuntimePermissions

/**
 * The route that never needs a manufacturer's permission: speaker on,
 * microphone recording the room.
 *
 * TWO VOICES MEANS TWO PEOPLE, BY ANY ROUTE (owner, 2026-09-14). The privileged
 * route can be closed by an audio driver; this one cannot, because it asks the
 * phone for nothing unusual. The far party's voice leaves the loudspeaker into
 * the room and the microphone hears the room, so both people are in the file on
 * any handset ever made — which makes this the only mechanism in the app that
 * actually satisfies EVERY PHONE, OR IT DOES NOT COUNT.
 *
 * Its cost is real and never hidden: the call is audible to whoever is nearby.
 * So it stays off until the user switches it on, and the guide says plainly what
 * she is agreeing to.
 *
 * ONE KIND, ONE CLASS: ordinary calls and calls inside other apps are the same
 * mechanism with different switches, so they are two ENTRIES of this class
 * rather than two classes. [Variant] is the whole difference between them.
 */
class SpeakerphoneCaptureSource(
    private val routes: RouteMemory,
    private val variant: Variant,
) : CaptureSource {

    enum class Variant(
        val id: String,
        val labelRes: Int,
        val headlineRes: Int,
        val switchTitleRes: Int,
        val switchDetailRes: Int,
    ) {
        /** Ordinary calls through the phone network, recorded out loud. */
        CARRIER(
            id = "speakerphone-call",
            labelRes = R.string.route_speakerphone_label,
            headlineRes = R.string.guide_speaker_headline,
            switchTitleRes = R.string.guide_speaker_2_title,
            switchDetailRes = R.string.guide_speaker_2_detail,
        ),

        /** Calls inside any other app — the only route that can reach those at all. */
        VOIP(
            id = "speakerphone-voip",
            labelRes = R.string.route_speakerphone_voip_label,
            headlineRes = R.string.guide_voip_headline,
            switchTitleRes = R.string.guide_voip_2_title,
            switchDetailRes = R.string.guide_voip_2_detail,
        ),
    }

    override val id = variant.id

    override fun label(context: Context): String = context.getString(variant.labelRes)

    override fun tested(context: Context): VoiceCheck.Route = routes.verdict(id)

    override fun status(context: Context): CaptureSource.Status {
        if (!switchedOn()) return CaptureSource.Status.UNAVAILABLE
        if (!RuntimePermissions.isGranted(context, AppPermission.MICROPHONE)) {
            return CaptureSource.Status.NEEDS_SETUP
        }
        if (!MicRecorder(context).hasLoudspeaker()) return CaptureSource.Status.UNAVAILABLE
        return when (routes.verdict(id)) {
            VoiceCheck.Route.BOTH_PEOPLE -> CaptureSource.Status.READY
            VoiceCheck.Route.UNTESTED -> CaptureSource.Status.NEEDS_TEST
            VoiceCheck.Route.ONE_PERSON_ONLY -> CaptureSource.Status.PROVEN_HALF
            VoiceCheck.Route.NOTHING -> CaptureSource.Status.UNAVAILABLE
        }
    }

    override fun recorder(context: Context): RouteRecorder? {
        if (!switchedOn()) return null
        if (!RuntimePermissions.isGranted(context, AppPermission.MICROPHONE)) return null
        return MicRecorder(context)
    }

    override fun guide(context: Context): Guide = Guide(
        headline = context.getString(variant.headlineRes),
        steps = RuntimePermissions.steps(context) + listOf(
            Step(
                title = context.getString(R.string.guide_speaker_1_title),
                detail = context.getString(R.string.guide_speaker_1_detail),
            ),
            Step(
                title = context.getString(variant.switchTitleRes),
                detail = context.getString(variant.switchDetailRes),
                isDone = { switchedOn() },
            ),
        ),
        keepInMind = listOf(
            context.getString(R.string.guide_speaker_keep_audible),
        ),
    )

    private fun switchedOn(): Boolean = when (variant) {
        Variant.CARRIER -> routes.speakerphoneForCalls
        Variant.VOIP -> routes.speakerphoneForVoip
    }

    companion object {
        /**
         * Where a phone hides the Wi-Fi calling switch differs by manufacturer,
         * so the app offers the most specific screen the device actually answers
         * to and falls back to the general network screen rather than to nothing.
         *
         * Turning Wi-Fi calling off is the one system setting the owner allowed
         * the app to ask for (2026-09-14), and only because on many phones it is
         * the single difference between a recording and silence.
         */
        fun wifiCallingScreen(context: Context): Intent? = WIFI_CALLING_SCREENS
            .map { action -> Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .firstOrNull { intent -> intent.resolveActivity(context.packageManager) != null }

        private val WIFI_CALLING_SCREENS = listOf(
            "android.settings.WIFI_CALLING_SETTINGS",
            Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
            Settings.ACTION_WIRELESS_SETTINGS,
        )
    }
}
