package com.pebblesoft.toolbox.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pebblesoft.toolbox.R
import com.pebblesoft.toolbox.ui.components.AdaptiveBody
import com.pebblesoft.toolbox.ui.components.Section
import com.pebblesoft.toolbox.ui.components.fullWidthItem
import com.pebblesoft.toolbox.ui.components.SoftCard
import java.util.Locale

/**
 * Settings — kept short on purpose.
 *
 * Everything here is a choice the user might need to change under pressure, so
 * each row says what it does in the user's own terms. Nothing technical, no
 * diagnostics, no "advanced" drawer.
 */
@Composable
fun SettingsScreen(
    biometricOn: Boolean,
    onBiometric: (Boolean) -> Unit,
    neutralLook: Boolean,
    onNeutralLook: (Boolean) -> Unit,
    storageBytes: Long,
    versionName: String,
) {
    AdaptiveBody(spacing = 18.dp) {
        item {
            Section(stringResource(R.string.settings_lock_section)) {
                SoftCard {
                    SwitchRow(
                        title = stringResource(R.string.settings_biometric),
                        detail = null,
                        checked = biometricOn,
                        onChange = onBiometric,
                    )
                }
            }
        }

        item {
            Section(stringResource(R.string.settings_look_section)) {
                SoftCard {
                    SwitchRow(
                        title = stringResource(R.string.settings_look_neutral),
                        detail = stringResource(R.string.settings_look_detail),
                        checked = neutralLook,
                        onChange = onNeutralLook,
                    )
                }
            }
        }

        item {
            Section(stringResource(R.string.settings_storage_section)) {
                SoftCard {
                    Text(
                        stringResource(R.string.settings_storage_used, humanBytes(storageBytes)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        item {
            Section(stringResource(R.string.settings_about_section)) {
                SoftCard {
                    Text(
                        stringResource(R.string.settings_version, versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        fullWidthItem { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

internal fun humanBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format(Locale.getDefault(), "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.0f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
