package com.pulsecoach.util

/**
 * Fits a degree-2 polynomial to a cumulative calorie curve and extrapolates
 * it forward to a target session duration.
 *
 * The projection pipeline:
 *   1. Collect (elapsedMinutes, cumulativeCalories) pairs while recording.
 *   2. Once [MIN_DATA_MINUTES] of data exist, call [project].
 *   3. The returned list is the projected curve from the last actual point to
 *      [targetMinutes], ready to render as a chart overlay.
 *
 * Model: y = a + b*x + c*x^2, solved via least-squares normal equations.
 * The normal equations form a 3x3 linear system solved by Gaussian elimination —
 * no external math library is needed.
 *
 * Safety: if the fitted curve turns downward before [targetMinutes] (can happen
 * with limited data), the projector falls back to linear extrapolation from the
 * slope of the last two minutes of actual data.
 */
object PolynomialProjector {

    /** Minimum recorded minutes before projection is attempted. */
    const val MIN_DATA_MINUTES = 10f

    /**
     * Output resolution: one projected point every [STEP_MINUTES] minutes.
     * 1.0 = one point per minute, which is enough for a smooth chart line and
     * keeps the projected series small (max ~50 points for a 60-min session).
     */
    const val STEP_MINUTES = 1.0f

    /**
     * Projects the calorie curve from the last recorded point to [targetMinutes].
     *
     * @param dataPoints Ordered (elapsedMinutes, cumulativeCalories) pairs.
     * @param targetMinutes Session target duration in minutes.
     * @return Projected points starting at the last data point, or null if there
     *         is less than [MIN_DATA_MINUTES] of data, or the session has already
     *         passed the target.
     */
    fun project(
        dataPoints: List<Pair<Float, Float>>,
        targetMinutes: Float
    ): List<Pair<Float, Float>>? {
        if (dataPoints.size < 3) return null
        val lastTime = dataPoints.last().first
        if (lastTime < MIN_DATA_MINUTES) return null
        if (lastTime >= targetMinutes) return null

        val coefficients = fitQuadratic(dataPoints)
        val lastActualCal = dataPoints.last().second

        // Build the candidate projected curve from lastTime to targetMinutes
        val candidate = buildCurve(lastTime, targetMinutes, coefficients)

        // Safety check: the projection must be monotonically non-decreasing.
        // Calories can never go down during a session. If the quadratic curves
        // back down (e.g. c < 0 and the peak is before targetMinutes), fall back
        // to linear extrapolation from the recent slope.
        val isMonotonic = candidate.zipWithNext().all { (a, b) -> b.second >= a.second - 0.1f }
            && candidate.last().second >= lastActualCal

        return if (isMonotonic) candidate else linearFallback(dataPoints, targetMinutes)
    }

    /**
     * Fits y = a + b*x + c*x^2 using least-squares normal equations.
     *
     * The normal equations (X^T X)β = X^T y reduce to a 3x3 linear system:
     *
     *   [ n    Σx   Σx² ] [a]   [Σy   ]
     *   [ Σx   Σx²  Σx³ ] [b] = [Σxy  ]
     *   [ Σx²  Σx³  Σx⁴ ] [c]   [Σx²y ]
     *
     * Accumulated in Double to avoid float overflow with x⁴ terms (x can be ~60
     * minutes, so x⁴ ≈ 13 million — well within Double range but not Float).
     *
     * Returns [a, b, c]. If the system is near-singular (very few distinct x
     * values), falls back to a naive linear estimate.
     */
    internal fun fitQuadratic(points: List<Pair<Float, Float>>): DoubleArray {
        var n = 0.0
        var sx = 0.0; var sx2 = 0.0; var sx3 = 0.0; var sx4 = 0.0
        var sy = 0.0; var sxy = 0.0; var sx2y = 0.0

        for ((x, y) in points) {
            val xd = x.toDouble()
            val yd = y.toDouble()
            n    += 1.0
            sx   += xd
            sx2  += xd * xd
            sx3  += xd * xd * xd
            sx4  += xd * xd * xd * xd
            sy   += yd
            sxy  += xd * yd
            sx2y += xd * xd * yd
        }

        // Augmented matrix [A | b] for the 3x3 normal-equation system
        val augmented = arrayOf(
            doubleArrayOf(n,   sx,  sx2, sy  ),
            doubleArrayOf(sx,  sx2, sx3, sxy ),
            doubleArrayOf(sx2, sx3, sx4, sx2y)
        )

        return gaussianElimination(augmented)
            ?: doubleArrayOf(0.0, if (n > 0.0) sy / n else 0.0, 0.0)
    }

    /**
     * Solves a 3x3 linear system given as a 3×4 augmented matrix [A | b]
     * using partial-pivoting Gaussian elimination.
     *
     * Partial pivoting: before eliminating each column, we swap the current row
     * with the row below it that has the largest absolute value in that column.
     * This prevents division by near-zero values, which would amplify floating-
     * point errors (a common beginner trap with Gaussian elimination).
     *
     * Returns [x0, x1, x2] or null if the matrix is singular (no unique solution).
     */
    internal fun gaussianElimination(augmented: Array<DoubleArray>): DoubleArray? {
        val n = 3
        // Work on a copy so we don't mutate the caller's data
        val m = Array(n) { augmented[it].copyOf() }

        // Forward elimination
        for (col in 0 until n) {
            // Find the row with the largest absolute value in this column (partial pivot)
            var maxRow = col
            for (row in col + 1 until n) {
                if (Math.abs(m[row][col]) > Math.abs(m[maxRow][col])) maxRow = row
            }
            val tmp = m[col]; m[col] = m[maxRow]; m[maxRow] = tmp

            if (Math.abs(m[col][col]) < 1e-10) return null  // singular

            for (row in col + 1 until n) {
                val factor = m[row][col] / m[col][col]
                for (j in col..n) {
                    m[row][j] -= factor * m[col][j]
                }
            }
        }

        // Back substitution
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            x[i] = m[i][n]
            for (j in i + 1 until n) x[i] -= m[i][j] * x[j]
            x[i] /= m[i][i]
        }
        return x
    }

    /** Evaluates y = a + b*x + c*x² and clamps to >= 0 (calories can't be negative). */
    private fun evalPolynomial(coeff: DoubleArray, x: Float): Float {
        val xd = x.toDouble()
        return (coeff[0] + coeff[1] * xd + coeff[2] * xd * xd).toFloat().coerceAtLeast(0f)
    }

    /** Builds the projected curve by stepping from [startMinutes] to [endMinutes]. */
    private fun buildCurve(
        startMinutes: Float,
        endMinutes: Float,
        coeff: DoubleArray
    ): List<Pair<Float, Float>> {
        val result = mutableListOf<Pair<Float, Float>>()
        var t = startMinutes
        while (t <= endMinutes + STEP_MINUTES / 2f) {
            result.add(t to evalPolynomial(coeff, t))
            t += STEP_MINUTES
        }
        // Always include the exact target endpoint
        if (result.isEmpty() || result.last().first < endMinutes - 0.01f) {
            result.add(endMinutes to evalPolynomial(coeff, endMinutes))
        }
        return result
    }

    /**
     * Linear fallback: extrapolates from the last data point using the average
     * calorie rate over the last two minutes of recorded data.
     *
     * Used when the quadratic fit would produce a non-monotonic curve (i.e. the
     * polynomial peaks and starts declining before the session ends). A steady
     * linear projection is always a reasonable lower-bound estimate.
     */
    private fun linearFallback(
        dataPoints: List<Pair<Float, Float>>,
        targetMinutes: Float
    ): List<Pair<Float, Float>> {
        val last = dataPoints.last()
        // Use a reference point ~2 minutes back for a stable slope estimate
        val reference = dataPoints.lastOrNull { it.first <= last.first - 2f } ?: dataPoints.first()
        val slope = if (last.first > reference.first) {
            (last.second - reference.second) / (last.first - reference.first)
        } else 0f

        val result = mutableListOf<Pair<Float, Float>>()
        var t = last.first
        while (t <= targetMinutes + STEP_MINUTES / 2f) {
            val cal = (last.second + slope * (t - last.first)).coerceAtLeast(last.second)
            result.add(t to cal)
            t += STEP_MINUTES
        }
        if (result.isEmpty() || result.last().first < targetMinutes - 0.01f) {
            val cal = (last.second + slope * (targetMinutes - last.first)).coerceAtLeast(last.second)
            result.add(targetMinutes to cal)
        }
        return result
    }
}
