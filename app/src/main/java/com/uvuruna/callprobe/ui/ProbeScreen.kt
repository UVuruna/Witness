package com.uvuruna.callprobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
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
import com.uvuruna.callprobe.audio.Heard
import com.uvuruna.callprobe.audio.ProbeSource
import com.uvuruna.callprobe.audio.RecordingEntry
import com.uvuruna.callprobe.listen.ListenLog

private val RED = Color(0xFFD32F2F)
private val AMBER = Color(0xFFF57C00)
private val GREEN = Color(0xFF2E7D32)

/**
 * The whole probe UI, one scrolling screen: pick an AudioSource (ordinary or
 * privileged), choose the audio route, record manually or arm call-auto mode,
 * then PLAY each recording back and record by ear who was actually captured —
 * which is the M0 answer no amplitude number can give.
 */
@Composable
fun ProbeScreen(
    vm: ProbeViewModel,
    onRequestManual: (Int) -> Unit,
    onRequestAuto: (Boolean, Int) -> Unit,
    onRequestListen: (Boolean) -> Unit,
    onExemptBattery: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var sourceIdx by remember { mutableIntStateOf(0) }
    val source = vm.sources[sourceIdx]

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Call Audio Probe", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Measures what each audio source delivers during a real phone call. " +
                        "Record one call per source, then play it back and say who you hear. " +
                        "A number can prove sound arrived; only your ear proves whose it was.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SourcePicker(
                sources = vm.sources,
                selected = sourceIdx,
                enabled = !state.recording,
                onSelect = { sourceIdx = it },
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelMeter(state.level, state.recording)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            if (state.recording) vm.stop() else onRequestManual(source.id)
                        },
                    ) { Text(if (state.recording) "Stop" else "Record now") }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = state.autoMode,
                            onCheckedChange = { onRequestAuto(it, source.id) },
                        )
                        Text("  Auto on call", fontSize = 13.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.speaker,
                        onCheckedChange = { vm.setSpeaker(it) },
                        enabled = !state.recording,
                    )
                    Text("  Force loudspeaker", fontSize = 13.sp)
                }
                Text(
                    "Speaker on and speaker off are two different measurements — " +
                        "run every source both ways.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.recording) {
                    Text(
                        "● recording via ${state.source}",
                        color = RED,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        item {
            ListenCard(
                state = state,
                onToggle = onRequestListen,
                onExemptBattery = onExemptBattery,
                onClearLog = { vm.clearListenLog() },
            )
        }

        item {
            Text("Recordings", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }

        items(state.recordings) { entry ->
            RecordingCard(
                entry = entry,
                playing = state.playing == entry.wav.absolutePath,
                onPlay = { vm.play(entry) },
                onHeard = { vm.setHeard(entry, it) },
                onDelete = { vm.delete(entry) },
            )
        }
    }
}

/**
 * The source registry as chips, ordinary above and privileged below. The
 * privileged four are expected to be refused for an ordinary app — they are
 * offered anyway, because a recorded refusal is a measurement and an assumed
 * one is not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcePicker(
    sources: List<ProbeSource>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Audio source", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sources.forEachIndexed { i, s ->
                if (!s.privileged) {
                    FilterChip(
                        selected = i == selected,
                        onClick = { onSelect(i) },
                        label = { Text(s.name, fontSize = 11.sp) },
                        enabled = enabled,
                    )
                }
            }
        }
        Text(
            "Privileged — reserved for the platform; expected to be refused",
            fontSize = 11.sp,
            color = AMBER,
            modifier = Modifier.padding(top = 4.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sources.forEachIndexed { i, s ->
                if (s.privileged) {
                    FilterChip(
                        selected = i == selected,
                        onClick = { onSelect(i) },
                        label = { Text(s.name, fontSize = 11.sp) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

/**
 * The M0.5 panel: arm hours-long listening (levels only, no audio written),
 * watch the live state, and read the last session's verdict — battery drain
 * per hour and every gap where the OS suspended the listener.
 */
@Composable
private fun ListenCard(
    state: ProbeState,
    onToggle: (Boolean) -> Unit,
    onExemptBattery: () -> Unit,
    onClearLog: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Listen mode (M0.5)", fontWeight = FontWeight.SemiBold)
                Switch(checked = state.listening, onCheckedChange = onToggle)
            }
            Text(
                "Keeps the mic open for hours like the future SOS listener — " +
                    "no audio saved, only battery and uptime. Arm it, unplug, leave overnight.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.listening) {
                LevelMeter(state.listenLevel, true)
                Text(
                    "listening ${state.listenMinutes} min · ${state.listenLoud} loud events · " +
                        "green mic dot should be showing in the status bar",
                    fontSize = 12.sp,
                    color = GREEN,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (state.listenError != null) {
                Text("FAILED to open mic: ${state.listenError}", color = RED, fontSize = 12.sp)
            }

            if (!state.batteryExempt) {
                Button(onClick = onExemptBattery) {
                    Text("Allow background run (battery exemption)", fontSize = 12.sp)
                }
                Text(
                    "Without this Samsung may kill the listener — the probe measures that too.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val s = state.listenSummary
            if (s != null) {
                val v = listenVerdict(s)
                Text(v.first, color = v.second, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "last session: ${s.durationMin} min · ${s.heartbeats} heartbeats · " +
                        "${s.gaps} gaps · battery ${s.batteryStart}% → ${s.batteryEnd}%" +
                        (s.drainPerHour?.let { " · %.1f%%/h".format(it) } ?: "") +
                        (if (s.charged) " · CHARGER SEEN — drain invalid" else "") +
                        " · ${s.loudEvents} loud",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "clear log", color = RED, fontSize = 12.sp,
                    modifier = Modifier.clickable { onClearLog() },
                )
            }
        }
    }
}

/** Turns a listen-session summary into the plain M0.5 verdict. */
private fun listenVerdict(s: ListenLog.Summary): Pair<String, Color> = when {
    s.gaps > 0 -> "OS INTERRUPTED the listener ${s.gaps}x" to RED
    s.charged -> "session ran on charger — repeat unplugged for the battery answer" to AMBER
    s.drainPerHour == null -> "session too short — leave it running 6+ hours" to AMBER
    s.drainPerHour <= 2.0 -> "SUSTAINABLE — %.1f%%/h, uninterrupted".format(s.drainPerHour) to GREEN
    s.drainPerHour <= 5.0 -> "COSTLY — %.1f%%/h, needs design work".format(s.drainPerHour) to AMBER
    else -> "TOO EXPENSIVE — %.1f%%/h".format(s.drainPerHour) to RED
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
                        if (frac > 0.02f) GREEN else Color(0xFFBDBDBD),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordingCard(
    entry: RecordingEntry,
    playing: Boolean,
    onPlay: () -> Unit,
    onHeard: (Heard) -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(entry.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text("delete", color = RED, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp).clickable { onDelete() })
            }
            if (entry.error != null) {
                Text("REFUSED: ${entry.error}", color = RED, fontSize = 12.sp)
            } else {
                val v = signalVerdict(entry)
                Text(v.first, color = v.second, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Text(
                "${entry.sourceName} · ${entry.route} · ${entry.kind} · " +
                    "${entry.durationMs / 1000}s · ${entry.sizeBytes / 1024} KB · " +
                    "peak ${entry.peak} · ${(entry.silentRatio * 100).toInt()}% silent",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (entry.error == null && entry.durationMs > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onPlay) {
                        Text(if (playing) "Stop" else "Play", fontSize = 12.sp)
                    }
                    Text(
                        "listen, then say who you hear:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Heard.BOTH, Heard.ME, Heard.THEM, Heard.NOTHING).forEach { h ->
                        FilterChip(
                            selected = entry.heard == h,
                            onClick = { onHeard(h) },
                            label = { Text(h.label, fontSize = 11.sp) },
                        )
                    }
                }
                Text(
                    "heard: ${entry.heard.label}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (entry.heard == Heard.BOTH) GREEN else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** What the NUMBERS say — sound arrived or it did not. Whose voice it was is
 *  the owner's verdict, stored separately. */
private fun signalVerdict(e: RecordingEntry): Pair<String, Color> = when {
    e.peak < 60 -> "SILENT — device gave the mic nothing" to RED
    e.silentRatio > 0.7 -> "MOSTLY SILENT — likely muted during the call" to AMBER
    else -> "SIGNAL PRESENT — play it back to hear whose" to GREEN
}
