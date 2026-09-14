package com.pebblesoft.toolbox.ui.recordings

import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pebblesoft.toolbox.R
import com.pebblesoft.toolbox.data.CallRecord
import com.pebblesoft.toolbox.data.Direction
import com.pebblesoft.toolbox.data.Quality
import com.pebblesoft.toolbox.ui.OpenRecord
import com.pebblesoft.toolbox.ui.components.AdaptiveBody
import com.pebblesoft.toolbox.ui.components.Section
import com.pebblesoft.toolbox.ui.components.SoftCard
import com.pebblesoft.toolbox.ui.components.fullWidthItem
import kotlinx.coroutines.delay
import java.io.File

/**
 * One recording, and everything a person needs to decide what to do with it.
 *
 * Four questions get answered on this screen, in this order: who and when, what
 * the recording is WORTH, what is in it, and what she may do with it. The
 * verdict comes second on purpose — before she listens, before she shares, and
 * in a full sentence rather than a pill, because THE HALF-RECORDING LAW is worth
 * nothing if the warning is quieter than the play button.
 *
 * Playing and sharing both work on a DECRYPTED COPY in the cache, never on the
 * vault file. The copy is wiped when this screen goes away, which is also what
 * keeps THE INSPECTION TEST true: between visits there is nothing on the phone
 * for anyone to find.
 */
@Composable
fun RecordDetailScreen(
    record: CallRecord,
    opened: OpenRecord?,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var confirmingDelete by remember { mutableStateOf(false) }
    var warningBeforeShare by remember { mutableStateOf(false) }

    AdaptiveBody(spacing = 16.dp) {
        fullWidthItem { Header(record) }
        fullWidthItem { Verdict(record) }

        if (opened?.audio != null) {
            fullWidthItem { Player(opened.audio) }
            fullWidthItem { SealCard(opened.sealIntact) }
        } else if (opened != null && record.quality != Quality.NOT_CAPTURED) {
            fullWidthItem {
                Section {
                    SoftCard(tone = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            opened.failure.ifEmpty { stringResource(R.string.detail_open_failed) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        fullWidthItem { Transcript(opened?.transcript) }

        fullWidthItem {
            Section {
                if (opened?.audio != null) {
                    OutlinedButton(
                        onClick = { warningBeforeShare = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.detail_share))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedButton(
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.detail_delete))
                }
            }
        }

        fullWidthItem { Spacer(Modifier.height(24.dp)) }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.detail_delete)) },
            text = { Text(stringResource(R.string.detail_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete(); onClose() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (warningBeforeShare && opened?.audio != null) {
        AlertDialog(
            onDismissRequest = { warningBeforeShare = false },
            title = { Text(stringResource(R.string.share_warning_title)) },
            text = { Text(stringResource(R.string.share_warning_body)) },
            confirmButton = {
                TextButton(onClick = {
                    warningBeforeShare = false
                    shareCopy(context, opened.audio)
                }) { Text(stringResource(R.string.share_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { warningBeforeShare = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun Header(record: CallRecord) {
    Section {
        Text(
            record.contactName ?: record.number.ifBlank { stringResource(R.string.rec_unknown_number) },
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${stringResource(directionLabel(record.direction))} · " +
                "${formatClock(record.startedAt)} · ${formatDuration(record.durationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * What the recording is worth, said in a sentence.
 *
 * Only `BOTH_VOICES` gets the calm colour; every other verdict is drawn as a
 * warning, because the difference between evidence and audio is the whole point
 * of the product.
 */
@Composable
private fun Verdict(record: CallRecord) {
    val scheme = MaterialTheme.colorScheme
    val evidence = record.quality == Quality.BOTH_VOICES
    Section {
        SoftCard(tone = if (evidence) scheme.primaryContainer else scheme.errorContainer) {
            Text(
                stringResource(qualityHeadline(record.quality)),
                style = MaterialTheme.typography.titleMedium,
                color = if (evidence) scheme.onPrimaryContainer else scheme.onErrorContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(qualityExplanation(record.quality)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (evidence) scheme.onPrimaryContainer else scheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun Player(audio: File) {
    val player = remember(audio) {
        runCatching {
            MediaPlayer().apply {
                setDataSource(audio.absolutePath)
                prepare()
            }
        }.getOrNull()
    }
    var playing by remember(audio) { mutableStateOf(false) }
    var position by remember(audio) { mutableStateOf(0) }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    LaunchedEffect(playing) {
        while (playing && player != null) {
            position = runCatching { player.currentPosition }.getOrDefault(0)
            if (!player.isPlaying) playing = false
            delay(PROGRESS_TICK_MS)
        }
    }

    if (player == null) return
    val duration = player.duration.coerceAtLeast(1)

    Section {
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        if (player.isPlaying) {
                            player.pause()
                            playing = false
                        } else {
                            player.start()
                            playing = true
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (playing) R.string.detail_pause else R.string.detail_play
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (playing) R.string.detail_pause else R.string.detail_play)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = { position.toFloat() / duration },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${formatDuration(position.toLong())} / ${formatDuration(duration.toLong())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SealCard(intact: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Section {
        SoftCard(tone = if (intact) scheme.surface else scheme.errorContainer) {
            Text(
                stringResource(if (intact) R.string.detail_seal_ok else R.string.detail_seal_broken),
                style = MaterialTheme.typography.titleSmall,
                color = if (intact) scheme.onSurface else scheme.onErrorContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.detail_seal_explain),
                style = MaterialTheme.typography.bodySmall,
                color = if (intact) scheme.onSurfaceVariant else scheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun Transcript(text: String?) {
    Section(stringResource(R.string.detail_transcript)) {
        SoftCard {
            Text(
                text ?: stringResource(R.string.detail_transcript_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = if (text == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Hand out the temporary copy, never the vault file, and only through a granted
 * URI the receiving app loses as soon as it is done.
 */
private fun shareCopy(context: android.content.Context, audio: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", audio)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "audio/wav"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

private fun directionLabel(direction: Direction): Int = when (direction) {
    Direction.INCOMING -> R.string.rec_incoming
    Direction.OUTGOING -> R.string.rec_outgoing
    Direction.UNKNOWN -> R.string.rec_direction_unknown
}

private fun qualityHeadline(quality: Quality): Int = when (quality) {
    Quality.BOTH_VOICES -> R.string.rec_quality_evidence
    Quality.ONE_VOICE -> R.string.rec_quality_one_voice
    Quality.UNVERIFIED -> R.string.rec_quality_unverified
    Quality.FAILED -> R.string.rec_quality_failed
    Quality.NOT_CAPTURED -> R.string.rec_quality_not_captured
}

private fun qualityExplanation(quality: Quality): Int = when (quality) {
    Quality.BOTH_VOICES -> R.string.detail_quality_both
    Quality.ONE_VOICE -> R.string.detail_quality_one
    Quality.UNVERIFIED -> R.string.detail_quality_unverified
    Quality.FAILED -> R.string.detail_quality_failed
    Quality.NOT_CAPTURED -> R.string.detail_quality_not_captured
}

/** Five ticks a second is smooth enough to read and cheap enough to ignore. */
private const val PROGRESS_TICK_MS = 200L
