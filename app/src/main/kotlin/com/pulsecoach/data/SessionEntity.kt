package com.pulsecoach.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity — maps to the "sessions" table.
 *
 * [endTimeMs] is nullable: it is null while the session is in progress and set
 * when the user taps Stop. This lets us detect crashed/abandoned sessions.
 *
 * [totalCalories] and [avgBpm] are aggregates written once at session end —
 * they are derived from the hr_samples rows but stored here so the history
 * screen can display them without summing thousands of rows on every load.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMs: Long,
    val endTimeMs: Long?,          // null = session still in progress
    val targetDurationMs: Long?,   // null = no target set
    val totalCalories: Float,
    val avgBpm: Float,
    val notes: String
)
