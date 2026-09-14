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
 *
 * The answer has four shapes, not two, because "set up" and "proven to work" are
 * different facts and only the second one is protection. A phone that finished
 * the setup but was never tested says so; a phone the test caught recording her
 * alone says THAT, and points at the route that captures both people. Saying
 * "Recording is on" in either of those cases would be the most dangerous
 * sentence the app could show.
 */
@Composable
fun HomeScreen(
    state: AppState,
    onOpenSetup: () -> Unit,
    onOpenTest: () -> Unit,
    onOpenSettings: () -> Unit,
    onRearm: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    AdaptiveBody(spacing = 22.dp) {
        fullWidthItem {
            Section {
                when (state.captureStatus) {
                    CaptureSource.Status.READY -> StatusCard(
                        icon = Icons.Filled.CheckCircle,
                        headline = stringResource(R.string.home_state_ready),
                        detail = state.serviceProblem.ifEmpty {
                            stringResource(R.string.home_state_ready_detail)
                        },
                        accent = MaterialTheme.colorScheme.primaryContainer,
                        onAccent = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    CaptureSource.Status.NEEDS_TEST -> ActionCard(
                        headline = stringResource(R.string.home_state_test),
                        detail = stringResource(R.string.home_state_test_detail),
                        button = stringResource(R.string.home_test_button),
                        onClick = onOpenTest,
                    )

                    CaptureSource.Status.PROVEN_HALF -> ActionCard(
                        headline = stringResource(R.string.home_state_half),
                        detail = stringResource(R.string.home_state_half_detail),
                        button = stringResource(R.string.nav_settings),
                        onClick = onOpenSettings,
                    )

                    else -> ActionCard(
                        headline = if (state.serviceProblem.isEmpty()) {
                            stringResource(R.string.home_state_setup)
                        } else {
                            stringResource(R.string.home_state_problem)
                        },
                        detail = state.serviceProblem.ifEmpty {
                            stringResource(R.string.home_state_setup_detail)
                        },
                        button = stringResource(R.string.home_setup_button),
                        onClick = onOpenSetup,
                        // The setup guide tells her, in her own language, that
                        // after a restart she opens the app and taps a button on
                        // this screen. Until now that button did not exist —
                        // the sentence was a promise to a phone that had gone
                        // quiet overnight.
                        secondButton = stringResource(R.string.home_rearm),
                        onSecond = onRearm,
                    )
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

/**
 * The card the screen shows when the answer is "not yet", with the single tap
 * that changes it. One card shape for every unprotected state, so the user
 * learns one thing to look for instead of four.
 */
@Composable
private fun ActionCard(
    headline: String,
    detail: String,
    button: String,
    onClick: () -> Unit,
    secondButton: String? = null,
    onSecond: () -> Unit = {},
) {
    StatusCard(
        icon = Icons.Filled.ErrorOutline,
        headline = headline,
        detail = detail,
        accent = MaterialTheme.colorScheme.tertiaryContainer,
        onAccent = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column {
            Button(
                onClick = onClick,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Text(button)
            }
            if (secondButton != null) {
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = onSecond,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(secondButton, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}
