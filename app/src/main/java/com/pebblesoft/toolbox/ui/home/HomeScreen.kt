package com.pebblesoft.toolbox.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pebblesoft.toolbox.R
import com.pebblesoft.toolbox.capture.CaptureSource
import com.pebblesoft.toolbox.data.DefaultRule
import com.pebblesoft.toolbox.data.UnknownRule
import com.pebblesoft.toolbox.ui.AppState
import com.pebblesoft.toolbox.ui.components.AdaptiveBody
import com.pebblesoft.toolbox.ui.components.Section
import com.pebblesoft.toolbox.ui.components.fullWidthItem
import com.pebblesoft.toolbox.ui.components.SoftCard
import com.pebblesoft.toolbox.ui.components.StatusCard
import com.pebblesoft.toolbox.ui.recordings.RecordingRow

/**
 * The first screen.
 *
 * It answers one question in one glance — "is my phone protecting me right
 * now?" — and gives exactly one action when the answer is no. Everything else
 * on this screen is reassurance, not work.
 */
@Composable
fun HomeScreen(
    state: AppState,
    onOpenSetup: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    val ready = state.captureStatus == CaptureSource.Status.READY

    AdaptiveBody(spacing = 22.dp) {
        fullWidthItem {
            Section {
                if (ready) {
                    StatusCard(
                        icon = Icons.Filled.CheckCircle,
                        headline = stringResource(R.string.home_state_ready),
                        detail = stringResource(R.string.home_state_ready_detail),
                        accent = MaterialTheme.colorScheme.primaryContainer,
                        onAccent = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    StatusCard(
                        icon = Icons.Filled.ErrorOutline,
                        headline = stringResource(R.string.home_state_setup),
                        detail = stringResource(R.string.home_state_setup_detail),
                        accent = MaterialTheme.colorScheme.tertiaryContainer,
                        onAccent = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Button(
                            onClick = onOpenSetup,
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ) {
                            Text(stringResource(R.string.home_setup_button))
                        }
                    }
                }
            }
        }

        item {
            Section(stringResource(R.string.home_covered_title)) {
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.fillMaxWidth(0.04f))
                        Column {
                            Text(
                                stringResource(
                                    when (state.defaultRule) {
                                        DefaultRule.RECORD_EVERYTHING -> R.string.home_covered_all
                                        DefaultRule.ONLY_WHITELIST -> R.string.home_covered_whitelist
                                    }
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    when (state.unknownRule) {
                                        UnknownRule.RECORD -> R.string.home_covered_unknown_record
                                        UnknownRule.SKIP -> R.string.home_covered_unknown_skip
                                    }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            Section(stringResource(R.string.home_recent_title)) {
                if (state.records.isEmpty()) {
                    SoftCard {
                        Text(
                            stringResource(R.string.home_recent_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(state.records.take(3), key = { it.id }) { record ->
            Section { RecordingRow(record) { onOpenRecord(record.id) } }
        }

        if (state.records.size > 3) {
            item {
                Section {
                    TextButton(onClick = onOpenRecordings, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.home_open_all))
                    }
                }
            }
        }

        fullWidthItem { Spacer(Modifier.height(24.dp)) }
    }
}
