package com.pulsecoach.util

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric tests for the SharedPreferences-backed behaviour of [ProjectionCalibrator].
 *
 * These tests cover the parts that cannot be tested with pure JVM tests:
 *   - The n < MIN_SESSIONS guard in getCorrectionFactor()
 *   - The write/read round-trip through SharedPreferences
 *   - Outlier rejection in updateFactor()
 *   - Sigma gate in getProjectionSigma()
 *
 * [Config] pins the SDK to 34 (Android 14) — a stable Robolectric target that
 * avoids any API-35-specific shadow gaps while still exercising real SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectionCalibratorPrefsTest {

    private lateinit var context: Context

    // A simple projected curve: linear from 0 to 300 cal over 30 min
    private val curve = listOf(0f to 0f, 10f to 100f, 20f to 200f, 30f to 300f)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Clear calibration prefs before each test so state doesn't bleed between them
        context.getSharedPreferences("pulse_coach_calibration", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ── getCorrectionFactor guard ─────────────────────────────────────────────

    @Test
    fun `getCorrectionFactor returns 1f with no data stored`() {
        assertEquals(1.0f, ProjectionCalibrator.getCorrectionFactor(context), 0.001f)
    }

    @Test
    fun `getCorrectionFactor returns 1f after only 1 session`() {
        // actualCalories = 200, projected at minute 20 = 200 → ratio = 1.0
        ProjectionCalibrator.updateFactor(context, 200f, curve, 20f)
        assertEquals(1.0f, ProjectionCalibrator.getCorrectionFactor(context), 0.001f)
    }

    @Test
    fun `getCorrectionFactor returns 1f after only 2 sessions`() {
        repeat(2) { ProjectionCalibrator.updateFactor(context, 200f, curve, 20f) }
        assertEquals(1.0f, ProjectionCalibrator.getCorrectionFactor(context), 0.001f)
    }

    @Test
    fun `getCorrectionFactor activates and departs from 1f after MIN_SESSIONS sessions`() {
        // Feed 3 sessions where actual is consistently 20% above projection (ratio = 1.2)
        repeat(ProjectionCalibrator.MIN_SESSIONS) {
            ProjectionCalibrator.updateFactor(context, 240f, curve, 20f) // 240/200 = 1.2
        }
        val factor = ProjectionCalibrator.getCorrectionFactor(context)
        assertNotEquals("Factor should depart from 1.0f after MIN_SESSIONS", 1.0f, factor)
        assertEquals(1.2f, factor, 0.01f)
    }

    // ── updateFactor round-trip ───────────────────────────────────────────────

    @Test
    fun `updateFactor no-ops when projected curve is null`() {
        ProjectionCalibrator.updateFactor(context, 200f, null, 20f)
        // n should still be 0, so factor stays 1.0f
        assertEquals(1.0f, ProjectionCalibrator.getCorrectionFactor(context), 0.001f)
    }

    @Test
    fun `updateFactor no-ops when ratio is below outlier floor`() {
        // actualCalories = 80, projected at 20 min = 200 → ratio = 0.4 (below 0.5)
        ProjectionCalibrator.updateFactor(context, 80f, curve, 20f)
        assertEquals(1.0f, ProjectionCalibrator.getCorrectionFactor(context), 0.001f)
    }

    @Test
    fun `updateFactor no-ops when ratio is above outlier ceiling`() {
        // actualCalories = 500, projected at 20 min = 200 → ratio = 2.5 (above 2.0)
        ProjectionCalibrator.updateFactor(context, 500f, curve, 20f)
        assertEquals(1.0f, ProjectionCalibrator.getCorrectionFactor(context), 0.001f)
    }

    @Test
    fun `updateFactor rolling mean converges toward observed ratio`() {
        // 3 sessions at ratio 0.8 (actual under-runs projection by 20%)
        repeat(ProjectionCalibrator.MIN_SESSIONS) {
            ProjectionCalibrator.updateFactor(context, 160f, curve, 20f) // 160/200 = 0.8
        }
        assertEquals(0.8f, ProjectionCalibrator.getCorrectionFactor(context), 0.01f)
    }

    // ── getProjectionSigma gate ───────────────────────────────────────────────

    @Test
    fun `getProjectionSigma returns null with no data`() {
        assertNull(ProjectionCalibrator.getProjectionSigma(context))
    }

    @Test
    fun `getProjectionSigma returns null with fewer than MIN_SIGMA_SESSIONS sessions`() {
        repeat(ProjectionCalibrator.MIN_SIGMA_SESSIONS - 1) {
            ProjectionCalibrator.updateFactor(context, 200f, curve, 20f)
        }
        assertNull(ProjectionCalibrator.getProjectionSigma(context))
    }

    @Test
    fun `getProjectionSigma returns non-null after MIN_SIGMA_SESSIONS sessions`() {
        repeat(ProjectionCalibrator.MIN_SIGMA_SESSIONS) {
            ProjectionCalibrator.updateFactor(context, 200f, curve, 20f)
        }
        // All ratios are 1.0, so sigma = 0 — but it should be non-null
        val sigma = ProjectionCalibrator.getProjectionSigma(context)
        assert(sigma != null) { "Expected non-null sigma at ${ProjectionCalibrator.MIN_SIGMA_SESSIONS} sessions" }
    }
}
