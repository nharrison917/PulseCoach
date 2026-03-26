package com.pulsecoach.model

/**
 * Domain model for a recorded training session.
 * This is the pure data class used outside the data layer —
 * ViewModels and UI work with Session, not SessionEntity.
 */
data class Session(
    val id: Long,
    val startTimeMs: Long,
    val endTimeMs: Long?,
    val targetDurationMs: Long?,
    val totalCalories: Float,
    val avgBpm: Float,
    val notes: String
)
