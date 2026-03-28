package com.pulsecoach.util

import com.pulsecoach.model.HrSample
import com.pulsecoach.model.Session
import com.pulsecoach.model.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for Phase 4 additions to HistoricalAverager:
 *   - durationBucketFor() boundary mapping
 *   - getFilteredCurve() fallback ladder (Tier 1 → Tier 2 → null)
 */
class HistoricalAveragerFallbackTest {

    // ── durationBucketFor ─────────────────────────────────────────────────────

    @Test
    fun `durationBucketFor maps short durations to 20`() {
        assertEquals(20, HistoricalAverager.durationBucketFor(20 * 60_000L))
        assertEquals(20, HistoricalAverager.durationBucketFor(25 * 60_000L)) // boundary
    }

    @Test
    fun `durationBucketFor maps medium-short durations to 30`() {
        assertEquals(30, HistoricalAverager.durationBucketFor(26 * 60_000L)) // just over boundary
        assertEquals(30, HistoricalAverager.durationBucketFor(30 * 60_000L))
        assertEquals(30, HistoricalAverager.durationBucketFor(37 * 60_000L)) // boundary
    }

    @Test
    fun `durationBucketFor maps medium-long durations to 45`() {
        assertEquals(45, HistoricalAverager.durationBucketFor(38 * 60_000L)) // just over boundary
        assertEquals(45, HistoricalAverager.durationBucketFor(45 * 60_000L))
        assertEquals(45, HistoricalAverager.durationBucketFor(52 * 60_000L)) // boundary
    }

    @Test
    fun `durationBucketFor maps long durations to 60`() {
        assertEquals(60, HistoricalAverager.durationBucketFor(53 * 60_000L)) // just over boundary
        assertEquals(60, HistoricalAverager.durationBucketFor(60 * 60_000L))
        assertEquals(60, HistoricalAverager.durationBucketFor(90 * 60_000L))
    }

    // ── getFilteredCurve — fallback ladder ────────────────────────────────────

    @Test
    fun `getFilteredCurve returns null when fewer than 10 typed sessions exist`() {
        // 9 sessions of the target type — not enough for either tier
        val sessions = buildSessions(count = 9, durationMin = 30, type = SessionType.STEADY)
        val result = HistoricalAverager.getFilteredCurve(
            sessions = sessions,
            samples = buildSamples(sessions),
            targetMinutes = 30,
            durationBucket = 30
        )
        assertNull(result)
    }

    @Test
    fun `getFilteredCurve Tier 1 fires when 10+ sessions match type and bucket`() {
        // 10 sessions all in the 30-min bucket
        val sessions = buildSessions(count = 10, durationMin = 30, type = SessionType.STEADY)
        val result = HistoricalAverager.getFilteredCurve(
            sessions = sessions,
            samples = buildSamples(sessions),
            targetMinutes = 30,
            durationBucket = 30
        )
        assertNotNull("Tier 1 should return a curve with 10 matching sessions", result)
    }

    @Test
    fun `getFilteredCurve Tier 2 fires when type matches but bucket does not have 10`() {
        // 4 sessions in the 30-min bucket + 7 in the 45-min bucket = 11 total of the same type
        // Tier 1 (30-min bucket) only has 4 — not enough
        // Tier 2 (all 11) — enough
        val bucket30 = buildSessions(count = 4, durationMin = 30, type = SessionType.PUSH, startId = 0)
        val bucket45 = buildSessions(count = 7, durationMin = 45, type = SessionType.PUSH, startId = 4)
        val allSessions = bucket30 + bucket45

        val result = HistoricalAverager.getFilteredCurve(
            sessions = allSessions,
            samples = buildSamples(allSessions),
            targetMinutes = 30,
            durationBucket = 30
        )
        assertNotNull("Tier 2 should return a curve when 11 same-type sessions exist", result)
    }

    @Test
    fun `getFilteredCurve returns null when Tier 1 and Tier 2 both fail`() {
        // 5 sessions of the type — neither tier reaches 10
        val sessions = buildSessions(count = 5, durationMin = 45, type = SessionType.RECOVERY)
        val result = HistoricalAverager.getFilteredCurve(
            sessions = sessions,
            samples = buildSamples(sessions),
            targetMinutes = 45,
            durationBucket = 45
        )
        assertNull("Should return null when fewer than 10 typed sessions exist", result)
    }

    @Test
    fun `getFilteredCurve Tier 1 is preferred over Tier 2 when both qualify`() {
        // 10 sessions in the 30-min bucket AND 5 more in the 45-min bucket = 15 total
        // Both tiers qualify, but Tier 1 (10 sessions) should be used — not Tier 2 (15).
        // We verify this indirectly: Tier 1 curve uses only the 30-min sessions.
        // Build 30-min sessions with calPerMinute=10 and 45-min sessions with calPerMinute=20.
        // If Tier 1 is used, the curve will reflect ~10 cal/min; if Tier 2, it would be higher.
        val bucket30 = buildSessions(count = 10, durationMin = 30, type = SessionType.STEADY, startId = 0)
        val bucket45 = buildSessions(count = 5,  durationMin = 45, type = SessionType.STEADY, startId = 10)
        val allSessions = bucket30 + bucket45

        val tier1Samples = buildSamples(bucket30, calPerMinute = 10f)
        val tier2Samples = buildSamples(bucket45, calPerMinute = 20f)

        val result = HistoricalAverager.getFilteredCurve(
            sessions = allSessions,
            samples = tier1Samples + tier2Samples,
            targetMinutes = 30,
            durationBucket = 30
        )
        assertNotNull(result)
        // buildCurve looks up minute bucket 1 (1-based), where cumulative = calPerMinute * 2.
        // Tier 1 only (calPerMinute=10) → bucket 1 value = 20f.
        // If Tier 2 were mixed in (calPerMinute=20, 5 sessions) → value would be ~26.7f.
        // Asserting ≤ 22f confirms Tier 1 sessions drove the average, not Tier 2.
        val minute1 = result!![0]
        assertEquals(20f, minute1, 2f)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val baseMs = 1_700_000_000_000L

    private fun buildSessions(
        count: Int,
        durationMin: Int,
        type: SessionType?,
        startId: Int = 0
    ): List<Session> {
        val durationMs = durationMin * 60_000L
        return (0 until count).map { i ->
            val start = baseMs + (startId + i) * 26L * 3_600_000L
            Session(
                id = (startId + i + 1).toLong(),
                startTimeMs = start,
                endTimeMs = start + durationMs,
                targetDurationMs = durationMs,
                totalCalories = durationMin * 10f,
                avgBpm = 140f,
                notes = "",
                sessionType = type
            )
        }
    }

    private fun buildSamples(
        sessions: List<Session>,
        calPerMinute: Float = 10f
    ): List<HrSample> {
        return sessions.flatMap { session ->
            val durationMs = (session.endTimeMs ?: session.startTimeMs) - session.startTimeMs
            val durationMin = (durationMs / 60_000L).toInt()
            (1..durationMin).map { minute ->
                HrSample(
                    id = 0L,
                    sessionId = session.id,
                    // Place sample at the last second of each minute
                    timestampMs = session.startTimeMs + minute * 60_000L - 1_000L,
                    bpm = 140,
                    zone = 3,
                    calPerMinute = calPerMinute,
                    cumulativeCalories = calPerMinute * minute
                )
            }
        }
    }
}
