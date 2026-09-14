package com.pebblesoft.toolbox.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Which way the call went. UNKNOWN covers calls the platform never attributes. */
enum class Direction { INCOMING, OUTGOING, UNKNOWN }

/**
 * What the recording is worth as evidence.
 *
 * THE HALF-RECORDING LAW (CLAUDE.md): a file holding one side of a conversation
 * is not a smaller version of the product, it is not the product. The app may
 * never present such a file as evidence, so every record carries this verdict
 * and the UI shows it as loudly as the recording itself.
 *
 * Every value here is a MEASUREMENT, never an assumption — `capture.VoiceCheck`
 * is the only thing allowed to write one, and it decides from two channels that
 * differ or from the silent window of the guided test call. The verdict the
 * previous round used, "the file is not silent", is not in this list, because
 * one person shouting satisfies it.
 */
enum class Quality {
    /** Two different people measured in the file — this is evidence. */
    BOTH_VOICES,

    /** Measured: the far party never reached the file. Not evidence, and said so. */
    ONE_VOICE,

    /** Produced, but nothing has proven who is in it. Not evidence, and said so. */
    UNVERIFIED,

    /** The capture did not deliver a usable conversation. Never shown as evidence. */
    FAILED,

    /**
     * The app saw the conversation happen and could not record it — a call
     * inside another app while the loudspeaker route was off. There is no file.
     * A gap the user can see beats a gap she cannot.
     */
    NOT_CAPTURED,
}

/** Where the transcript stands for this recording. */
enum class TranscriptState { NONE, PENDING, DONE, FAILED }

/**
 * One recorded call — the row in the vault index.
 *
 * The bytes live encrypted in [com.pebblesoft.toolbox.vault.Vault]; this row is
 * only the index and the seal. [sha256] is taken at file close, before anything
 * else may read it, so a later edit of the file can always be proven.
 */
@Entity(
    tableName = "call_records",
    indices = [Index("number"), Index("startedAt")],
)
data class CallRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** The other party's number, as dialled or received. Empty when withheld. */
    val number: String,

    /** Contact name resolved when the call happened; null if not in contacts. */
    val contactName: String? = null,

    val direction: Direction,
    val startedAt: Long,
    val durationMs: Long,

    /** File name inside the vault — never a path outside app-private storage. */
    val audioFile: String,
    val audioBytes: Long,

    /** The seal: hash of the encrypted payload, and when it was taken. */
    val sha256: String,
    val sealedAt: Long,

    val quality: Quality = Quality.UNVERIFIED,

    /** Which capture mechanism produced it — so a failure can be traced to its path. */
    val capturedBy: String,

    val transcriptState: TranscriptState = TranscriptState.NONE,
    val transcriptFile: String? = null,
)
