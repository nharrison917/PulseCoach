package com.pulsecoach.util

import com.pulsecoach.model.BiologicalSex
import com.pulsecoach.model.UserProfile

/**
 * Keytel et al. (2005) HR-based calorie formula.
 *
 * Returns estimated calories burned per minute for a given heart rate and user profile.
 * The formula has two coefficient sets — one for each biological sex.
 *
 * Reliability note: the formula is only accurate above 90 bpm. Below that threshold
 * (e.g. standing still before a run) the output is clamped to 0 to avoid misleading data.
 *
 * This is a pure function with no side effects — straightforward to unit test.
 */
object CalorieCalculator {

    private const val MIN_RELIABLE_BPM = 90

    /**
     * Returns calories per minute for the given [bpm] and [profile].
     * Returns 0f if [bpm] is below [MIN_RELIABLE_BPM].
     */
    fun calPerMinute(bpm: Int, profile: UserProfile): Float {
        if (bpm < MIN_RELIABLE_BPM) return 0f

        val raw = when (profile.sex) {
            BiologicalSex.MALE ->
                (-55.0969 + 0.6309 * bpm + 0.1988 * profile.weightKg + 0.2017 * profile.age) / 4.184
            BiologicalSex.FEMALE ->
                (-20.4022 + 0.4472 * bpm - 0.1263 * profile.weightKg + 0.074 * profile.age) / 4.184
        }

        // Clamp to >= 0: the formula can go negative at very low HR values
        return maxOf(0f, raw.toFloat())
    }

    /**
     * Returns the calories contributed by one HR reading.
     * The H10 emits one reading per second, so each sample covers 1/60 of a minute.
     */
    fun calPerSample(bpm: Int, profile: UserProfile): Float =
        calPerMinute(bpm, profile) / 60f
}
