package com.pulsecoach.model

/**
 * Domain model for a single heart rate reading within a recorded session.
 * This is the pure data class used outside the data layer.
 */
data class HrSample(
    val id: Long,
    val sessionId: Long,
    val timestampMs: Long,
    val bpm: Int,
    val zone: Int,
    val calPerMinute: Float,
    val cumulativeCalories: Float
)
