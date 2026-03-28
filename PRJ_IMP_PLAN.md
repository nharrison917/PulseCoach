# PulseCoach — Implementation Plan

Projection quality improvements, to be implemented in order.
See ANALYSIS.md for the analytical rationale and trade-off analysis behind these choices.

---

## Phase 7: Projection Calibration (Level 4 + Level 5)

### Level 4: Personal Bias Correction

**New file — `util/ProjectionCalibrator.kt`**

A singleton object (matching the pattern of `PolynomialProjector` and `HistoricalAverager`) that owns the personal correction factor.

Responsibilities:
- `fun updateFactor(context, actualCalories, projectedCurve, actualDurationMinutes)` — interpolates the projected curve at `actualDurationMinutes`, computes `ratio = actual / projected`, updates the rolling mean stored in SharedPreferences. Skip if ratio is outside [0.5, 2.0] (outlier guard). Only store after ≥ 3 valid ratios.
- `fun getCorrectionFactor(context): Float` — returns stored factor, or `1.0f` if fewer than 3 sessions have contributed (no-op until meaningful).
- `fun applyTo(curve, factor): List<Pair<Float, Float>>` — multiplies all y-values by `factor`. Pure function, easy to unit test.

SharedPreferences keys: `"proj_correction_factor"` (Float), `"proj_correction_n"` (Int).

**Changes to `LiveSessionViewModel.kt`**

1. **Check first:** verify the ViewModel extends `AndroidViewModel` (not plain `ViewModel`) — needed for `application` context to reach SharedPreferences. If it doesn't, update the class declaration: `class LiveSessionViewModel(application: Application) : AndroidViewModel(application)`.

2. In `stopRecording()`: before clearing `_projectedCalorieCurve`, capture the last projected curve and actual duration (`sampleCount / 60f`). Pass to `ProjectionCalibrator.updateFactor()`. This is the only place where both actuals and projection are simultaneously in scope.

3. In the per-minute projection block (inside `startHrStream`'s collect): after computing the projection (polynomial or blend), call `ProjectionCalibrator.applyTo(curve, factor)` before assigning to `_projectedCalorieCurve.value`.

**No UI changes required for Level 4.** The correction is applied transparently — the chart already renders whatever `projectedCalorieCurve` contains.

**Testing:**
- Unit test `ProjectionCalibrator.applyTo()` — verify scaling and edge cases
- Unit test the interpolation logic at `actualDurationMinutes` — verify it handles curves that don't extend to the actual duration
- Verify factor stays at `1.0f` with < 3 sessions, activates correctly at 3+

---

### Level 5: Prediction Intervals

Implement only after Level 4 is working and tested.

**Extends `ProjectionCalibrator.kt`**

Add alongside the existing correction factor logic:
- Track the list of past ratios — stored as a comma-separated string in SharedPreferences, capped at the last 50 sessions
- `fun getProjectionSigma(context): Float?` — returns standard deviation of stored ratios, or `null` if fewer than 5 data points

The band in calorie terms at a projected point: `band(t) = sigma * projectedCalories(t)`. Scales uncertainty with projection magnitude — larger projected totals have wider absolute bands.

**Changes to `LiveSessionViewModel.kt`**

Add `_projectionBand: MutableStateFlow<Float?>` (the σ value, or null). Updated alongside the projection each minute.

**Changes to `LiveCalorieChart.kt`**

Add parameter: `projectionBand: Float? = null`

When non-null, compute upper/lower series:
- `upperPoints = projectedPoints.map { (t, cal) -> t to cal + projectionBand * cal }`
- `lowerPoints = projectedPoints.map { (t, cal) -> t to (cal - projectionBand * cal).coerceAtLeast(0f) }`

Add as series 3 and 4 in `LineCartesianLayer` via `LineProvider.series(line0, line1, line2, line3)`. Use thin, low-opacity dashed lines in the projection color.

Extend chart caption: `"Polynomial projection  •  ±12% historical range"` when band is active.

**Changes to `LiveSessionScreen.kt`**

Collect `projectionBand` from the ViewModel. Pass to `LiveCalorieChart` — one additional `collectAsStateWithLifecycle()` call and one additional parameter at the call site.

**Vico note:** Going from 2 to 4 series means all four line styles must be defined upfront in `LineProvider.series(...)`. Review existing CLAUDE.md Vico gotchas before touching the chart.

**Testing:**
- Unit test `getProjectionSigma()` with known lists — verify std dev calculation and null guard
- Verify band is null with < 5 sessions, activates at 5+
- Visual inspection: band should widen at later projected minutes (since `band * cal` grows as cal grows)
