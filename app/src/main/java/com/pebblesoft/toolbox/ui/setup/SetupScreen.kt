package com.pebblesoft.toolbox.ui.setup

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import com.pebblesoft.toolbox.capture.CaptureRegistry
import com.pebblesoft.toolbox.capture.Guide
import com.pebblesoft.toolbox.ui.components.EmptyState
import com.pebblesoft.toolbox.ui.components.AdaptiveBody
import com.pebblesoft.toolbox.ui.components.Section
import com.pebblesoft.toolbox.ui.components.fullWidthItem
import com.pebblesoft.toolbox.ui.components.SoftCard

/**
 * The instructions screen — the one place the user is asked to DO something.
 *
 * Each step is numbered, written in one short sentence, and carries the button
 * that takes her straight to the screen it talks about, so she never has to
 * hunt through Settings. A step she has already completed shows a tick and
 * stops asking.
 *
 * ONE SETUP, GUIDED, THEN NOTHING (CLAUDE.md): if a mechanism cannot be
 * explained as a short numbered list here, it does not ship. The empty state is
 * deliberate and honest — a phone that cannot do this is told so plainly rather
 * than walked into a setup that will not work.
 *
 * Permissions are steps like any other. The previous round put five dangerous
 * permissions in the manifest and asked for none of them, which is the whole
 * reason nothing recorded; asking from inside this numbered list, each with the
 * sentence that says why, keeps the request in the one place the user is already
 * being led through rather than in a burst of system dialogs.
 */
@Composable
fun SetupScreen(onDone: () -> Unit, onGranted: () -> Unit = {}) {
    val context = LocalContext.current
    val source = CaptureRegistry.candidate(context)
    val guide = source?.guide(context)
    val asker = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onGranted() }

    if (guide == null) {
        EmptyState(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.setup_none_title),
            detail = stringResource(R.string.setup_none_detail),
        )
        return
    }

    AdaptiveBody(spacing = 14.dp) {
        fullWidthItem {
            Section {
                Text(guide.headline, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.setup_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        itemsIndexed(guide.steps) { index, step ->
            StepCard(index + 1, step, context) { permissions -> asker.launch(permissions.toTypedArray()) }
        }

        if (guide.keepInMind.isNotEmpty()) {
            item { KeepInMind(guide) }
        }

        fullWidthItem {
            Section {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.setup_finish))
                }
            }
        }

        fullWidthItem { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StepCard(
    ordinal: Int,
    step: com.pebblesoft.toolbox.capture.Step,
    context: Context,
    onAsk: (List<String>) -> Unit,
) {
    val done = step.isDone(context)
    Section {
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(
                            if (done) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.setup_done_marker),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text("$ordinal", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(step.title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(step.detail, style = MaterialTheme.typography.bodyMedium)

            if (step.grant.isNotEmpty() && !done) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onAsk(step.grant) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.setup_allow))
                }
            }

            step.openScreen?.let { intent ->
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(intent) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.setup_open))
                }
            }
        }
    }
}

@Composable
private fun KeepInMind(guide: Guide) {
    Section(stringResource(R.string.setup_keep_in_mind)) {
        SoftCard(tone = MaterialTheme.colorScheme.tertiaryContainer) {
            guide.keepInMind.forEach { line ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("•  ", color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}
