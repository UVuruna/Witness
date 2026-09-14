package com.pebblesoft.toolbox.ui.recordings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pebblesoft.toolbox.R
import com.pebblesoft.toolbox.data.CallRecord
import com.pebblesoft.toolbox.data.Direction
import com.pebblesoft.toolbox.data.Quality
import com.pebblesoft.toolbox.data.TranscriptState
import com.pebblesoft.toolbox.ui.components.EmptyState
import com.pebblesoft.toolbox.ui.components.Pill
import com.pebblesoft.toolbox.ui.components.AdaptiveBody
import com.pebblesoft.toolbox.ui.components.Section
import com.pebblesoft.toolbox.ui.components.fullWidthItem
import com.pebblesoft.toolbox.ui.components.SoftCard
import java.util.Calendar
import java.util.Locale

/**
 * The data section: every recording, grouped the way a person looks for one.
 *
 * People remember two things about a call — WHO it was and ROUGHLY WHEN. So the
 * list groups by person first, and inside a person by time, newest first; the
 * search box covers the case where they remember only a fragment of the number.
 */
@Composable
fun RecordingsScreen(
    records: List<CallRecord>,
    onOpen: (Long) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val matching = remember(records, query) {
        if (query.isBlank()) records
        else records.filter { record ->
            val needle = query.trim().lowercase(Locale.getDefault())
            record.contactName?.lowercase(Locale.getDefault())?.contains(needle) == true ||
                record.number.contains(needle)
        }
    }

    val groups = remember(matching) { groupByPerson(matching) }

    AdaptiveBody(spacing = 14.dp) {
        fullWidthItem {
            Section {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.rec_search)) },
                )
            }
        }

        if (matching.isEmpty()) {
            fullWidthItem {
                EmptyState(
                    icon = Icons.Filled.GraphicEq,
                    title = stringResource(R.string.rec_empty_title),
                    detail = stringResource(R.string.rec_empty_detail),
                )
            }
        }

        groups.forEach { group ->
            fullWidthItem(key = "head-${group.key}") {
                Section {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        stringResource(R.string.rec_count, group.records.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(group.records, key = { it.id }) { record ->
                Section { RecordingRow(record) { onOpen(record.id) } }
            }
        }

        fullWidthItem { Spacer(Modifier.height(24.dp)) }
    }
}

/** One person's recordings, newest first. */
data class PersonGroup(val key: String, val title: String, val records: List<CallRecord>)

private fun groupByPerson(records: List<CallRecord>): List<PersonGroup> =
    records
        .groupBy { it.contactName ?: it.number.ifBlank { "?" } }
        .map { (title, list) -> PersonGroup(title, title, list.sortedByDescending { it.startedAt }) }
        .sortedByDescending { group -> group.records.firstOrNull()?.startedAt ?: 0L }

/**
 * One line in the list.
 *
 * The quality pill is never decoration: THE HALF-RECORDING LAW means a file
 * that does not hold both voices must announce itself as such everywhere it
 * appears, so nobody ever carries one to a lawyer believing it is evidence.
 */
@Composable
fun RecordingRow(record: CallRecord, onClick: () -> Unit) {
    SoftCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (record.direction) {
                    Direction.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
                    Direction.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
                    Direction.UNKNOWN -> Icons.Filled.Call
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.contactName
                        ?: record.number.ifBlank { stringResource(R.string.rec_unknown_number) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${formatClock(record.startedAt)} · ${formatDuration(record.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QualityPill(record.quality)
            TranscriptPill(record.transcriptState)
        }
    }
}

/**
 * The verdict, in the same place on every row.
 *
 * Only [Quality.BOTH_VOICES] gets the calm colour. Everything else is a warning
 * colour on purpose: a file holding one person is not a slightly worse recording,
 * it is not evidence, and nobody must ever carry one to a lawyer believing
 * otherwise.
 */
@Composable
private fun QualityPill(quality: Quality) {
    val scheme = MaterialTheme.colorScheme
    when (quality) {
        Quality.BOTH_VOICES -> Pill(
            stringResource(R.string.rec_quality_evidence), scheme.onPrimaryContainer, scheme.primaryContainer
        )
        Quality.ONE_VOICE -> Pill(
            stringResource(R.string.rec_quality_one_voice), scheme.onErrorContainer, scheme.errorContainer
        )
        Quality.UNVERIFIED -> Pill(
            stringResource(R.string.rec_quality_unverified), scheme.onSurfaceVariant, scheme.surfaceVariant
        )
        Quality.FAILED -> Pill(
            stringResource(R.string.rec_quality_failed), scheme.onErrorContainer, scheme.errorContainer
        )
        Quality.NOT_CAPTURED -> Pill(
            stringResource(R.string.rec_quality_not_captured), scheme.onErrorContainer, scheme.errorContainer
        )
    }
}

@Composable
private fun TranscriptPill(state: TranscriptState) {
    val scheme = MaterialTheme.colorScheme
    val label = when (state) {
        TranscriptState.NONE -> R.string.rec_transcript_none
        TranscriptState.PENDING -> R.string.rec_transcript_pending
        TranscriptState.DONE -> R.string.rec_transcript_done
        TranscriptState.FAILED -> R.string.rec_transcript_failed
    }
    Pill(stringResource(label), scheme.onSurfaceVariant, scheme.surfaceVariant)
}

internal fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", minutes / 60, minutes % 60, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

internal fun formatClock(epochMillis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return String.format(
        Locale.getDefault(),
        "%02d.%02d.%d. %02d:%02d",
        calendar.get(Calendar.DAY_OF_MONTH),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
    )
}
