package com.pulsecoach.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for zone configuration.
 *
 * @Dao tells Room this interface contains database operations.
 * Room generates the implementation at compile time — we never write SQL boilerplate.
 *
 * The Flow return type on getZoneConfig() is key: Room emits a new value automatically
 * whenever the row changes. This means the UI reacts to saved settings instantly
 * without any manual refresh logic.
 */
@Dao
interface ZoneConfigDao {

    /** Observe the zone config row. Emits null if no row has been saved yet. */
    @Query("SELECT * FROM zone_config WHERE id = 1")
    fun getZoneConfig(): Flow<ZoneConfigEntity?>

    /**
     * Insert or replace the zone config row.
     * @Upsert = update if the primary key exists, insert if it doesn't.
     * suspend = must be called from a coroutine (not the main thread).
     */
    @Upsert
    suspend fun upsert(config: ZoneConfigEntity)
}
