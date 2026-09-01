package com.uvuruna.callprobe

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.uvuruna.callprobe.ui.ProbeScreen
import com.uvuruna.callprobe.ui.ProbeViewModel

/**
 * Single-screen host. All the probe needs from the platform is RECORD_AUDIO
 * (to open the mic) and READ_PHONE_STATE (to notice a call in auto mode); it
 * asks for both up front, then hands control to ProbeScreen.
 */
class MainActivity : ComponentActivity() {

    private val vm: ProbeViewModel by viewModels()

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* result read live */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNeededPermissions()

        setContent {
            MaterialTheme {
                Surface {
                    ProbeScreen(
                        vm = vm,
                        onRequestManual = { source ->
                            if (hasAudio()) vm.startManual(source) else requestNeededPermissions()
                        },
                        onRequestAuto = { enabled, source ->
                            if (hasAudio()) vm.setAutoMode(enabled, source) else requestNeededPermissions()
                        },
                        onRequestListen = { enabled ->
                            if (hasAudio()) vm.setListenMode(enabled) else requestNeededPermissions()
                        },
                        onExemptBattery = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:$packageName"),
                                )
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun hasAudio(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun requestNeededPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        permissions.launch(wanted.toTypedArray())
    }
}
