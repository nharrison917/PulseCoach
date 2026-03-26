package com.pulsecoach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The Room database for PulseCoach.
 *
 * Version history:
 *  1 → Initial schema: zone_config table only.
 *  2 → Added sessions and hr_samples tables (Phase 2 session recording).
 *
 * IMPORTANT: increment [version] and add a Migration object every time you add
 * or change a table column — Room will crash on existing installs if you don't.
 * Never use fallbackToDestructiveMigration() on a released build.
 */
@Database(
    entities = [
        ZoneConfigEntity::class,
        SessionEntity::class,
        HrSampleEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PulseCoachDatabase : RoomDatabase() {

    abstract fun zoneConfigDao(): ZoneConfigDao
    abstract fun sessionDao(): SessionDao
    abstract fun hrSampleDao(): HrSampleDao

    companion object {
        @Volatile private var instance: PulseCoachDatabase? = null

        /**
         * Migration from v1 to v2.
         *
         * Room maps Kotlin types to SQLite types as follows:
         *   Long / Long?  → INTEGER (nullable if no NOT NULL)
         *   Int           → INTEGER NOT NULL
         *   Float         → REAL NOT NULL
         *   String        → TEXT NOT NULL
         *   autoGenerate  → INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
         *
         * These CREATE TABLE statements must exactly match what Room's KSP
         * processor would generate for [SessionEntity] and [HrSampleEntity].
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `startTimeMs` INTEGER NOT NULL,
                        `endTimeMs` INTEGER,
                        `targetDurationMs` INTEGER,
                        `totalCalories` REAL NOT NULL,
                        `avgBpm` REAL NOT NULL,
                        `notes` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `hr_samples` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `bpm` INTEGER NOT NULL,
                        `zone` INTEGER NOT NULL,
                        `calPerMinute` REAL NOT NULL,
                        `cumulativeCalories` REAL NOT NULL
                    )
                    """.trimIndent()
                )
                // Index on sessionId speeds up "fetch all samples for session X" queries
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_hr_samples_sessionId` ON `hr_samples` (`sessionId`)"
                )
            }
        }

        /** Returns the singleton database instance, creating it on first call. */
        fun getInstance(context: Context): PulseCoachDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PulseCoachDatabase::class.java,
                    "pulsecoach_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
