package com.pebblesoft.toolbox.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * The vault's index.
 *
 * Room holds only what points at the evidence — never the audio itself, which
 * lives encrypted in [com.pebblesoft.toolbox.vault.Vault]. The database file is
 * app-private, so a file manager on the phone shows nothing.
 */
@Database(
    entities = [CallRecord::class, NumberRule::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class Db : RoomDatabase() {

    abstract fun records(): CallRecordDao
    abstract fun rules(): NumberRuleDao

    companion object {
        @Volatile private var instance: Db? = null

        fun get(context: Context): Db = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, Db::class.java, "toolbox.db"
            ).build().also { instance = it }
        }
    }
}

class Converters {
    @TypeConverter fun directionToString(v: Direction): String = v.name
    @TypeConverter fun stringToDirection(v: String): Direction = Direction.valueOf(v)

    @TypeConverter fun qualityToString(v: Quality): String = v.name
    @TypeConverter fun stringToQuality(v: String): Quality = Quality.valueOf(v)

    @TypeConverter fun transcriptToString(v: TranscriptState): String = v.name
    @TypeConverter fun stringToTranscript(v: String): TranscriptState = TranscriptState.valueOf(v)

    @TypeConverter fun ruleModeToString(v: RuleMode): String = v.name
    @TypeConverter fun stringToRuleMode(v: String): RuleMode = RuleMode.valueOf(v)
}
