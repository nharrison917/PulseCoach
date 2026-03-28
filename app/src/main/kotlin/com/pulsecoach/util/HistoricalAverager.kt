package com.pulsecoach.util

import com.pulsecoach.model.HrSample
import com.pulsecoach.model.Session

/**
 * Builds a per-minute average cumulative calorie curve from past qualifying sessions,
 * then blends it with a polynomial projection to produce a more stable estimate.
 *
 * Why blend? The polynomial uses only the current session's data, so early in a session
 * (10-15 min) it can over- or under-shoot. Historical data from similar sessions anchors
 * the projection, reducing variance without discarding real-time signal.
 *
 * Blend formula: projection[t] = POLY_WEIGHT * polynomial[t] + HIST_WEIGHT * historical[t]
 * Only activates once [BLEND_MIN_SESSIONS] qualifying sessions are available.
 *
 * Phase 4 adds a typed fallback ladder via [getFilteredCurve]:
 *   Tier 1: sessions matching both intensity type AND duration bucket (>= 10 required)
 *   Tier 2: sessions matching intensity type only, any duration (>= 10 required)
 *   Tier 3: polynomial only (returns null)
 * Types are never mixed — Recovery/Steady/Push histories stay separate.
 */
object HistoricalAverager {

    /** Minimum number of qualifying sessions needed before blending is activated. */
    const val BLEND_MIN_SESSIONS = 10

    /**
     * Minimum number of sessions that must have data at a given minute for that
     * minute to be included in the average. Minutes below this threshold are
     * extrapolated linearly from the last well-averaged point.
     */
    private const val MIN_CONTRIBUTING_SESSIONS = 3

    private const val POLY_WEIGHT = 0.4f
    private const val HIST_WEIGHT = 0.6f

    // Duration bucket boundaries (inclusive upper bounds in minutes)
    private const val BUCKET_20_MAX = 25L
    private const val BUCKET_30_MAX = 37L
    private const val BUCKET_45_MAX = 52L
    // >= 53 min → 60 bucket

    /**
     * Maps an actual session duration to the nearest standard bucket (20/30/45/60 min).
     *
     * Bucket boundaries:
     *   ≤ 25 min  → 20
     *   26-37 min → 30
     *   38-52 min → 45
     *   ≥ 53 min  → 60
     *
     * @param durationMs Actual session duration in milliseconds.
     */
    fun durationBucketFor(durationMs: Long): Int {
        val durationMin = durationMs / 60_000L
        return when {
            durationMin <= BUCKET_20_MAX -> 20
            durationMin <= BUCKET_30_MAX -> 30
            durationMin <= BUCKET_45_MAX -> 45
            else -> 60
        }
    }

    /**
     * Fallback ladder for typed projection — call this instead of [buildCurve] when
     * the user has pre-classified their session with a [SessionType].
     *
     * [sessions] must already be filtered to the desired type (via
     * [SessionRepository.getQualifyingSessionsByType]). This function then applies
     * the duration-bucket filter internally:
     *
     *   Tier 1: sessions whose actual duration maps to [durationBucket] AND count >= 10
     *   Tier 2: all [sessions] (same type, any duration)         AND count >= 10
     *   Tier 3: returns null — caller falls back to polynomial-only
     *
     * @param sessions      Qualifying sessions pre-filtered to the desired SessionType.
     * @param samples       All HR samples for those sessions (flat list).
     * @param targetMinutes How many minutes the historical curve should cover.
     * @param durationBucket The target's bucket: one of 20, 30, 45, or 60.
     */
    fun getFilteredCurve(
        sessions: List<Session>,
        samples: List<HrSample>,
        targetMinutes: Int,
        durationBucket: Int
    ): List<Float>? {
        // Tier 1: same intensity type + same duration bucket
        val tier1 = sessions.filter { session ->
            val actualMs = (session.endTimeMs ?: 0L) - session.startTimeMs
            durationBucketFor(actualMs) == durationBucket
        }
        if (tier1.size >= BLEND_MIN_SESSIONS) {
            return buildCurve(tier1, samples, targetMinutes)
        }

        // Tier 2: same intensity type, any duration
        if (sessions.size >= BLEND_MIN_SESSIONS) {
            return buildCurve(sessions, samples, targetMinutes)
        }

        // Tier 3: not enough typed history — caller uses polynomial only
        return null
    }

    /**
     * Builds a per-minute average calorie curve covering minutes 1..[targetMinutes].
     *
     * Returns a list where `result[i]` = average cumulative calories at minute `i + 1`
     * (0-indexed, so result[0] = minute 1, result[4] = minute 5, etc.).
     *
     * Minutes with fewer than [MIN_CONTRIBUTING_SESSIONS] contributing sessions are
     * filled by linear extrapolation from the last well-covered minute.
     *
     * @param sessions  The qualifying sessions whose history to average.
     * @param samples   All HR samples for those sessions (flat list; grouped internally).
     * @param targetMinutes  How many minutes to cover. Should be >= the longest target
     *                       duration the user might select (60 is a safe ceiling).
     */
    fun buildCurve(
        sessions: List<Session>,
        samples: List<HrSample>,
        targetMinutes: Int
    ): List<Float> {
        if (sessions.isEmpty()) return emptyList()

        // Group samples by session so we can process each session independently
        val samplesBySession: Map<Long, List<HrSample>> = samples.groupBy { it.sessionId }

        // For each session, compute a map of minute -> cumulative calories at that minute.
        // "Minute" here = floor((sampleTimestamp - sessionStart) / 60_000).
        // We take the LAST sample in each minute window because it has the highest
        // cumulative value (calories only increase within a session).
        val perSessionCurves: List<Map<Int, Float>> = sessions.mapNotNull { session ->
            val sessionSamples = samplesBySession[session.id] ?: return@mapNotNull null
            if (sessionSamples.isEmpty()) return@mapNotNull null
            val startMs = session.startTimeMs
            sessionSamples
                .groupBy { ((it.timestampMs - startMs) / 60_000L).toInt() }
                .mapValues { (_, minuteSamples) ->
                    minuteSamples.maxByOrNull { it.timestampMs }!!.cumulativeCalories
                }
        }

        if (perSessionCurves.isEmpty()) return emptyList()

        // Build the averaged curve, minute by minute, with linear extrapolation fallback.
        val result = mutableListOf<Float>()
        // Track the last minute that had enough contributors, so we can extrapolate linearly
        // beyond that point (both for gaps mid-session and for minutes beyond session lengths).
        var lastGoodMinute = 0        // 1-based minute index
        var lastGoodValue  = 0f
        var recentSlope    = 0f       // calories per minute, updated as we step forward

        for (minute in 1..targetMinutes) {
            val contributors: List<Float> = perSessionCurves.mapNotNull { it[minute] }

            if (contributors.size >= MIN_CONTRIBUTING_SESSIONS) {
                val avg = contributors.average().toFloat()
                // Update the slope estimate whenever we have a previous anchor point
                if (lastGoodMinute > 0) {
                    recentSlope = (avg - lastGoodValue) / (minute - lastGoodMinute).toFloat()
                }
                lastGoodMinute = minute
                lastGoodValue  = avg
                result.add(avg)
            } else {
                // Not enough sessions reached this minute — extrapolate from last known point.
                // coerceAtLeast(0f) prevents a large negative slope from producing nonsense.
                val extrapolated = if (lastGoodMinute > 0) {
                    (lastGoodValue + recentSlope * (minute - lastGoodMinute)).coerceAtLeast(lastGoodValue)
                } else {
                    0f
                }
                result.add(extrapolated)
            }
        }

        return result
    }

    /**
     * Blends a polynomial projection with the pre-built historical curve.
     *
     * Each projected point at minute t becomes:
     *   POLY_WEIGHT * polynomial(t) + HIST_WEIGHT * historical(t)
     *
     * If the historical curve doesn't reach a projected minute (e.g. all qualifying
     * sessions were shorter than the target), that point falls back to the polynomial
     * value alone so the chart always has data.
     *
     * @param polynomial  Output of [PolynomialProjector.project] — (minute, calories) pairs.
     * @param historical  Output of [buildCurve] — index i = minute i+1.
     */
    fun blend(
        polynomial: List<Pair<Float, Float>>,
        historical: List<Float>
    ): List<Pair<Float, Float>> {
        return polynomial.map { (minute, polyCal) ->
            // Convert 1-based minute to 0-based index into the historical list
            val index = minute.toInt() - 1
            val histCal = if (index >= 0 && index < historical.size) historical[index] else polyCal
            minute to (POLY_WEIGHT * polyCal + HIST_WEIGHT * histCal)
        }
    }
}
