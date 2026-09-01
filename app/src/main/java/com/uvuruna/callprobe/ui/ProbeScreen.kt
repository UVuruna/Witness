package com.uvuruna.callprobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uvuruna.callprobe.audio.RecordingEntry

/**
 * The whole probe UI, one screen: pick an AudioSource, record manually or arm
 * call-auto mode, watch the live level, and read the per-recording verdict
 * (peak / silence ratio) that answers the M0 question.
 */
@Composable
fun ProbeScreen(
    vm: ProbeViewModel,
    onRequestManual: (Int) -> Unit,
    onRequestAuto: (Boolean, Int) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var sourceIdx by remember { mutableIntStateOf(0) }
    val source = vm.sources[sourceIdx].second

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Call Audio Probe", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Measures what the microphone captures during a phone call. " +
                "Put the call on speakerphone, arm auto mode, and read the verdict.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Audio source", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.sources.forEachIndexed { i, (name, _) ->
                FilterChip(
                    selected = i == sourceIdx,
                    onClick = { sourceIdx = i },
                    label = { Text(name, fontSize = 11.sp) },
                    enabled = !state.recording,
                )
            }
        }

        LevelMeter(state.level, state.recording)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { if (state.recording) vm.stop() else onRequestManual(source) },
            ) { Text(if (state.recording) "Stop" else "Record now") }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = state.autoMode,
                    onCheckedChange = { onRequestAuto(it, source) },
                )
                Text("  Auto on call", fontSize = 13.sp)
            }
        }

        if (state.recording) {
            Text(
                "● recording via ${state.source}",
                color = Color(0xFFD32F2F),
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text("Recordings", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.recordings) { entry ->
                RecordingCard(entry, onDelete = { vm.delete(entry) })
            }
        }
    }
}

@Composable
private fun LevelMeter(level: Int, recording: Boolean) {
    val frac = (level / 32767f).coerceIn(0f, 1f)
    Column {
        Box(
            Modifier.fillMaxWidth().height(24.dp)
                .background(Color(0xFFE0E0E0), RoundedCornerShape(4.dp)),
        ) {
            Box(
                Modifier.fillMaxWidth(frac).height(24.dp)
                    .background(
                        if (frac > 0.02f) Color(0xFF2E7D32) else Color(0xFFBDBDBD),
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
        Text(
            if (recording && frac <= 0.02f) "silent — mic hears nothing" else "peak $level / 32767",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordingCard(entry: RecordingEntry, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(entry.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text("delete", color = Color(0xFFD32F2F), fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp).clickable { onDelete() })
            }
            if (entry.error != null) {
                Text("FAILED: ${entry.error}", color = Color(0xFFD32F2F), fontSize = 12.sp)
            } else {
                val verdict = verdict(entry)
                Text(verdict.first, color = verdict.second, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Text(
                "${entry.sourceName} · ${entry.kind} · ${entry.durationMs / 1000}s · " +
                    "${entry.sizeBytes / 1024} KB · peak ${entry.peak} · " +
                    "${(entry.silentRatio * 100).toInt()}% silent",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Turns the measured stats into a plain human verdict for the M0 question. */
private fun verdict(e: RecordingEntry): Pair<String, Color> = when {
    e.peak < 60 -> "SILENT — device gave the mic nothing" to Color(0xFFD32F2F)
    e.silentRatio > 0.7 -> "MOSTLY SILENT — likely muted during call" to Color(0xFFF57C00)
    else -> "AUDIO CAPTURED — mic heard sound" to Color(0xFF2E7D32)
}
