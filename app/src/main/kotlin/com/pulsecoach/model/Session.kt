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
    val notes: String,
    val sessionType: SessionType?, // null = unclassified ("--")
    val zone1Seconds: Int = 0,
    val zone2Seconds: Int = 0,
    val zone3Seconds: Int = 0,
    val zone4Seconds: Int = 0,
    val zone5Seconds: Int = 0,
    val maxBpm: Int = 0
)
