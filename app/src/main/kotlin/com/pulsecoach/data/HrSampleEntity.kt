package com.pulsecoach.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity — maps to the "hr_samples" table.
 *
 * One row per heart rate reading during a recorded session (~1 row/second).
 * [sessionId] links back to [SessionEntity.id].
 *
 * [cumulativeCalories] is stored (not computed at read time) so CSV export
 * can write the column directly without a sequential pass over the rows.
 *
 * The index on [sessionId] makes "fetch all samples for session X" fast —
 * without it, Room would scan the entire table for every chart or export load.
 */
@Entity(
    tableName = "hr_samples",
    indices = [Index(value = ["sessionId"])]
)
data class HrSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,
    val bpm: Int,
    val zone: Int,
    val calPerMinute: Float,
    val cumulativeCalories: Float
)
