package com.pebblesoft.toolbox.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pebblesoft.toolbox.R
import com.pebblesoft.toolbox.capture.SpeakerphoneCaptureSource
import com.pebblesoft.toolbox.capture.TestPhase
import com.pebblesoft.toolbox.capture.TestState
import com.pebblesoft.toolbox.capture.VoiceCheck
import com.pebblesoft.toolbox.ui.components.AdaptiveBody
import com.pebblesoft.toolbox.ui.components.Section
import com.pebblesoft.toolbox.ui.components.SoftCard
import com.pebblesoft.toolbox.ui.components.StatusCard
import com.pebblesoft.toolbox.ui.components.fullWidthItem

/**
 * Twenty seconds that replace a promise with a measurement.
 *
 * EVERY PHONE, OR IT DOES NOT COUNT (CLAUDE.md) cannot be kept by any code in
 * this app, because whether the far party reaches the recording is decided by a
 * manufacturer's audio driver. It CAN be kept by asking the phone itself, once,
 * on a real call — and that is what this screen is.
 *
 * The instruction that makes it work is the silent window: she talks for five
 * seconds, then stays quiet for ten while the other side keeps talking. Sound
 * arriving during her silence can only be the other person. That one trick is
 * what lets a single-channel recording prove something energy analysis never
 * could, and it is why the screen insists on the order of the steps.
 *
 * A phone that fails the check is not abandoned: it is told which route does
 * work here, and offered the Wi-Fi calling switch, which is the single most
 * common reason a call records as silence.
 */
@Composable
fun TestCallScreen(
    state: TestState,
    voipEnabled: Boolean,
    onArm: (String?) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current

    AdaptiveBody(spacing = 16.dp) {
        fullWidthItem {
            Section {
                Text(
                    stringResource(R.string.test_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.test_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (state.phase) {
            TestPhase.IDLE -> {
                fullWidthItem { Instructions() }
                fullWidthItem {
                    Section {
                        Button(
                            onClick = { onArm(null) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.test_start)) }

                        // A call inside another app never reaches the telephony
                        // path, so it can only be measured by naming its route.
                        // Without this button that route stays untested for the
                        // life of the install, and everything it records is
                        // filed "not checked" no matter how good it is.
                        if (voipEnabled) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { onArm(SpeakerphoneCaptureSource.Variant.VOIP.id) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.test_start_voip)) }
                        }
                    }
                }
            }

            TestPhase.ARMED -> {
                fullWidthItem { Instructions() }
                fullWidthItem {
                    Section {
                        StatusCard(
                            icon = Icons.Filled.Hearing,
                            headline = stringResource(R.string.test_waiting),
                            detail = stringResource(R.string.test_step_1),
                            accent = MaterialTheme.colorScheme.tertiaryContainer,
                            onAccent = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            TestPhase.RECORDING -> fullWidthItem {
                Section {
                    StatusCard(
                        icon = Icons.Filled.Hearing,
                        headline = stringResource(R.string.test_recording),
                        detail = stringResource(R.string.test_step_3),
                        accent = MaterialTheme.colorScheme.tertiaryContainer,
                        onAccent = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            TestPhase.FINISHED -> {
                fullWidthItem { Verdict(state) }
                if (state.route != VoiceCheck.Route.BOTH_PEOPLE) {
                    fullWidthItem { WifiCallingHint() }
                }
                fullWidthItem {
                    Section {
                        OutlinedButton(
                            onClick = { onArm(state.sourceId.takeIf { it.isNotEmpty() }) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.test_again)) }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onDone,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.setup_finish)) }
                    }
                }
            }
        }

        fullWidthItem { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Instructions() {
    Section {
        SoftCard {
            listOf(
                stringResource(R.string.test_step_1),
                stringResource(R.string.test_step_2),
                stringResource(R.string.test_step_3),
            ).forEachIndexed { index, line ->
                Row(
                    Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Verdict(state: TestState) {
    val scheme = MaterialTheme.colorScheme
    val both = state.route == VoiceCheck.Route.BOTH_PEOPLE
    Section {
        StatusCard(
            icon = if (both) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            headline = stringResource(
                when (state.route) {
                    VoiceCheck.Route.BOTH_PEOPLE -> R.string.test_result_both
                    VoiceCheck.Route.ONE_PERSON_ONLY -> R.string.test_result_one
                    else -> R.string.test_result_nothing
                }
            ),
            detail = state.failure.ifEmpty { stringResource(R.string.test_intro) },
            accent = if (both) scheme.primaryContainer else scheme.errorContainer,
            onAccent = if (both) scheme.onPrimaryContainer else scheme.onErrorContainer,
        )
    }
}

@Composable
private fun WifiCallingHint() {
    val context = LocalContext.current
    val screen = SpeakerphoneCaptureSource.wifiCallingScreen(context)
    Section {
        SoftCard(tone = MaterialTheme.colorScheme.tertiaryContainer) {
            Text(
                stringResource(R.string.test_wifi_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (screen != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(screen) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.test_wifi_open))
                }
            }
        }
    }
}
