package com.pebblesoft.toolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.pebblesoft.toolbox.ui.AppNav
import com.pebblesoft.toolbox.ui.theme.ToolboxTheme

/**
 * The single host activity.
 *
 * A shipped build carries FLAG_SECURE, so the app never appears in the
 * recent-apps preview or in a screenshot — part of THE INSPECTION TEST: someone
 * who takes the phone and swipes through recents must find nothing worth
 * opening.
 *
 * A DEBUG build deliberately does not. FLAG_SECURE blanks `screencap` too, so
 * with it on, every screenshot the GUI law is judged by comes out black and the
 * layout can never be checked. The flag guards real users; the debug build
 * exists only on our machines.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!BuildConfig.DEBUG) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "—"

        setContent {
            ToolboxTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNav(versionName = versionName)
                }
            }
        }
    }
}
