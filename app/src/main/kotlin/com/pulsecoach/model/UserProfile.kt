package com.pulsecoach.model

/** Biological sex used as a coefficient selector in the Keytel calorie formula. */
enum class BiologicalSex { MALE, FEMALE }

/** User's physical profile required for calorie-per-minute calculations. */
data class UserProfile(
    val age: Int,
    val weightKg: Float,
    val sex: BiologicalSex
)
