package com.pulsecoach.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** JVM unit tests for the pure helper functions in [ProjectionCalibrator]. */
class ProjectionCalibratorTest {

    // ── applyTo ──────────────────────────────────────────────────────────────

    @Test
    fun `applyTo scales all y values by factor`() {
        val curve = listOf(1f to 10f, 2f to 20f, 3f to 30f)
        val result = ProjectionCalibrator.applyTo(curve, 1.1f)
        assertEquals(11f, result[0].second, 0.001f)
        assertEquals(22f, result[1].second, 0.001f)
        assertEquals(33f, result[2].second, 0.001f)
    }

    @Test
    fun `applyTo with factor 1 returns unchanged values`() {
        val curve = listOf(5f to 100f, 10f to 200f)
        val result = ProjectionCalibrator.applyTo(curve, 1.0f)
        assertEquals(100f, result[0].second, 0.001f)
        assertEquals(200f, result[1].second, 0.001f)
    }

    @Test
    fun `applyTo preserves x values unchanged`() {
        val curve = listOf(7f to 50f, 14f to 100f)
        val result = ProjectionCalibrator.applyTo(curve, 0.8f)
        assertEquals(7f, result[0].first, 0.001f)
        assertEquals(14f, result[1].first, 0.001f)
    }

    @Test
    fun `applyTo on empty curve returns empty list`() {
        val result = ProjectionCalibrator.applyTo(emptyList(), 1.5f)
        assertEquals(0, result.size)
    }

    // ── interpolateProjection ─────────────────────────────────────────────────

    @Test
    fun `interpolateProjection returns null for empty curve`() {
        assertNull(ProjectionCalibrator.interpolateProjection(emptyList(), 5f))
    }

    @Test
    fun `interpolateProjection returns null when target is before first point`() {
        val curve = listOf(10f to 100f, 20f to 200f)
        assertNull(ProjectionCalibrator.interpolateProjection(curve, 5f))
    }

    @Test
    fun `interpolateProjection returns last value when target exceeds curve`() {
        val curve = listOf(10f to 100f, 20f to 200f)
        val result = ProjectionCalibrator.interpolateProjection(curve, 30f)
        assertEquals(200f, result!!, 0.001f)
    }

    @Test
    fun `interpolateProjection returns exact value at a known point`() {
        val curve = listOf(10f to 100f, 20f to 200f)
        val result = ProjectionCalibrator.interpolateProjection(curve, 10f)
        assertEquals(100f, result!!, 0.001f)
    }

    @Test
    fun `interpolateProjection linearly interpolates between two points`() {
        // At minute 15, halfway between 10→100 and 20→200, expect 150
        val curve = listOf(10f to 100f, 20f to 200f)
        val result = ProjectionCalibrator.interpolateProjection(curve, 15f)
        assertEquals(150f, result!!, 0.001f)
    }

    @Test
    fun `interpolateProjection handles multi-segment curve`() {
        val curve = listOf(0f to 0f, 10f to 100f, 20f to 250f, 30f to 450f)
        // Between minute 20 and 30: at minute 25 expect 350 (midpoint of 250..450)
        val result = ProjectionCalibrator.interpolateProjection(curve, 25f)
        assertEquals(350f, result!!, 0.001f)
    }

    // ── computeRollingMean ────────────────────────────────────────────────────

    @Test
    fun `computeRollingMean first observation sets mean to that value`() {
        // n=0, oldMean=1.0 (initial default), newValue=1.2 → (1.0*0 + 1.2) / 1 = 1.2
        val result = ProjectionCalibrator.computeRollingMean(1.0f, 0, 1.2f)
        assertEquals(1.2f, result, 0.001f)
    }

    @Test
    fun `computeRollingMean averages correctly over multiple observations`() {
        // Simulate three ratios: 1.2, 0.9, 1.05
        var mean = ProjectionCalibrator.computeRollingMean(1.0f, 0, 1.2f) // n becomes 1
        mean = ProjectionCalibrator.computeRollingMean(mean, 1, 0.9f)     // n becomes 2
        mean = ProjectionCalibrator.computeRollingMean(mean, 2, 1.05f)    // n becomes 3
        assertEquals((1.2f + 0.9f + 1.05f) / 3f, mean, 0.001f)
    }

    @Test
    fun `computeRollingMean with factor 1 and ratio 1 stays at 1`() {
        val result = ProjectionCalibrator.computeRollingMean(1.0f, 5, 1.0f)
        assertEquals(1.0f, result, 0.001f)
    }

    // ── outlier guard (via constants) ─────────────────────────────────────────

    @Test
    fun `outlier bounds are correct`() {
        assertEquals(0.5f, ProjectionCalibrator.OUTLIER_MIN, 0.001f)
        assertEquals(2.0f, ProjectionCalibrator.OUTLIER_MAX, 0.001f)
    }

    @Test
    fun `MIN_SESSIONS threshold is correct`() {
        assertEquals(3, ProjectionCalibrator.MIN_SESSIONS)
    }

    // ── computeSigma ──────────────────────────────────────────────────────────

    @Test
    fun `computeSigma returns null with fewer than MIN_SIGMA_SESSIONS values`() {
        val ratios = listOf(1.0f, 1.1f, 0.9f, 1.05f) // 4 — one short
        assertNull(ProjectionCalibrator.computeSigma(ratios))
    }

    @Test
    fun `computeSigma returns non-null at exactly MIN_SIGMA_SESSIONS values`() {
        val ratios = listOf(1.0f, 1.1f, 0.9f, 1.05f, 0.95f) // exactly 5
        val result = ProjectionCalibrator.computeSigma(ratios)
        assert(result != null) { "Expected non-null sigma at 5 sessions" }
    }

    @Test
    fun `computeSigma returns null for empty list`() {
        assertNull(ProjectionCalibrator.computeSigma(emptyList()))
    }

    @Test
    fun `computeSigma returns zero for a list of identical values`() {
        val ratios = listOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
        assertEquals(0f, ProjectionCalibrator.computeSigma(ratios)!!, 0.001f)
    }

    @Test
    fun `computeSigma sample standard deviation matches manual calculation`() {
        // Values: 1.0, 1.2, 0.8, 1.1, 0.9
        // mean = 1.0; deviations^2 = 0.0, 0.04, 0.04, 0.01, 0.01; sum = 0.10
        // sample variance = 0.10 / (5-1) = 0.025; sigma = sqrt(0.025) ≈ 0.1581
        val ratios = listOf(1.0f, 1.2f, 0.8f, 1.1f, 0.9f)
        val result = ProjectionCalibrator.computeSigma(ratios)
        assertEquals(0.1581f, result!!, 0.001f)
    }

    @Test
    fun `computeSigma increases with more spread-out values`() {
        val tight  = listOf(1.0f, 1.01f, 0.99f, 1.0f, 1.005f)
        val spread = listOf(0.6f, 1.4f, 0.8f, 1.2f, 1.0f)
        val sigTight  = ProjectionCalibrator.computeSigma(tight)!!
        val sigSpread = ProjectionCalibrator.computeSigma(spread)!!
        assert(sigSpread > sigTight) {
            "Spread sigma ($sigSpread) should exceed tight sigma ($sigTight)"
        }
    }

    @Test
    fun `MIN_SIGMA_SESSIONS threshold is correct`() {
        assertEquals(5, ProjectionCalibrator.MIN_SIGMA_SESSIONS)
    }
}
