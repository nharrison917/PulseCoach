package com.pulsecoach.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity — maps to the "zone_config" table in SQLite.
 *
 * We use id = 1 as a fixed primary key, making this a singleton row.
 * There is only ever one active zone configuration; we upsert (insert or replace) it.
 *
 * @Entity tells Room to create a table for this class.
 * @PrimaryKey marks the unique identifier column.
 */
@Entity(tableName = "zone_config")
data class ZoneConfigEntity(
    @PrimaryKey val id: Int = 1,
    val zone1MaxBpm: Int = 114,
    val zone2MaxBpm: Int = 135,
    val zone3MaxBpm: Int = 155,
    val zone4MaxBpm: Int = 175
)
