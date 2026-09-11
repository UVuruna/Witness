package com.pebblesoft.toolbox.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordDao {

    /** Newest first — the data screen groups these by contact, then by time. */
    @Query("SELECT * FROM call_records ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE id = :id")
    fun observe(id: Long): Flow<CallRecord?>

    @Query("SELECT * FROM call_records WHERE transcriptState = :state ORDER BY startedAt ASC")
    suspend fun withTranscriptState(state: TranscriptState): List<CallRecord>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: CallRecord): Long

    @Query("UPDATE call_records SET transcriptState = :state, transcriptFile = :file WHERE id = :id")
    suspend fun setTranscript(id: Long, state: TranscriptState, file: String?)

    @Query("UPDATE call_records SET quality = :quality WHERE id = :id")
    suspend fun setQuality(id: Long, quality: Quality)

    @Delete
    suspend fun delete(record: CallRecord)

    @Query("SELECT COUNT(*) FROM call_records")
    fun observeCount(): Flow<Int>
}

@Dao
interface NumberRuleDao {

    @Query("SELECT * FROM number_rules ORDER BY label IS NULL, label ASC, number ASC")
    fun observeAll(): Flow<List<NumberRule>>

    @Query("SELECT * FROM number_rules WHERE number = :number LIMIT 1")
    suspend fun find(number: String): NumberRule?

    @Upsert
    suspend fun upsert(rule: NumberRule)

    @Query("DELETE FROM number_rules WHERE number = :number")
    suspend fun remove(number: String)
}
