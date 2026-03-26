package com.pulsecoach.util

import com.pulsecoach.model.BiologicalSex
import com.pulsecoach.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CalorieCalculator.
 *
 * These run on the JVM (no device needed) because CalorieCalculator has
 * no Android dependencies — it's pure math.
 *
 * Known-good values were computed by hand from the Keytel (2005) formula
 * and then locked in here so we'd catch any accidental coefficient change.
 */
class CalorieCalculatorTest {

    // A representative male profile for most tests
    private val maleProfile = UserProfile(age = 30, weightKg = 80f, sex = BiologicalSex.MALE)

    // A representative female profile
    private val femaleProfile = UserProfile(age = 30, weightKg = 65f, sex = BiologicalSex.FEMALE)

    // ── Sub-threshold guard ───────────────────────────────────────────────────

    @Test
    fun `returns zero when bpm is below 90`() {
        // Below MIN_RELIABLE_BPM the formula is unreliable; we expect a hard zero.
        assertEquals(0f, CalorieCalculator.calPerMinute(89, maleProfile))
    }

    @Test
    fun `returns zero when bpm is exactly 89`() {
        assertEquals(0f, CalorieCalculator.calPerMinute(89, femaleProfile))
    }

    @Test
    fun `returns zero at bpm 0`() {
        // bpm == 0 means no reading yet — should also be zero.
        assertEquals(0f, CalorieCalculator.calPerMinute(0, maleProfile))
    }

    // ── Male formula ──────────────────────────────────────────────────────────

    @Test
    fun `male formula at 90 bpm produces correct value`() {
        // Manual calculation for male, age=30, weight=80, HR=90:
        // raw = (-55.0969 + 0.6309*90 + 0.1988*80 + 0.2017*30) / 4.184
        //     = (-55.0969 + 56.781 + 15.904 + 6.051) / 4.184
        //     = 23.6391 / 4.184
        //     ≈ 5.650 cal/min
        val result = CalorieCalculator.calPerMinute(90, maleProfile)
        assertEquals(5.65f, result, 0.05f)  // 0.05 tolerance for float rounding
    }

    @Test
    fun `male formula at 150 bpm produces correct value`() {
        // raw = (-55.0969 + 0.6309*150 + 0.1988*80 + 0.2017*30) / 4.184
        //     = (-55.0969 + 94.635 + 15.904 + 6.051) / 4.184
        //     = 61.4931 / 4.184
        //     ≈ 14.698 cal/min
        val result = CalorieCalculator.calPerMinute(150, maleProfile)
        assertEquals(14.70f, result, 0.05f)
    }

    // ── Female formula ────────────────────────────────────────────────────────

    @Test
    fun `female formula at 90 bpm produces correct value`() {
        // Manual calculation for female, age=30, weight=65, HR=90:
        // raw = (-20.4022 + 0.4472*90 - 0.1263*65 + 0.074*30) / 4.184
        //     = (-20.4022 + 40.248 - 8.2095 + 2.22) / 4.184
        //     = 13.8563 / 4.184
        //     ≈ 3.312 cal/min
        val result = CalorieCalculator.calPerMinute(90, femaleProfile)
        assertEquals(3.31f, result, 0.05f)
    }

    @Test
    fun `female formula at 150 bpm produces correct value`() {
        // raw = (-20.4022 + 0.4472*150 - 0.1263*65 + 0.074*30) / 4.184
        //     = (-20.4022 + 67.08 - 8.2095 + 2.22) / 4.184
        //     = 40.6883 / 4.184
        //     ≈ 9.724 cal/min
        val result = CalorieCalculator.calPerMinute(150, femaleProfile)
        assertEquals(9.72f, result, 0.05f)
    }

    // ── Output floor ──────────────────────────────────────────────────────────

    @Test
    fun `result is never negative`() {
        // A very low-weight, low-age profile at exactly 90 bpm could theoretically
        // produce a negative raw value — clamp should prevent it.
        val edgeProfile = UserProfile(age = 10, weightKg = 30f, sex = BiologicalSex.FEMALE)
        val result = CalorieCalculator.calPerMinute(90, edgeProfile)
        assertTrue("calPerMinute must be >= 0, got $result", result >= 0f)
    }

    // ── calPerSample relationship ─────────────────────────────────────────────

    @Test
    fun `calPerSample equals calPerMinute divided by 60`() {
        // The H10 emits one sample per second = 1/60 of a minute.
        val perMin = CalorieCalculator.calPerMinute(140, maleProfile)
        val perSample = CalorieCalculator.calPerSample(140, maleProfile)
        assertEquals(perMin / 60f, perSample, 1e-6f)
    }

    @Test
    fun `calPerSample is zero below threshold`() {
        assertEquals(0f, CalorieCalculator.calPerSample(85, maleProfile))
    }
}
