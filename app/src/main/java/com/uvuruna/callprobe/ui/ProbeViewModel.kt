package com.uvuruna.callprobe.ui

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uvuruna.callprobe.audio.RecordingEntry
import com.uvuruna.callprobe.audio.RecordingStore
import com.uvuruna.callprobe.call.CallMonitor
import com.uvuruna.callprobe.listen.ListenLog
import com.uvuruna.callprobe.listen.ListenService
import com.uvuruna.callprobe.service.RecorderService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProbeState(
    val recording: Boolean = false,
    val level: Int = 0,             // live peak 0..32767
    val source: String = "",
    val autoMode: Boolean = false,  // auto-record when a call is active
    val recordings: List<RecordingEntry> = emptyList(),
    val listening: Boolean = false, // M0.5 continuous-listening probe
    val listenLevel: Int = 0,
    val listenMinutes: Long = 0,
    val listenLoud: Int = 0,
    val listenError: String? = null,
    val listenSummary: ListenLog.Summary? = null,
    val batteryExempt: Boolean = false,
)

/**
 * Drives the probe screen: starts/stops the RecorderService with a chosen
 * AudioSource, polls its live level, toggles the call-triggered auto mode,
 * and reloads the finished-recording list.
 */
class ProbeViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ProbeState())
    val state: StateFlow<ProbeState> = _state.asStateFlow()

    val sources = RecordingStore.SOURCES

    private var monitor: CallMonitor? = null

    init {
        refresh()
        viewModelScope.launch {
            var ticks = 0
            while (true) {
                val now = System.currentTimeMillis()
                _state.value = _state.value.copy(
                    recording = RecorderService.recording,
                    level = RecorderService.level,
                    source = RecorderService.currentSource,
                    listening = ListenService.listening,
                    listenLevel = ListenService.level,
                    listenMinutes = if (ListenService.listening)
                        (now - ListenService.startedAtMs) / 60_000 else 0,
                    listenLoud = ListenService.loudEvents,
                    listenError = ListenService.lastError,
                )
                if (!RecorderService.recording && _state.value.level != 0) refresh()
                // The summary reads a file — refresh it every 10 s, not every tick.
                if (ticks++ % 100 == 0) refresh()
                delay(100)
            }
        }
    }

    fun startManual(source: Int) {
        RecorderService.start(getApplication(), source, "manual")
    }

    fun stop() {
        RecorderService.stop(getApplication())
        viewModelScope.launch { delay(400); refresh() }
    }

    @RequiresPermission(android.Manifest.permission.READ_PHONE_STATE)
    fun setAutoMode(enabled: Boolean, source: Int) {
        _state.value = _state.value.copy(autoMode = enabled)
        if (enabled) {
            val m = CallMonitor(
                getApplication(),
                onCallStart = { RecorderService.start(getApplication(), source, "call") },
                onCallEnd = {
                    RecorderService.stop(getApplication())
                    viewModelScope.launch { delay(400); refresh() }
                },
            )
            m.start()
            monitor = m
        } else {
            monitor?.stop()
            monitor = null
        }
    }

    fun setListenMode(enabled: Boolean) {
        val app: Application = getApplication()
        if (enabled) ListenService.start(app) else ListenService.stop(app)
        viewModelScope.launch { delay(400); refresh() }
    }

    fun clearListenLog() {
        ListenLog.clear(getApplication())
        refresh()
    }

    fun delete(entry: RecordingEntry) {
        RecordingStore.delete(entry)
        refresh()
    }

    fun refresh() {
        val app: Application = getApplication()
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        _state.value = _state.value.copy(
            recordings = RecordingStore.list(app),
            listenSummary = ListenLog.summary(app),
            batteryExempt = pm.isIgnoringBatteryOptimizations(app.packageName),
        )
    }

    override fun onCleared() {
        monitor?.stop()
        super.onCleared()
    }
}
