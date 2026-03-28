package com.pulsecoach.util

import com.pulsecoach.model.HrSample
import com.pulsecoach.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for HistoricalAverager.
 *
 * All tests use minimal synthetic data constructed inline — no Room, no Android.
 * Delta for float assertions is 0.01 (1/100th of a calorie) — tighter than any
 * real-world noise but loose enough to tolerate float arithmetic rounding.
 */
class HistoricalAveragerTest {

    private val delta = 0.01f

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Creates a minimal Session with only the fields HistoricalAverager reads. */
    private fun session(id: Long, startMs: Long = 0L) = Session(
        id = id,
        startTimeMs = startMs,
        endTimeMs = startMs + 60 * 60_000L,   // 1-hour placeholder; not used by averager
        targetDurationMs = null,
        totalCalories = 0f,
        avgBpm = 0f,
        notes = "",
        sessionType = null
    )

    /**
     * Creates a flat list of HrSample rows that simulate a session where the
     * cumulative calorie total increases by [calPerMinute] every minute.
     *
     * One sample per minute (at the last second of each minute) is enough for
     * HistoricalAverager — it takes the last sample in each minute window.
     */
    private fun samplesForSession(
        sessionId: Long,
        startMs: Long,
        durationMinutes: Int,
        calPerMinute: Float
    ): List<HrSample> = (1..durationMinutes).map { minute ->
        HrSample(
            id = 0,
            sessionId = sessionId,
            // Place sample at the 30th second of minute `minute` (bucket = minute * 60_000 + 30_000 / 60_000 = minute).
            // Using +30_000 lands squarely in bucket `minute`; the old -1_000 gave bucket minute-1.
            timestampMs = startMs + minute * 60_000L + 30_000L,
            bpm = 140,
            zone = 3,
            calPerMinute = calPerMinute,
            cumulativeCalories = minute * calPerMinute
        )
    }

    // ── buildCurve ────────────────────────────────────────────────────────────

    @Test
    fun `buildCurve returns empty list for empty sessions`() {
        val result = HistoricalAverager.buildCurve(emptyList(), emptyList(), 45)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildCurve averages identical sessions exactly`() {
        // Three sessions all burning 10 cal/min — average must equal 10 * minute
        val sessions = listOf(session(1L), session(2L), session(3L))
        val samples = sessions.flatMap { s ->
            samplesForSession(s.id, s.startTimeMs, durationMinutes = 30, calPerMinute = 10f)
        }

        val result = HistoricalAverager.buildCurve(sessions, samples, targetMinutes = 30)

        assertEquals(30, result.size)
        // Minute 5 -> cumulative = 5 * 10 = 50
        assertEquals(50f, result[4], delta)   // index 4 = minute 5
        // Minute 20 -> cumulative = 200
        assertEquals(200f, result[19], delta)
    }

    @Test
    fun `buildCurve averages sessions with different rates`() {
        // Session 1: 8 cal/min, Session 2: 12 cal/min, Session 3: 10 cal/min
        // Average at minute 1: (8 + 12 + 10) / 3 = 10.0
        val s1 = session(1L); val s2 = session(2L); val s3 = session(3L)
        val samples = samplesForSession(1L, 0L, 20, 8f) +
                      samplesForSession(2L, 0L, 20, 12f) +
                      samplesForSession(3L, 0L, 20, 10f)

        val result = HistoricalAverager.buildCurve(listOf(s1, s2, s3), samples, targetMinutes = 20)

        assertEquals(10f, result[0], delta)   // minute 1: (8+12+10)/3
        assertEquals(20f, result[1], delta)   // minute 2: (16+24+20)/3
    }

    @Test
    fun `buildCurve extrapolates linearly beyond session lengths`() {
        // Three 20-minute sessions, each burning 10 cal/min flat
        // Target is 30 min — minutes 21-30 must be extrapolated
        val sessions = listOf(session(1L), session(2L), session(3L))
        val samples = sessions.flatMap { s ->
            samplesForSession(s.id, s.startTimeMs, durationMinutes = 20, calPerMinute = 10f)
        }

        val result = HistoricalAverager.buildCurve(sessions, samples, targetMinutes = 30)

        assertEquals(30, result.size)
        // Last averaged minute = 20 -> 200 cal; slope = 10 cal/min
        // Minute 25 extrapolated = 200 + 5 * 10 = 250
        assertEquals(250f, result[24], delta)
        // Minute 30 extrapolated = 200 + 10 * 10 = 300
        assertEquals(300f, result[29], delta)
    }

    @Test
    fun `buildCurve skips minutes with fewer than 3 contributors`() {
        // Sessions 1 & 2 run 10 min; session 3 runs only 5 min
        // Minutes 6-10 have only 2 contributors -> should be extrapolated, not averaged
        val s1 = session(1L); val s2 = session(2L); val s3 = session(3L)
        val samples = samplesForSession(1L, 0L, 10, 10f) +
                      samplesForSession(2L, 0L, 10, 10f) +
                      samplesForSession(3L, 0L, 5,  10f)

        val result = HistoricalAverager.buildCurve(listOf(s1, s2, s3), samples, targetMinutes = 10)

        // Minutes 1-5 have 3 contributors -> should be averaged normally (10 cal/min slope)
        assertEquals(50f, result[4], delta)   // minute 5: 3 sessions all at 50
        // Minutes 6-10 have only 2 contributors -> extrapolated from minute-5 slope
        // The extrapolated value should be >= the minute-5 value (non-decreasing)
        assertTrue(result[5] >= result[4])
        assertTrue(result[9] >= result[5])
    }

    // ── blend ─────────────────────────────────────────────────────────────────

    @Test
    fun `blend applies 40-60 weights correctly`() {
        // Polynomial says 100 cal at minute 15; historical[14] = 15 * 8 = 120 cal
        // Blend = 0.4 * 100 + 0.6 * 120 = 40 + 72 = 112
        val polynomial = listOf(15f to 100f)
        val historical = List(20) { i -> (i + 1) * 8f }   // index i = minute i+1; [14] = 120

        val result = HistoricalAverager.blend(polynomial, historical)

        assertEquals(1, result.size)
        assertEquals(112f, result[0].second, delta)
    }

    @Test
    fun `blend falls back to polynomial when historical is too short`() {
        // Projection extends to minute 50 but historical only covers 30 min
        val polynomial = listOf(50f to 500f)
        val historical = List(30) { i -> (i + 1) * 10f }

        val result = HistoricalAverager.blend(polynomial, historical)

        // Should fall back to polynomial value (500) for minute 50
        assertEquals(500f, result[0].second, delta)
    }

    @Test
    fun `blend preserves x values from polynomial`() {
        val polynomial = listOf(11f to 90f, 12f to 100f, 13f to 110f)
        val historical  = List(20) { i -> (i + 1) * 9f }

        val result = HistoricalAverager.blend(polynomial, historical)

        assertEquals(3, result.size)
        assertEquals(11f, result[0].first, delta)
        assertEquals(12f, result[1].first, delta)
        assertEquals(13f, result[2].first, delta)
    }
}
