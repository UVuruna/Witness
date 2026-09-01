package com.uvuruna.callprobe.ui

import android.app.Application
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uvuruna.callprobe.audio.RecordingEntry
import com.uvuruna.callprobe.audio.RecordingStore
import com.uvuruna.callprobe.call.CallMonitor
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
            while (true) {
                _state.value = _state.value.copy(
                    recording = RecorderService.recording,
                    level = RecorderService.level,
                    source = RecorderService.currentSource,
                )
                if (!RecorderService.recording && _state.value.level != 0) refresh()
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

    fun delete(entry: RecordingEntry) {
        RecordingStore.delete(entry)
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(recordings = RecordingStore.list(getApplication()))
    }

    override fun onCleared() {
        monitor?.stop()
        super.onCleared()
    }
}
