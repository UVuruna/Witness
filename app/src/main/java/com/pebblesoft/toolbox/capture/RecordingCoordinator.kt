package com.pebblesoft.toolbox.capture

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.pebblesoft.toolbox.app
import com.pebblesoft.toolbox.data.CallRecord
import com.pebblesoft.toolbox.data.Direction
import com.pebblesoft.toolbox.data.Quality
import com.pebblesoft.toolbox.data.TranscriptState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Where the guided test call stands, so the setup screen can narrate it. */
enum class TestPhase { IDLE, ARMED, RECORDING, FINISHED }

data class TestState(
    val phase: TestPhase = TestPhase.IDLE,
    val route: VoiceCheck.Route = VoiceCheck.Route.UNTESTED,
    val sourceId: String = "",
    val failure: String = "",
)

/**
 * The conductor: it decides, records, judges, seals and files — in that order.
 *
 * One call in, one row out. Three things it does that the previous round did
 * not, each of which was a way of losing the recording or lying about it:
 *
 * **It waits for the number before it judges.** Android 12 stopped handing the
 * caller's number to the call-state listener, so a decision taken at OFFHOOK is
 * taken blind. Recording starts anyway and the lists are applied again at the
 * end, against the number [CallIdentity] reads back from the call log. A call
 * the user put on the never-record list leaves nothing behind — the file is
 * deleted and no row is written — but a call whose number only arrives late is
 * no longer silently filed as an outgoing call to nobody.
 *
 * **It never guesses the quality.** [VoiceCheck] decides from what was measured
 * while the bytes were written. A file whose far side never arrived is marked
 * [Quality.ONE_VOICE] and is never handed on as evidence, and an unmeasurable
 * one is [Quality.UNVERIFIED] rather than promoted for being loud.
 *
 * **It holds one session at a time, under a lock.** Start and end arrive on
 * different coroutines from a platform callback; the previous shape raced on a
 * short call and could leave a recorder running with no row to show for it.
 */
class RecordingCoordinator(
    private val context: Context,
    private val routes: RouteMemory,
) {

    /** What kind of conversation a session is following. */
    private enum class Kind {
        /** An ordinary call through the phone network. */
        CARRIER,

        /** A call inside another app — no number, no call-log row, no modem audio. */
        VOIP,

        /** The guided setup call: measured, reported, then thrown away. */
        TEST,
    }

    private class Session(
        val startedAt: Long,
        val numberHint: String?,
        /** Null when no route could record this call — the row still gets written. */
        val source: CaptureSource?,
        val recorder: RouteRecorder?,
        val sink: ParcelFileDescriptor?,
        val plaintext: File?,
        val startError: String,
        val kind: Kind,
    ) {
        val sourceId: String get() = source?.id ?: "none"
    }

    private val mutex = Mutex()
    private var session: Session? = null
    private var testArmed = false
    private var testRouteId: String? = null

    /**
     * True from the moment telephony goes off-hook to the moment it goes idle,
     * whatever the lists decided.
     *
     * [VoipWatcher] needs to know that a conversation is a carrier call, and the
     * platform is the wrong place to ask: the audio mode moves for both kinds,
     * and the call-state getter is served by Telecom, which on modern Android
     * also sees the self-managed connections WhatsApp, Signal and Teams
     * register — the very apps the VoIP route exists for. Asking there could
     * silence VoIP detection for exactly them. The app's own eyes cannot be
     * wrong about this, so they are the source of truth.
     */
    @Volatile var carrierCallInProgress: Boolean = false
        private set

    private val _test = MutableStateFlow(TestState())
    val test: StateFlow<TestState> = _test

    /**
     * Arm the guided test.
     *
     * @param routeId the route to measure, or null for the best available one.
     * The loudspeaker route for calls inside other apps can ONLY be measured by
     * naming it: such a call never reaches the carrier path, so without this it
     * would stay untested for the life of the install and every recording it
     * made would be filed UNVERIFIED forever.
     */
    fun armTest(routeId: String? = null) {
        testArmed = true
        testRouteId = routeId
        _test.value = TestState(phase = TestPhase.ARMED, sourceId = routeId.orEmpty())
    }

    fun cancelTest() {
        testArmed = false
        testRouteId = null
        _test.value = TestState()
    }

    /**
     * A call has begun. The number may be unknown at this moment, and usually is
     * on Android 12 and later, so an explicit never-record match is the only
     * thing that stops capture here; everything else is judged at the end.
     */
    suspend fun onCallStarted(numberHint: String?) {
        carrierCallInProgress = true
        begin(numberHint, if (testArmed && !testingVoip()) Kind.TEST else Kind.CARRIER)
    }

    /**
     * A conversation started inside another app.
     *
     * There is no number, no call-log row, and nothing in the modem to tap — so
     * the only route that can capture it is the loudspeaker one, and only if she
     * turned it on. When she has not, the call still becomes a row saying it was
     * not saved: a gap she can see beats a gap she cannot.
     */
    suspend fun onVoipStarted() {
        begin(numberHint = null, kind = if (testingVoip()) Kind.TEST else Kind.VOIP)
    }

    /** Telephony went idle. Ends a carrier or test session, never a VoIP one. */
    suspend fun onCallEnded() {
        carrierCallInProgress = false
        finish(Kind.VOIP)
    }

    /** The audio mode left communication. Ends a VoIP or test session only. */
    suspend fun onVoipEnded() {
        finish(Kind.CARRIER)
    }

    /**
     * The service is going away mid-conversation. Stop the recorder and file
     * what there is, rather than leaving a worker thread reading the room with
     * the loudspeaker on and a session that blocks every later call.
     */
    suspend fun abandon() {
        carrierCallInProgress = false
        finish(exceptKind = null)
    }

    private fun testingVoip(): Boolean =
        testArmed && testRouteId == SpeakerphoneCaptureSource.Variant.VOIP.id

    private suspend fun begin(numberHint: String?, kind: Kind) {
        mutex.withLock {
            val open = session
            if (open != null) {
                // Call waiting, or a conversation starting while another is
                // still open. It cannot be recorded — one recorder, one audio
                // source — but it must not vanish either, and the previous shape
                // returned here silently while the class doc promised a row.
                if (kind != Kind.TEST && open.kind != kind) {
                    fileRecord(
                        number = numberHint.orEmpty(), name = null, direction = Direction.UNKNOWN,
                        startedAt = System.currentTimeMillis(), duration = 0, bytes = 0, seal = null,
                        quality = Quality.NOT_CAPTURED, file = "", source = "busy",
                    )
                }
                return
            }

            if (kind == Kind.CARRIER && numberHint != null) {
                val early = context.app.policy.decide(numberHint, CallIdentity.isInContacts(context, numberHint))
                if (!early.record) return
            }

            val startedAt = System.currentTimeMillis()
            val source = pickSource(kind)
            val recorder = source?.recorder(context)

            if (source == null || recorder == null) {
                // Nothing can record right now — Shizuku not re-armed after a
                // reboot, or a call in another app with the loudspeaker route
                // switched off. The conversation still becomes a row.
                session = Session(startedAt, numberHint, null, null, null, null, NO_ROUTE, kind)
                return
            }

            val file = File(context.cacheDir, "cap-$startedAt.wav")
            val pfd = ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE
            )
            val error = runCatching { recorder.start(pfd) }.getOrElse { it.message ?: "the recorder was lost" }

            session = Session(startedAt, numberHint, source, recorder, pfd, file, error, kind)
            if (kind == Kind.TEST) _test.value = TestState(phase = TestPhase.RECORDING, sourceId = source.id)
        }
    }

    /**
     * A conversation is over. Stop, measure, judge, seal, file.
     *
     * @param exceptKind a session of this kind belongs to the OTHER watcher and
     * is left alone. Both watchers used to call one argument-less method, so on
     * a handset whose audio mode lingers after hang-up the mode listener could
     * end a live carrier recording, or a carrier hang-up could end a VoIP one.
     */
    private suspend fun finish(exceptKind: Kind?) {
        val finished = mutex.withLock {
            val open = session ?: return
            if (exceptKind != null && open.kind == exceptKind) return
            session = null
            open
        }
        val outcome = finished.recorder
            ?.let { active -> runCatching { active.stop() }.getOrElse { CaptureOutcome.failed(it.message.orEmpty()) } }
            ?: CaptureOutcome.failed(finished.startError)
        runCatching { finished.sink?.close() }

        if (finished.kind == Kind.TEST) {
            finishTest(finished, outcome)
            return
        }

        val duration = System.currentTimeMillis() - finished.startedAt

        // The audio mode moves for things that are not conversations — a
        // Bluetooth headset connecting, an assistant turn, a web page. Filing a
        // row for every blip fills her evidence list with calls that never
        // happened, which corrodes the one promise this feature makes.
        if (finished.kind == Kind.VOIP && duration < VOIP_FLOOR_MS && finished.plaintext == null) {
            return
        }

        // Everything below touches Room, DataStore, the keystore and the disk.
        // Any of them can throw, and the scope this runs in has no exception
        // handler — an escape would kill the process and lose the recording
        // instead of losing one field. Whatever fails, a row gets written.
        runCatching { fileFinished(finished, outcome, duration) }
            .onFailure { failure ->
                finished.plaintext?.delete()
                runCatching {
                    fileRecord(
                        number = finished.numberHint.orEmpty(), name = null,
                        direction = Direction.UNKNOWN, startedAt = finished.startedAt,
                        duration = duration, bytes = 0, seal = null, quality = Quality.FAILED,
                        file = "", source = finished.sourceId,
                    )
                }
                Log.e(TAG, "filing the finished call failed", failure)
            }
    }

    private suspend fun fileFinished(finished: Session, outcome: CaptureOutcome, duration: Long) {
        val party = if (finished.kind == Kind.CARRIER) {
            CallIdentity.resolve(context, finished.startedAt)
        } else {
            null
        }
        val number = party?.number ?: finished.numberHint.orEmpty()
        val name = party?.name ?: CallIdentity.contactName(context, number)

        if (finished.kind == Kind.CARRIER) {
            val decision = context.app.policy.decide(number, name != null)
            if (!decision.record) {
                finished.plaintext?.delete()
                return
            }
        }

        val audio = finished.plaintext
        if (finished.startError.isNotEmpty() || audio == null || !audio.exists() || audio.length() <= EMPTY_WAV) {
            audio?.delete()
            // No route at all is a different fact from a route that tried and
            // failed, and the user is owed the difference: one is a gap she can
            // close by turning something on, the other is a broken recording.
            val verdict = if (finished.source == null) Quality.NOT_CAPTURED else Quality.FAILED
            fileRecord(
                number, name, party?.direction ?: Direction.UNKNOWN, finished.startedAt, duration,
                bytes = 0, seal = null, quality = verdict, file = "", source = finished.sourceId,
            )
            return
        }

        val vault = context.app.vault
        val stored = "rec-${finished.startedAt}.wav"
        vault.openWrite(stored).use { out -> audio.inputStream().use { it.copyTo(out) } }
        val bytes = vault.sizeOf(stored)
        val seal = vault.seal(stored)
        audio.delete()

        val quality = VoiceCheck.verdict(outcome, routes.carriesBothPeople(finished.sourceId))
        fileRecord(
            number, name, party?.direction ?: Direction.UNKNOWN, finished.startedAt, duration,
            bytes, seal.sha256, quality, stored, finished.sourceId,
        )
    }

    private suspend fun finishTest(finished: Session, outcome: CaptureOutcome) {
        testArmed = false
        testRouteId = null
        finished.plaintext?.delete()
        val verdict = if (finished.startError.isNotEmpty()) {
            VoiceCheck.Route.NOTHING
        } else {
            VoiceCheck.readTestCall(outcome, QUIET_FROM_SLOT, QUIET_TO_SLOT)
        }
        finished.source?.let { routes.remember(it.id, verdict) }
        _test.value = TestState(
            phase = TestPhase.FINISHED,
            route = verdict,
            sourceId = finished.sourceId,
            failure = finished.startError.ifEmpty { outcome.error },
        )
    }

    /**
     * The best route that can record this second.
     *
     * A call inside another app has nothing in the modem to tap, so the
     * privileged route is not a candidate there however well it is set up — only
     * the loudspeaker one can hear that conversation at all.
     */
    private fun pickSource(kind: Kind): CaptureSource? {
        val voipRoute = SpeakerphoneCaptureSource.Variant.VOIP.id

        // A test names the route it is measuring; without that the loudspeaker
        // route for other apps could never be proven, and everything it recorded
        // would stay UNVERIFIED for the life of the install.
        testRouteId?.takeIf { kind == Kind.TEST }?.let { wanted ->
            return CaptureRegistry.byId(wanted)?.takeIf { it.recorder(context) != null }
        }

        val candidates = CaptureRegistry.usable(context).filter { source ->
            if (kind == Kind.VOIP) source.id == voipRoute else source.id != voipRoute
        }
        return candidates.firstOrNull { it.recorder(context) != null }
    }

    private suspend fun fileRecord(
        number: String, name: String?, direction: Direction, startedAt: Long,
        duration: Long, bytes: Long, seal: String?, quality: Quality, file: String, source: String,
    ) {
        context.app.db.records().insert(
            CallRecord(
                number = number,
                contactName = name,
                direction = direction,
                startedAt = startedAt,
                durationMs = duration,
                audioFile = file,
                audioBytes = bytes,
                sha256 = seal.orEmpty(),
                sealedAt = System.currentTimeMillis(),
                quality = quality,
                capturedBy = source,
                transcriptState = if (quality == Quality.BOTH_VOICES) {
                    TranscriptState.PENDING
                } else {
                    TranscriptState.NONE
                },
            )
        )
    }

    private companion object {
        /** A WAV holding only its 44-byte header never held a conversation. */
        const val EMPTY_WAV = 44L

        /** Why a call produced nothing when no route was armed at the time. */
        const val NO_ROUTE = "no capture route was ready"

        /**
         * Below this, a communication-mode session was not a conversation —
         * a headset connecting, an assistant turn, a notification sound.
         */
        const val VOIP_FLOOR_MS = 5_000L

        const val TAG = "Capture"

        // The test call asks her to stay quiet from the fifth to the fifteenth
        // second while the other side keeps talking. Timeline slots are a
        // quarter second each, and sound inside that window is the far party.
        const val QUIET_FROM_SLOT = 20
        const val QUIET_TO_SLOT = 60
    }
}
