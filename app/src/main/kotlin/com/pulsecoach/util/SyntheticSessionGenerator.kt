package com.pulsecoach.util

import com.pulsecoach.model.HrSample
import com.pulsecoach.model.UserProfile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Generates parameterized synthetic HR curves for seeding test sessions.
 *
 * Each session has three phases:
 *   1. Logistic warm-up: HR rises smoothly from rest to target over 6 minutes.
 *   2. Noisy steady state: HR holds at target with +5 bpm cardiovascular drift
 *      and Gaussian noise (sigma=4, clamped to +/-10 bpm).
 *   3. Exponential cooldown: HR decays back toward rest over the last 6 minutes.
 */
object SyntheticSessionGenerator {

    private const val REST_HR = 68
    private const val WARMUP_MIN = 6
    private const val COOLDOWN_MIN = 6

    /**
     * The 12 seeded sessions: (targetHr, durationMinutes).
     * Zone 2 (~125 bpm), Zone 3 (~147 bpm), Zone 4 (~165 bpm) at varied durations.
     */
    val SESSION_CONFIGS: List<Pair<Int, Int>> = listOf(
        125 to 28, 125 to 32, 125 to 35, 125 to 40, 125 to 45,
        147 to 30, 147 to 35, 147 to 40, 147 to 45, 147 to 50,
        165 to 25, 165 to 30
    )

    /**
     * One generated session's data, ready to insert into Room.
     * [samples] have sessionId = 0L (placeholder; the repository sets the real ID after insert).
     */
    data class Result(
        val durationMs: Long,
        val totalCalories: Float,
        val avgBpm: Float,
        val samples: List<HrSample>
    )

    /**
     * Generates one synthetic session at the given HR target and duration.
     *
     * @param random Overridable for deterministic unit tests.
     */
    fun generate(
        targetHr: Int,
        durationMin: Int,
        profile: UserProfile,
        startTimeMs: Long,
        random: Random = Random.Default
    ): Result {
        val totalSeconds = durationMin * 60
        val warmupSec = WARMUP_MIN * 60
        val cooldownStartSec = totalSeconds - COOLDOWN_MIN * 60
        // coerceAtLeast(1) prevents division by zero on very short sessions
        val steadyDuration = (cooldownStartSec - warmupSec).coerceAtLeast(1)

        val samples = mutableListOf<HrSample>()
        var cumulativeCalories = 0f
        var bpmSum = 0L

        for (t in 0 until totalSeconds) {
            val tMin = t / 60.0

            val rawBpm: Double = when {
                t < warmupSec -> {
                    // Logistic: starts near restHr, asymptotes to targetHr at warmupMin
                    REST_HR + (targetHr - REST_HR) / (1.0 + exp(-1.2 * (tMin - WARMUP_MIN / 2.0)))
                }
                t < cooldownStartSec -> {
                    // Cardiovascular drift: +5 bpm linearly over the steady-state window
                    val progress = (t - warmupSec).toDouble() / steadyDuration
                    val drift = 5.0 * progress
                    // Gaussian noise with sigma=4, clamped to +/-10 bpm
                    val noise = (gaussianNoise(random) * 4.0).coerceIn(-10.0, 10.0)
                    targetHr + drift + noise
                }
                else -> {
                    // Exponential decay from peak back toward restHr
                    val tCooldown = tMin - cooldownStartSec / 60.0
                    REST_HR + (targetHr - REST_HR) * exp(-0.4 * tCooldown)
                }
            }

            val bpm = rawBpm.roundToInt().coerceIn(40, 220)
            val zone = bpmToZone(bpm)
            val calPerMin = CalorieCalculator.calPerMinute(bpm, profile)
            val calThisSample = CalorieCalculator.calPerSample(bpm, profile)
            cumulativeCalories += calThisSample
            bpmSum += bpm

            samples.add(
                HrSample(
                    id = 0L,
                    sessionId = 0L,
                    timestampMs = startTimeMs + t * 1000L,
                    bpm = bpm,
                    zone = zone,
                    calPerMinute = calPerMin,
                    cumulativeCalories = cumulativeCalories
                )
            )
        }

        return Result(
            durationMs = totalSeconds * 1000L,
            totalCalories = cumulativeCalories,
            avgBpm = if (totalSeconds > 0) bpmSum.toFloat() / totalSeconds else 0f,
            samples = samples
        )
    }

    /**
     * Box-Muller transform: maps two uniform [0,1) samples to a standard normal (mean=0, sigma=1).
     * This avoids any external stats library.
     */
    private fun gaussianNoise(random: Random): Double {
        // u1 clamped away from 0 to avoid ln(0) = -infinity
        val u1 = random.nextDouble().coerceAtLeast(1e-10)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    /** Maps bpm to zone 1–5 using the default thresholds. */
    private fun bpmToZone(bpm: Int): Int = when {
        bpm < 115 -> 1
        bpm < 136 -> 2
        bpm < 156 -> 3
        bpm < 176 -> 4
        else      -> 5
    }
}
