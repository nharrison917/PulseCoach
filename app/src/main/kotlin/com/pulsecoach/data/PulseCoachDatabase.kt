package com.pulsecoach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The Room database for PulseCoach.
 *
 * @Database declares which entities (tables) exist and the schema version number.
 * IMPORTANT: increment [version] and provide a Migration whenever you add or change
 * a table column — otherwise Room will crash on existing installs.
 */
@Database(
    entities = [ZoneConfigEntity::class],
    version = 1,
    exportSchema = false // set to true later if you want schema change history files
)
abstract class PulseCoachDatabase : RoomDatabase() {

    /** Room generates this implementation — we just declare the abstract function. */
    abstract fun zoneConfigDao(): ZoneConfigDao

    companion object {
        // @Volatile ensures writes to `instance` are immediately visible to all threads.
        @Volatile private var instance: PulseCoachDatabase? = null

        /**
         * Returns the singleton database instance, creating it on first call.
         * synchronized() prevents two threads from creating the database simultaneously.
         */
        fun getInstance(context: Context): PulseCoachDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PulseCoachDatabase::class.java,
                    "pulsecoach_db"
                ).build().also { instance = it }
            }
    }
}
