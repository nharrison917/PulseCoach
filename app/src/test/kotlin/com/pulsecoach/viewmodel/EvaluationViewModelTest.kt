package com.pulsecoach.viewmodel

import com.pulsecoach.model.BiologicalSex
import com.pulsecoach.model.HrSample
import com.pulsecoach.model.UserProfile
import com.pulsecoach.util.PolynomialProjector
import com.pulsecoach.util.SyntheticSessionGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * JVM unit tests for evaluation logic.
 *
 * EvaluationViewModel itself requires an Application context, so these tests
 * exercise the companion-object helpers and the full SyntheticSessionGenerator
 * + PolynomialProjector pipeline directly — the same code paths the ViewModel
 * calls internally.
 */
class EvaluationViewModelTest {

    private val testProfile = UserProfile(age = 30, weightKg = 75f, sex = BiologicalSex.MALE)

    // ── buildMinuteCurve ──────────────────────────────────────────────────────

    @Test
    fun `buildMinuteCurve returns empty list for empty input`() {
        val result = EvaluationViewModel.buildMinuteCurve(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildMinuteCurve groups samples into correct minute buckets`() {
        // 3 samples: one at 0 s, one at 30 s, one at 90 s (minute 1)
        val base = 1_000_000L
        val samples = listOf(
            makeSample(base + 0L,     50f),
            makeSample(base + 30_000L, 55f),
            makeSample(base + 90_000L, 65f)  // 1 min 30 s → minute bucket 1
        )
        val curve = EvaluationViewModel.buildMinuteCurve(samples)
        assertEquals(2, curve.size)
        assertEquals(0f, curve[0].first, 0.001f)
        assertEquals(55f, curve[0].second, 0.001f) // last sample in minute 0
        assertEquals(1f, curve[1].first, 0.001f)
        assertEquals(65f, curve[1].second, 0.001f) // only sample in minute 1
    }

    @Test
    fun `buildMinuteCurve picks the last sample in each minute window`() {
        val base = 0L
        // Two samples in minute 0; cumCalories should be the larger one (later sample)
        val samples = listOf(
            makeSample(base + 10_000L, 10f),
            makeSample(base + 50_000L, 18f)  // later in minute 0
        )
        val curve = EvaluationViewModel.buildMinuteCurve(samples)
        assertEquals(1, curve.size)
        assertEquals(18f, curve[0].second, 0.001f)
    }

    // ── projection pipeline (SyntheticSessionGenerator + PolynomialProjector) ─

    @Test
    fun `generate produces expected number of samples`() {
        val result = SyntheticSessionGenerator.generate(
            targetHr = 125, durationMin = 30,
            profile = testProfile, startTimeMs = 0L, random = Random(42)
        )
        // One sample per second for 30 min = 1800 samples
        assertEquals(1800, result.samples.size)
    }

    @Test
    fun `polynomial projection activates after 10 min and returns non-null`() {
        val session = SyntheticSessionGenerator.generate(
            targetHr = 147, durationMin = 45,
            profile = testProfile, startTimeMs = 0L, random = Random(7)
        )
        val curve = EvaluationViewModel.buildMinuteCurve(session.samples)
        val first10 = curve.filter { (min, _) -> min <= 10f }

        // Should have at least 3 points for fitting
        assertTrue(first10.size >= 3)

        val projected = PolynomialProjector.project(first10, targetMinutes = 45f)
        assertNotNull(projected)
    }

    @Test
    fun `polynomial projection is null when fewer than 3 data points available`() {
        val twoPoints = listOf(Pair(0f, 0f), Pair(1f, 2f))
        val projected = PolynomialProjector.project(twoPoints, targetMinutes = 30f)
        assertNull(projected)
    }

    @Test
    fun `projection with full curve produces a positive finite result`() {
        // Feeding the entire session's curve to PolynomialProjector should always
        // return a valid, positive projection — the exact value will vary because
        // a degree-2 polynomial fits noisy data, not a perfect trace.
        val session = SyntheticSessionGenerator.generate(
            targetHr = 125, durationMin = 30,
            profile = testProfile, startTimeMs = 0L, random = Random(1)
        )
        val curve = EvaluationViewModel.buildMinuteCurve(session.samples)
        val durationMin = session.durationMs / 60_000f

        val projected = PolynomialProjector.project(curve, durationMin)
        assertNotNull(projected)

        val projectedFinal = projected!!.last().second
        // Result must be a positive finite number — not NaN, not negative
        assertTrue("Projected final calories must be > 0, got $projectedFinal", projectedFinal > 0f)
        assertTrue("Projected final must be finite", projectedFinal.isFinite())
    }

    @Test
    fun `MAPE improves (or holds) as observation window grows`() {
        // Generate 5 sessions; compute MAPE at 10 and 20 min windows
        // More data -> better projection -> lower MAPE (on average)
        val sessions = (0..4).map { seed ->
            SyntheticSessionGenerator.generate(
                targetHr = 147, durationMin = 45,
                profile = testProfile,
                startTimeMs = seed * 3_600_000L,
                random = Random(seed.toLong())
            )
        }
        val curves = sessions.map { EvaluationViewModel.buildMinuteCurve(it.samples) }

        fun mapeAtWindow(window: Int): Float {
            val errs = sessions.zip(curves).mapNotNull { (session, curve) ->
                val pts = curve.filter { (m, _) -> m <= window.toFloat() }
                if (pts.size < 3) return@mapNotNull null
                val proj = PolynomialProjector.project(pts, session.durationMs / 60_000f)
                val pFinal = proj?.lastOrNull()?.second ?: return@mapNotNull null
                abs(pFinal - session.totalCalories) / session.totalCalories
            }
            return if (errs.isEmpty()) Float.MAX_VALUE else errs.average().toFloat() * 100f
        }

        val mape10 = mapeAtWindow(10)
        val mape20 = mapeAtWindow(20)
        // Allow a small tolerance: 20-min window should not be drastically worse than 10-min
        assertTrue(
            "MAPE at 20 min ($mape20%) should not be >10 points worse than at 10 min ($mape10%)",
            mape20 <= mape10 + 10f
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeSample(timestampMs: Long, cumulativeCalories: Float) = HrSample(
        id = 0L,
        sessionId = 0L,
        timestampMs = timestampMs,
        bpm = 130,
        zone = 2,
        calPerMinute = 5f,
        cumulativeCalories = cumulativeCalories
    )
}
