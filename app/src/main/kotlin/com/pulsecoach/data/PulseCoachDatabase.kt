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
 *  3 → Added sessionType column to sessions (Phase 4 Session Intent Classification).
 *  4 → Added zone1Seconds–zone5Seconds columns to sessions (Phase 6 Zone Time Tracking).
 *  5 → Added maxBpm column to sessions.
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
    version = 5,
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

        /**
         * Migration from v2 to v3.
         * Adds the sessionType column (nullable TEXT) to the sessions table.
         * SQLite ALTER TABLE only supports adding columns, not removing them — and
         * nullable columns with no default are always safe to add this way.
         * Existing rows will have sessionType = NULL (displayed as "--" in the UI).
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `sessionType` TEXT")
            }
        }

        /**
         * Migration from v3 to v4.
         * Adds per-zone elapsed-second columns to the sessions table.
         * SQLite DEFAULT 0 means all existing sessions will show zero for every zone,
         * which is correct — we have no historical zone data for them.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `zone1Seconds` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `zone2Seconds` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `zone3Seconds` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `zone4Seconds` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `zone5Seconds` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from v4 to v5.
         * Adds maxBpm (INTEGER NOT NULL DEFAULT 0) to the sessions table.
         * Existing sessions will show 0, which the UI treats as "no data" and displays as "--".
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `maxBpm` INTEGER NOT NULL DEFAULT 0")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
