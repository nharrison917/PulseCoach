package com.pulsecoach.util

import android.content.Context
import kotlin.math.sqrt

/**
 * Tracks personal projection accuracy and applies a rolling bias correction factor.
 *
 * After each completed session the caller provides the actual calories and the
 * projected curve; this object computes the ratio, updates a rolling mean stored
 * in SharedPreferences, and exposes a correction factor that is applied to all
 * future projections.  The correction is a no-op (factor = 1.0f) until at least
 * MIN_SESSIONS valid ratios have been recorded.
 */
object ProjectionCalibrator {

    private const val PREFS_NAME = "pulse_coach_calibration"
    private const val KEY_FACTOR = "proj_correction_factor"
    private const val KEY_N = "proj_correction_n"
    /** Comma-separated list of the most recent accepted ratios (capped at 50). */
    private const val KEY_RATIOS = "proj_ratios"
    private const val MAX_RATIO_HISTORY = 50

    /** Minimum completed sessions before the factor departs from 1.0f. */
    internal const val MIN_SESSIONS = 3

    /** Minimum observations required before [getProjectionSigma] returns a value. */
    internal const val MIN_SIGMA_SESSIONS = 5

    /** Ratios outside this range are treated as outliers and ignored. */
    internal const val OUTLIER_MIN = 0.5f
    internal const val OUTLIER_MAX = 2.0f

    /**
     * Updates the rolling correction factor after a session completes.
     *
     * Interpolates [projectedCurve] at [actualDurationMinutes], then computes
     * ratio = actual / projected and folds it into the running mean.
     * Skips silently if the curve is null/empty, the projected value is zero,
     * or the ratio falls outside [OUTLIER_MIN, OUTLIER_MAX].
     */
    fun updateFactor(
        context: Context,
        actualCalories: Float,
        projectedCurve: List<Pair<Float, Float>>?,
        actualDurationMinutes: Float
    ) {
        if (projectedCurve == null || projectedCurve.isEmpty()) return

        val projected = interpolateProjection(projectedCurve, actualDurationMinutes) ?: return
        if (projected <= 0f) return

        val ratio = actualCalories / projected
        if (ratio < OUTLIER_MIN || ratio > OUTLIER_MAX) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentFactor = prefs.getFloat(KEY_FACTOR, 1.0f)
        val n = prefs.getInt(KEY_N, 0)

        // Rolling mean: newMean = (oldMean * n + newValue) / (n + 1)
        // This is a running cumulative average — each observation has equal weight.
        val newFactor = computeRollingMean(currentFactor, n, ratio)

        // Maintain the recent ratio list for sigma calculation (capped at MAX_RATIO_HISTORY).
        // Stored as a comma-separated string — simple and requires no extra dependencies.
        val stored = prefs.getString(KEY_RATIOS, "") ?: ""
        val ratioList = if (stored.isEmpty()) mutableListOf() else stored.split(",").map { it.toFloat() }.toMutableList()
        ratioList.add(ratio)
        if (ratioList.size > MAX_RATIO_HISTORY) ratioList.removeAt(0)

        // Single edit() call — both values written atomically
        prefs.edit()
            .putFloat(KEY_FACTOR, newFactor)
            .putInt(KEY_N, n + 1)
            .putString(KEY_RATIOS, ratioList.joinToString(","))
            .apply() // apply() is asynchronous — safe to call on any thread
    }

    /**
     * Returns the stored correction factor, or 1.0f (no-op) if fewer than
     * [MIN_SESSIONS] valid ratios have been contributed.
     */
    fun getCorrectionFactor(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.getInt(KEY_N, 0) >= MIN_SESSIONS) {
            prefs.getFloat(KEY_FACTOR, 1.0f)
        } else {
            1.0f
        }
    }

    /**
     * Applies a bias-correction [factor] to [curve], scaling only the increment above
     * [anchorCal] so the projection always stays anchored to the user's real current
     * calorie count. Defaults to 0f (no anchor) when called without an anchor, which
     * preserves the original "scale everything" behaviour used in tests.
     *
     * Pure function — does not read SharedPreferences.
     */
    fun applyTo(
        curve: List<Pair<Float, Float>>,
        factor: Float,
        anchorCal: Float = 0f
    ): List<Pair<Float, Float>> =
        curve.map { (minute, cal) -> minute to (anchorCal + (cal - anchorCal) * factor) }

    /**
     * Returns the standard deviation of past actual/projected ratios, or null if
     * fewer than [MIN_SIGMA_SESSIONS] valid ratios have been stored.
     *
     * This σ value is used as the fractional band width: at a projected calorie
     * value C, the upper band is C + σ*C and the lower is C − σ*C.
     */
    fun getProjectionSigma(context: Context): Float? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_RATIOS, "") ?: ""
        if (stored.isEmpty()) return null
        val ratios = stored.split(",").mapNotNull { it.toFloatOrNull() }
        return computeSigma(ratios)
    }

    // ── Internal helpers (exposed as internal so unit tests can reach them) ──

    /**
     * Computes one step of the rolling mean without side effects.
     * Used by [updateFactor] and exercised directly in unit tests.
     */
    internal fun computeRollingMean(oldMean: Float, n: Int, newValue: Float): Float =
        (oldMean * n + newValue) / (n + 1)

    /**
     * Returns the sample standard deviation of [ratios], or null if there are
     * fewer than [MIN_SIGMA_SESSIONS] values.  Uses n−1 (Bessel's correction)
     * because we are estimating population σ from a finite sample.
     * Pure function — used by [getProjectionSigma] and exercised in unit tests.
     */
    internal fun computeSigma(ratios: List<Float>): Float? {
        if (ratios.size < MIN_SIGMA_SESSIONS) return null
        val mean = ratios.sum() / ratios.size
        // sumOf operates on Double to avoid float accumulation error
        val variance = ratios.sumOf { ((it - mean) * (it - mean)).toDouble() } / (ratios.size - 1)
        return sqrt(variance).toFloat()
    }

    /**
     * Linearly interpolates [curve] (a list of minute→calorie pairs) at [atMinute].
     * Returns the calorie value at that minute, or null if the target is before
     * the first data point.  If the target exceeds the last point, the last
     * calorie value is returned (no extrapolation beyond the curve).
     */
    internal fun interpolateProjection(
        curve: List<Pair<Float, Float>>,
        atMinute: Float
    ): Float? {
        if (curve.isEmpty()) return null

        // Beyond the end of the curve — use the last recorded value
        if (atMinute >= curve.last().first) return curve.last().second

        // Before the start of the curve — can't interpolate
        if (atMinute < curve.first().first) return null

        // Find the first point whose x >= atMinute, then interpolate between
        // the preceding point and this one.
        val upperIndex = curve.indexOfFirst { it.first >= atMinute }
        if (upperIndex <= 0) return curve[0].second

        val (x0, y0) = curve[upperIndex - 1]
        val (x1, y1) = curve[upperIndex]
        val t = (atMinute - x0) / (x1 - x0) // t in [0, 1]
        return y0 + t * (y1 - y0)
    }
}
