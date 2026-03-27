package com.pulsecoach.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for PolynomialProjector.
 *
 * These run on the JVM — no device needed. All tests use controlled synthetic
 * data with known ground-truth outcomes so we can assert exact behaviour.
 */
class PolynomialProjectorTest {

    // ── gaussianElimination ───────────────────────────────────────────────────

    @Test
    fun `gaussianElimination solves a simple 3x3 system`() {
        // x  + 2y + 3z = 14   →  1 + 4 + 9  = 14 ✓
        // 2x +  y +  z =  7   →  2 + 2 + 3  =  7 ✓
        // 3x + 2y +  z = 10   →  3 + 4 + 3  = 10 ✓
        // Unique solution: x=1, y=2, z=3
        val augmented = arrayOf(
            doubleArrayOf(1.0, 2.0, 3.0, 14.0),
            doubleArrayOf(2.0, 1.0, 1.0,  7.0),
            doubleArrayOf(3.0, 2.0, 1.0, 10.0)
        )
        val result = PolynomialProjector.gaussianElimination(augmented)
        assertNotNull(result)
        assertEquals(1.0, result!![0], 1e-6)
        assertEquals(2.0, result[1], 1e-6)
        assertEquals(3.0, result[2], 1e-6)
    }

    @Test
    fun `gaussianElimination returns null for singular matrix`() {
        // Rows 0 and 1 are identical — system has no unique solution
        val augmented = arrayOf(
            doubleArrayOf(1.0, 2.0, 3.0, 6.0),
            doubleArrayOf(1.0, 2.0, 3.0, 6.0),
            doubleArrayOf(0.0, 1.0, 1.0, 2.0)
        )
        assertNull(PolynomialProjector.gaussianElimination(augmented))
    }

    // ── fitQuadratic ─────────────────────────────────────────────────────────

    @Test
    fun `fitQuadratic recovers coefficients of a perfect quadratic`() {
        // True curve: y = 2 + 3x + 0.5x^2
        // Generate 20 exact points (no noise) and verify recovery
        val points = (0..20).map { i ->
            val x = i.toFloat()
            val y = 2f + 3f * x + 0.5f * x * x
            x to y
        }
        val coeff = PolynomialProjector.fitQuadratic(points)

        assertEquals(2.0,  coeff[0], 0.01)   // a
        assertEquals(3.0,  coeff[1], 0.01)   // b
        assertEquals(0.5,  coeff[2], 0.01)   // c
    }

    @Test
    fun `fitQuadratic fits a linear series with near-zero quadratic term`() {
        // y = 5 + 8x  (linear)  ->  c should be ~0
        val points = (0..15).map { i -> i.toFloat() to (5f + 8f * i) }
        val coeff = PolynomialProjector.fitQuadratic(points)

        assertEquals(5.0, coeff[0], 0.1)
        assertEquals(8.0, coeff[1], 0.1)
        assertEquals(0.0, coeff[2], 0.1)
    }

    // ── project: guard conditions ─────────────────────────────────────────────

    @Test
    fun `project returns null when fewer than 3 data points`() {
        val points = listOf(0f to 0f, 5f to 20f)
        assertNull(PolynomialProjector.project(points, targetMinutes = 45f))
    }

    @Test
    fun `project returns null when elapsed time is below MIN_DATA_MINUTES`() {
        // 9 points at minute 0..8 — just under the 10-minute threshold
        val points = (0..8).map { i -> i.toFloat() to (i * 5f) }
        assertNull(PolynomialProjector.project(points, targetMinutes = 45f))
    }

    @Test
    fun `project returns null when already past target`() {
        val points = (0..50).map { i -> i.toFloat() to (i * 8f) }
        assertNull(PolynomialProjector.project(points, targetMinutes = 30f))
    }

    // ── project: happy path ───────────────────────────────────────────────────

    @Test
    fun `project returns non-null with sufficient linear data`() {
        // 15 minutes of data at 8 cal/min
        val points = (0..15).map { i -> i.toFloat() to (i * 8f) }
        val result = PolynomialProjector.project(points, targetMinutes = 45f)
        assertNotNull(result)
    }

    @Test
    fun `project result starts at the last actual data point`() {
        val points = (0..15).map { i -> i.toFloat() to (i * 8f) }
        val result = PolynomialProjector.project(points, targetMinutes = 45f)!!

        assertEquals(15f, result.first().first, 0.1f)
        assertEquals(points.last().second, result.first().second, 0.5f)
    }

    @Test
    fun `project result ends at or near targetMinutes`() {
        val points = (0..15).map { i -> i.toFloat() to (i * 8f) }
        val result = PolynomialProjector.project(points, targetMinutes = 45f)!!

        assertEquals(45f, result.last().first, PolynomialProjector.STEP_MINUTES)
    }

    @Test
    fun `project result is monotonically non-decreasing`() {
        // Use a realistic quadratic calorie curve: accelerates slightly
        val points = (0..15).map { i ->
            val x = i.toFloat()
            x to (0f + 7f * x + 0.05f * x * x)  // gentle upward curve
        }
        val result = PolynomialProjector.project(points, targetMinutes = 45f)!!

        result.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "Projection must not decrease: ${a.second} -> ${b.second} at t=${b.first}",
                b.second >= a.second - 0.1f
            )
        }
    }

    @Test
    fun `project falls back to linear when quadratic would decrease`() {
        // Construct data whose quadratic fit peaks well before targetMinutes.
        // Points look like a downward-opening parabola: y = 200 - (x-10)^2
        // At x=10 the curve peaks, then decreases — linear fallback should trigger.
        val points = (0..15).map { i ->
            val x = i.toFloat()
            x to (200f - (x - 10f) * (x - 10f))
        }
        val result = PolynomialProjector.project(points, targetMinutes = 45f)!!

        // With linear fallback the projection must still be non-decreasing
        result.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "Linear fallback must not decrease: ${a.second} -> ${b.second}",
                b.second >= a.second - 0.1f
            )
        }
    }

    // ── project: accuracy on known quadratic ─────────────────────────────────

    @Test
    fun `project accurately extrapolates a perfect quadratic to target`() {
        // True curve: y = 3x + 0.1x^2  (calories grow slightly faster over time)
        // With 20 exact data points the polynomial fit should be near-perfect.
        val points = (0..20).map { i ->
            val x = i.toFloat()
            x to (3f * x + 0.1f * x * x)
        }
        val result = PolynomialProjector.project(points, targetMinutes = 45f)!!

        val trueAt45 = 3f * 45f + 0.1f * 45f * 45f   // 135 + 202.5 = 337.5
        assertEquals(trueAt45, result.last().second, 2f)  // within 2 cal
    }
}
