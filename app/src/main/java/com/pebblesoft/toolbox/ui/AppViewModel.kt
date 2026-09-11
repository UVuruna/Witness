package com.pebblesoft.toolbox.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pebblesoft.toolbox.app
import com.pebblesoft.toolbox.capture.CaptureRegistry
import com.pebblesoft.toolbox.capture.CaptureSource
import com.pebblesoft.toolbox.data.CallRecord
import com.pebblesoft.toolbox.data.DefaultRule
import com.pebblesoft.toolbox.data.NumberRule
import com.pebblesoft.toolbox.data.RuleMode
import com.pebblesoft.toolbox.data.UnknownRule
import com.pebblesoft.toolbox.data.normalizeNumber
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the screens need, in one shape. */
data class AppState(
    val records: List<CallRecord> = emptyList(),
    val whitelist: List<NumberRule> = emptyList(),
    val blacklist: List<NumberRule> = emptyList(),
    val defaultRule: DefaultRule = DefaultRule.RECORD_EVERYTHING,
    val unknownRule: UnknownRule = UnknownRule.RECORD,
    val captureStatus: CaptureSource.Status = CaptureSource.Status.UNAVAILABLE,
    val captureLabel: String? = null,
)

/**
 * The state holder behind every screen.
 *
 * It owns no logic of its own beyond shaping: whether a call is recorded is
 * decided by [com.pebblesoft.toolbox.rules.RecordingPolicy], what the phone can
 * do is decided by [CaptureRegistry]. This class only turns those into
 * something a composable can draw.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.app
    private val records = container.db.records()
    private val rules = container.db.rules()
    private val prefs = container.prefs

    val state: StateFlow<AppState> = combine(
        records.observeAll(),
        rules.observeAll(),
        prefs.defaultRule,
        prefs.unknownRule,
    ) { allRecords, allRules, defaultRule, unknownRule ->
        val source = CaptureRegistry.candidate(application)
        AppState(
            records = allRecords,
            whitelist = allRules.filter { it.mode == RuleMode.ALWAYS_RECORD },
            blacklist = allRules.filter { it.mode == RuleMode.NEVER_RECORD },
            defaultRule = defaultRule,
            unknownRule = unknownRule,
            captureStatus = source?.status(application) ?: CaptureSource.Status.UNAVAILABLE,
            captureLabel = source?.label,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    val recordCount: StateFlow<Int> =
        records.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val storageBytes: StateFlow<Long> = records.observeAll()
        .map { list -> list.sumOf { it.audioBytes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun addRule(rawNumber: String, label: String?, mode: RuleMode) {
        val number = normalizeNumber(rawNumber)
        if (number.isEmpty()) return
        viewModelScope.launch {
            rules.upsert(NumberRule(number, label?.takeIf { it.isNotBlank() }, mode, nowMillis()))
        }
    }

    fun removeRule(rule: NumberRule) {
        viewModelScope.launch { rules.remove(rule.number) }
    }

    fun setDefaultRule(value: DefaultRule) {
        viewModelScope.launch { prefs.setDefaultRule(value) }
    }

    fun setUnknownRule(value: UnknownRule) {
        viewModelScope.launch { prefs.setUnknownRule(value) }
    }

    fun deleteRecord(record: CallRecord) {
        viewModelScope.launch {
            container.vault.delete(record.audioFile)
            record.transcriptFile?.let(container.vault::delete)
            records.delete(record)
        }
    }

    /** Drop every decrypted copy the moment the screen showing it goes away. */
    fun closeOpenCopies() {
        container.vault.purgeScratch()
    }

    private fun nowMillis(): Long = System.currentTimeMillis()
}
