package com.pebblesoft.toolbox.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pebblesoft.toolbox.app
import com.pebblesoft.toolbox.capture.CaptureRegistry
import com.pebblesoft.toolbox.capture.CaptureService
import com.pebblesoft.toolbox.capture.CaptureSource
import com.pebblesoft.toolbox.capture.TestState
import com.pebblesoft.toolbox.data.CallRecord
import com.pebblesoft.toolbox.data.DefaultRule
import com.pebblesoft.toolbox.data.NumberRule
import com.pebblesoft.toolbox.data.RuleMode
import com.pebblesoft.toolbox.data.UnknownRule
import com.pebblesoft.toolbox.data.normalizeNumber
import com.pebblesoft.toolbox.permissions.AppPermission
import com.pebblesoft.toolbox.permissions.RuntimePermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the screens need, in one shape. */
data class AppState(
    val records: List<CallRecord> = emptyList(),
    val whitelist: List<NumberRule> = emptyList(),
    val blacklist: List<NumberRule> = emptyList(),
    val defaultRule: DefaultRule = DefaultRule.RECORD_EVERYTHING,
    val unknownRule: UnknownRule = UnknownRule.RECORD,
    val captureStatus: CaptureSource.Status = CaptureSource.Status.UNAVAILABLE,
    val captureLabel: String? = null,
    /** Permissions still missing that stop recording outright. */
    val missingPermissions: List<AppPermission> = emptyList(),
    /** Empty while the always-on ear is listening; otherwise why it is not. */
    val serviceProblem: String = "",
    val speakerphoneCalls: Boolean = false,
    val speakerphoneVoip: Boolean = false,
)

/**
 * One recording, opened.
 *
 * The audio is a DECRYPTED COPY in the cache — the only form a media player or a
 * share sheet can take — and it exists only while the detail screen is on
 * screen. [sealIntact] is checked against the hash stored on the row at the
 * moment the file was closed, so the screen can say whether this is still
 * evidence rather than merely audio.
 */
data class OpenRecord(
    val audio: java.io.File? = null,
    val sealIntact: Boolean = false,
    val transcript: String? = null,
    val failure: String = "",
)

/** The four list-shaped things the data layer streams, carried together. */
private data class Lists(
    val records: List<CallRecord>,
    val rules: List<NumberRule>,
    val defaultRule: DefaultRule,
    val unknownRule: UnknownRule,
)

/**
 * The state holder behind every screen.
 *
 * It owns no logic of its own beyond shaping: whether a call is recorded is
 * decided by [com.pebblesoft.toolbox.rules.RecordingPolicy], what the phone can
 * do is decided by [CaptureRegistry], and what it PROVED is decided by the test
 * call. This class only turns those into something a composable can draw.
 *
 * The previous round computed the capture status inside a `combine` of four
 * database flows, so the home screen could only change its mind when a record or
 * a rule changed. Granting a permission, arming Shizuku or finishing the test
 * call left the screen saying the opposite of the truth until something
 * unrelated happened. The liveness sources are part of the combine now, and
 * [refresh] covers the one thing Android never streams — a permission result.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.app
    private val records = container.db.records()
    private val rules = container.db.rules()
    private val prefs = container.prefs

    private val refreshes = MutableStateFlow(0L)

    private val lists = combine(
        records.observeAll(),
        rules.observeAll(),
        prefs.defaultRule,
        prefs.unknownRule,
        ::Lists,
    )

    private val liveness = combine(
        container.routes.changes,
        container.shizuku.state,
        CaptureService.running,
        CaptureService.problem,
        refreshes,
    ) { _, _, _, problem, _ -> problem }

    val state: StateFlow<AppState> = combine(lists, liveness) { data, problem ->
        val source = CaptureRegistry.candidate(application)
        AppState(
            records = data.records,
            whitelist = data.rules.filter { it.mode == RuleMode.ALWAYS_RECORD },
            blacklist = data.rules.filter { it.mode == RuleMode.NEVER_RECORD },
            defaultRule = data.defaultRule,
            unknownRule = data.unknownRule,
            captureStatus = source?.status(application) ?: CaptureSource.Status.UNAVAILABLE,
            captureLabel = source?.label(application),
            missingPermissions = RuntimePermissions.missingRequired(application),
            serviceProblem = problem,
            speakerphoneCalls = container.routes.speakerphoneForCalls,
            speakerphoneVoip = container.routes.speakerphoneForVoip,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    /** Where the guided test call stands, straight from the conductor. */
    val testState: StateFlow<TestState> = container.coordinator.test

    val recordCount: StateFlow<Int> =
        records.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val storageBytes: StateFlow<Long> = records.observeAll()
        .map { list -> list.sumOf { it.audioBytes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** Recompute everything Android does not stream — permission results, mostly. */
    fun refresh() {
        refreshes.value = refreshes.value + 1
        CaptureService.ensureRunning(getApplication())
    }

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

    fun setSpeakerphoneCalls(value: Boolean) {
        viewModelScope.launch { prefs.setSpeakerphoneCalls(value) }
    }

    fun setSpeakerphoneVoip(value: Boolean) {
        viewModelScope.launch { prefs.setSpeakerphoneVoip(value) }
    }

    /** One recording's row, live — so a delete or a transcript closes the screen. */
    fun record(id: Long): Flow<CallRecord?> = records.observe(id)

    /**
     * Decrypt a copy for playing and sharing, and check the seal while we are
     * at it. Both touch the keystore and the disk, so neither belongs on the
     * frame the screen is drawn in.
     */
    suspend fun open(record: CallRecord): OpenRecord = withContext(Dispatchers.IO) {
        if (record.audioFile.isEmpty()) {
            return@withContext OpenRecord(failure = "no audio was kept for this call")
        }
        val transcript = record.transcriptFile
            ?.let { name -> runCatching { container.vault.readText(name) }.getOrNull() }
        runCatching { container.vault.decryptToScratch(record.audioFile, "wav") }
            .fold(
                onSuccess = { copy ->
                    OpenRecord(
                        audio = copy,
                        sealIntact = container.vault.matchesSeal(record.audioFile, record.sha256),
                        transcript = transcript,
                    )
                },
                onFailure = { failed ->
                    OpenRecord(
                        sealIntact = false,
                        transcript = transcript,
                        failure = failed.message ?: "the recording could not be opened",
                    )
                },
            )
    }

    fun deleteRecord(record: CallRecord) {
        viewModelScope.launch {
            if (record.audioFile.isNotEmpty()) container.vault.delete(record.audioFile)
            record.transcriptFile?.let(container.vault::delete)
            records.delete(record)
        }
    }

    /**
     * Arm the guided test: the next call is measured, reported, and thrown away.
     *
     * @param routeId names the route to measure. The loudspeaker route for calls
     * inside other apps has to be named — such a call never reaches the
     * telephony path, so it can never be picked as "the best available route".
     */
    fun armTest(routeId: String?) {
        container.coordinator.armTest(routeId)
        refresh()
    }

    fun cancelTest() = container.coordinator.cancelTest()

    /**
     * Re-arm capture: ask Shizuku for the privilege (or open its grant dialog)
     * and make sure the always-on ear is running. This is the one button the
     * home screen offers after a reboot, and the last tap of first setup.
     */
    fun rearm() {
        container.shizuku.requestPermission()
        container.shizuku.bindRecorder()
        refresh()
    }

    /** Drop every decrypted copy the moment the screen showing it goes away. */
    fun closeOpenCopies() {
        container.vault.purgeScratch()
    }

    private fun nowMillis(): Long = System.currentTimeMillis()
}
