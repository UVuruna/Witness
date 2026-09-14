package com.pebblesoft.toolbox.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.pebblesoft.toolbox.R
import com.pebblesoft.toolbox.capture.Step

/**
 * The permissions the app must be GIVEN, as opposed to the ones it declares.
 *
 * The previous round shipped a manifest listing five dangerous permissions and
 * not one line of code that ever asked for them. A manifest entry is an
 * announcement; on Android 6 and later the answer still has to come from the
 * user, and until it does the microphone stays shut, the call-state listener
 * throws, and the foreground service is killed the moment it claims the
 * microphone type. Three separate failures, one missing question.
 *
 * ONE KIND, ONE CLASS: a permission is an entry in [AppPermission], with the
 * sentence that explains it to the user attached to the entry rather than
 * scattered through screens. Adding one is a new enum constant plus its two
 * strings — never a new dialog somewhere.
 */
enum class AppPermission(
    val manifest: String,
    val titleRes: Int,
    val whyRes: Int,
    /** Below this API level the permission is granted at install and never asked. */
    val sinceSdk: Int = Build.VERSION_CODES.M,
    /** False when the app still works without it, in a reduced way. */
    val required: Boolean = true,
) {
    /** Without this nothing is recorded at all, by any route. */
    MICROPHONE(
        manifest = Manifest.permission.RECORD_AUDIO,
        titleRes = R.string.perm_microphone_title,
        whyRes = R.string.perm_microphone_why,
    ),

    /** Without this the app never learns that a call started. */
    CALL_STATE(
        manifest = Manifest.permission.READ_PHONE_STATE,
        titleRes = R.string.perm_call_state_title,
        whyRes = R.string.perm_call_state_why,
    ),

    /**
     * Android stopped handing the number to call-state listeners in version 12.
     * The call log is what is left, and it is also the only place the direction
     * of the call can be read back reliably.
     */
    CALL_LOG(
        manifest = Manifest.permission.READ_CALL_LOG,
        titleRes = R.string.perm_call_log_title,
        whyRes = R.string.perm_call_log_why,
    ),

    /** Names instead of numbers, and the "not in my contacts" rule. */
    CONTACTS(
        manifest = Manifest.permission.READ_CONTACTS,
        titleRes = R.string.perm_contacts_title,
        whyRes = R.string.perm_contacts_why,
        required = false,
    ),

    /**
     * Android 13 made the notification a permission. The always-on service still
     * runs without it, but its quiet notice cannot be shown — and a service the
     * user cannot see is a service she cannot trust.
     */
    NOTIFICATIONS(
        manifest = "android.permission.POST_NOTIFICATIONS",
        titleRes = R.string.perm_notifications_title,
        whyRes = R.string.perm_notifications_why,
        sinceSdk = Build.VERSION_CODES.TIRAMISU,
        required = false,
    );

    fun appliesHere(): Boolean = Build.VERSION.SDK_INT >= sinceSdk
}

object RuntimePermissions {

    fun isGranted(context: Context, permission: AppPermission): Boolean =
        !permission.appliesHere() ||
            ContextCompat.checkSelfPermission(context, permission.manifest) ==
            PackageManager.PERMISSION_GRANTED

    fun missing(context: Context): List<AppPermission> =
        AppPermission.entries.filter { it.appliesHere() && !isGranted(context, it) }

    /** Nothing can be recorded until these are granted. */
    fun missingRequired(context: Context): List<AppPermission> =
        missing(context).filter { it.required }

    fun allRequiredGranted(context: Context): Boolean = missingRequired(context).isEmpty()

    /**
     * The permission half of the setup list.
     *
     * One step per permission, in the same shape as every other instruction, so
     * the setup screen has a single kind of thing to draw and the user sees one
     * numbered list instead of a list plus a burst of system dialogs.
     */
    fun steps(context: Context): List<Step> =
        AppPermission.entries.filter { it.appliesHere() }.map { permission ->
            Step(
                title = context.getString(permission.titleRes),
                detail = context.getString(permission.whyRes),
                grant = listOf(permission.manifest),
                isDone = { isGranted(it, permission) },
            )
        }
}
