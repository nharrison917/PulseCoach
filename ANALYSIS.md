# PulseCoach — Analytical Methods

This document describes the analytical techniques underlying PulseCoach's calorie estimation and projection engine, framed for a quantitative analytics audience.

---

## Layer 0: Sensor Integration — 1Hz BPM to Cumulative Calories

The Polar H10 transmits one heart rate reading per second via BLE. Each reading is a scalar BPM value. The app converts this to a **calorie increment** and accumulates it in real time:

```
cal_per_sample = cal_per_minute(bpm, profile) / 60
cumulativeCalories += cal_per_sample
```

At each completed elapsed minute, the app records a data point `(t_minutes, cumulativeCalories)`. After 10 minutes, the resulting 10-point series is the input to the polynomial projector.

This accumulation step is **numerical integration by rectangle rule** (left Riemann sum with Δt = 1 second). For a smooth physiological signal at 1Hz, the integration error is negligible. The meaningful source of error is the formula's accuracy, not the integration method.

**Note on the calorie formula floor:** The Keytel formula is suppressed below 90 BPM because the regression was fit on exercise-range data. Applying it at resting HR would produce unreliable values. Below 90 BPM, `cal_per_sample = 0`.

---

## Layer 1: Physiological Regression Model (Calorie Estimation)

The calorie formula is **Keytel et al. (2005)** — a published multivariate linear regression fit on observed subjects relating heart rate, body weight, age, and sex to metabolic rate:

**Male:** `cal/min = (-55.0969 + 0.6309·HR + 0.1988·weight_kg + 0.2017·age) / 4.184`

**Female:** `cal/min = (-20.4022 + 0.4472·HR - 0.1263·weight_kg + 0.074·age) / 4.184`

This is **model transfer** — the regression was fit on a population sample and applied to an individual at inference time. The formula is evaluated on every 1Hz heart rate sample from the Polar H10 sensor and accumulated in real time.

**Known limitation:** It is a population mean model applied to one person, so it carries individual-level bias. Accuracy improves as a personal baseline (Layer 3) is established over repeated sessions.

---

## Layer 2: Polynomial Regression with Analytical Solver (In-Session Forecasting)

Once 10 minutes of data are recorded, `PolynomialProjector` fits a degree-2 curve to the cumulative calorie series:

> **y = a + bx + cx²**

where x = elapsed minutes, y = cumulative calories. This is **ordinary least squares (OLS) polynomial regression**.

Rather than calling an external library, the solver derives the **normal equations from first principles**:

> **(XᵀX)β = Xᵀy**

For a quadratic model this reduces to a 3×3 linear system:

```
[ n    Σx   Σx² ] [a]   [Σy   ]
[ Σx   Σx²  Σx³ ] [b] = [Σxy  ]
[ Σx²  Σx³  Σx⁴ ] [c]   [Σx²y ]
```

The system is solved by **Gaussian elimination with partial pivoting** — a numerically stable algorithm that prevents division by near-zero pivot values, which would amplify floating-point error. Summations are accumulated in `Double` to avoid overflow on the x⁴ terms (x ≈ 60 min → x⁴ ≈ 13M).

The fitted curve is then **extrapolated** to the user's target session end time. This is a forecasting application of regression — extrapolation carries more uncertainty than in-sample prediction, which motivates the ensemble approach in Layer 3.

**Domain constraint:** Cumulative calories are monotonically non-decreasing. If the quadratic curves back down before the target time (possible with limited early data, since a concave-down parabola peaks and then falls), the projector detects the violation and falls back to **linear extrapolation** from the slope of the most recent two minutes. This is a post-fit constraint enforcement step, common in constrained regression settings.

---

## Layer 3: Empirical Baseline + Weighted Ensemble (Historical Blending)

`HistoricalAverager.buildCurve()` computes a **per-minute mean** across all qualifying past sessions, producing an empirical baseline curve. This is a **reference class forecast** — it answers: "for someone with my training history, what does a typical session look like at minute t?"

Minutes with fewer than 3 contributing sessions (sparse tail of longer sessions) are filled by **linear extrapolation** from the last well-covered minute.

Once 10+ qualifying sessions exist, the in-session polynomial forecast is combined with this baseline:

> **projection(t) = 0.4 × polynomial(t) + 0.6 × historical(t)**

This is **forecast combination** (model ensembling). Bates & Granger (1969) established that combining forecasts from models with different error structures almost always outperforms either model alone. Here:

- The **polynomial** is reactive to the current session's real-time signal but unstable early (few data points → high variance fit)
- The **historical baseline** is stable but ignores the current session's actual trajectory

The 0.6/0.4 weighting toward history reflects that the polynomial's variance early in a session is typically larger than the baseline's bias. The weights are exposed as a tunable parameter in debug builds — exactly how a hyperparameter would be treated during model development.

---

## Layer 4: Stratified Segmentation with Hierarchical Fallback (Session Classification)

Users classify sessions by intent: **RECOVERY**, **STEADY**, or **PUSH**. The historical baseline is then filtered to matching sessions before averaging — this is **stratified analysis**, computing conditional means within subgroups rather than pooling across heterogeneous data.

Sessions are also bucketed by duration (20 / 30 / 45 / 60 min), creating a two-dimensional stratification: intent × duration.

A **hierarchical fallback ladder** handles sparse strata:

| Tier | Condition | Baseline used |
|------|-----------|--------------|
| 1 | Same intent + same duration bucket, N ≥ 10 | Most specific stratum |
| 2 | Same intent, any duration, N ≥ 10 | Intent only |
| 3 | Insufficient history | Polynomial only |

This mirrors the logic of **multilevel (hierarchical) modeling** — use the finest-grained stratum available, but fall back to a coarser level when sample size is insufficient. Types are never mixed: RECOVERY, STEADY, and PUSH histories remain strictly separate.

---

## Layer 5: Personal Bias Correction — Multiplicative Calibration Factor

After each completed session, the app computes a **personal correction factor** from the ratio of actual to projected calories:

```
ratio = actual_final_calories / projected_calories_at_actual_duration
```

`actual_final_calories` is `cumulativeCalories` at session end — the Keytel sum accumulated over the session. The denominator is obtained by **linear interpolation** of the projected curve at the actual session duration. If the session ran to minute 23.4 and the projected curve has points at minutes 23 and 24, the value at 23.4 is read off the straight-line segment between them.

**Critical interpretation:** both numerator and denominator are derived from the same Keytel formula applied to the same HR stream. The ratio does not measure how wrong Keytel is in absolute terms — it measures how much *this session's trajectory* deviated from *this person's own average trajectory*. The correction factor learns personal behavioral patterns (pacing strategy, fatigue profile, session-to-session consistency) rather than correcting physiological model error.

**Outlier guard:** ratios outside [0.5, 2.0] are discarded. A ratio below 0.5 means actual was less than half of projected — almost certainly a sensor drop or an extremely short session rather than a real signal. A ratio above 2.0 similarly indicates measurement failure. Discarded ratios do not count toward the session threshold.

**Rolling mean update:**

```
new_factor = (old_factor × n + ratio) / (n + 1)
```

This is a **cumulative running mean** — each accepted observation has equal weight. The factor is a no-op (returns 1.0) until n ≥ 3, preventing the first unusual session from distorting future projections.

All future projection curve values are multiplied by this factor before display:

```
calibrated(t) = raw_projection(t) × correction_factor
```

**Staleness caveat:** if training style changes substantially (e.g. switching from zone 2 base-building to race-pace work), the accumulated factor reflects the old distribution. A future enhancement could apply exponential decay weighting to older sessions to down-weight stale observations.

---

## Layer 6: Prediction Intervals — Uncertainty Quantification

The correction factor captures mean bias. The **standard deviation of past ratios** captures dispersion — how consistently the projection performs, not just where it tends to land:

```
σ = sqrt( Σ(ratio_i − mean)² / (n − 1) )
```

Bessel's correction (n − 1 denominator) is used because this is a **sample** standard deviation estimating the population σ from a finite set of sessions. The factor is null until n ≥ 5 to avoid producing a nonsensical σ from 2–3 data points.

The confidence band at each projected minute t is:

```
upper(t) = projection(t) × (1 + σ)
lower(t) = projection(t) × (1 − σ)
```

This is a **proportional band** — σ is fractional, not absolute. The consequence is intentional: at minute 45 where projected cumulative calories are high, the absolute band width is larger than at minute 15. Uncertainty compounds over the forecast horizon, which is statistically correct for an extrapolating model.

**Distributional assumption:** the band assumes errors are approximately symmetric around the mean. This is reasonable for a stable training routine but would widen asymmetrically for a user whose sessions vary heavily in intensity. A formal 68% coverage claim requires the ratio distribution to be approximately normal — plausible but not verified.

The band is stored as a single scalar σ (not the full curves), which is sufficient because the band shape is fully determined by σ and the projection curve at render time.

---

## Model Evaluation

An in-app evaluation screen (debug builds only) tests projection accuracy using a **hold-out approach**:

- For each session in history, treat it as the "current" session
- Observe only the first 10, 15, or 20 minutes of data
- Project to the session's actual end time
- Compare projected total calories to the actual recorded total

Accuracy metrics reported:
- **MAE** (Mean Absolute Error) — average absolute calorie deviation
- **MAPE** (Mean Absolute Percentage Error) — scale-invariant accuracy measure

Results are computed separately for polynomial-only vs. blended projections, and broken down by session type — allowing direct comparison of the ensemble's benefit over the single-model baseline.

---

## Resume Summary

> "End-to-end analytics pipeline on a real-time BLE sensor stream: applied a published physiological regression model (Keytel 2005) for caloric estimation via 1Hz numerical integration; implemented in-session polynomial forecasting via closed-form normal equations and partial-pivoting Gaussian elimination; combined real-time and historical forecasts using weighted ensemble methods; applied stratified segmentation by session intent with a hierarchical data-sufficiency fallback; added a self-personalizing multiplicative bias correction via rolling mean of actual/projected ratios; quantified forecast uncertainty with proportional prediction intervals scaled by the sample standard deviation of past projection errors; evaluated accuracy via MAE/MAPE on held-out sessions. Implemented from scratch in Kotlin with no external math libraries."

**What makes it analytically defensible:**
- Every formula traces back to published methodology (Keytel 2005; Bates & Granger 1969 on forecast combination)
- The monotonicity constraint and linear fallback show reasoning about model failure modes
- The N ≥ 10 threshold and fallback ladder reflect awareness of small-sample risk
- The correction factor uses Bessel's correction and an outlier guard — not naive averaging
- The distinction between "correcting Keytel absolute error" vs. "learning personal deviation from own average trajectory" is explicit — the model knows what it is and isn't doing
- The proportional band (σ × projection value) is the correct uncertainty structure for a multiplicative error model — not a fixed-width band
- Hold-out evaluation with MAE/MAPE means accuracy was measured, not assumed

**Honest scope:** This is not a production ML system. It applies the correct analytical concepts end-to-end on real hardware, with a genuine feedback loop between model output and measured accuracy.

---

## Projection Quality Levels — Trade-off Analysis

### Level 1 — Current Baseline
Degree-2 polynomial OLS + historical mean blend. Already implemented. Directionally correct after ~15 minutes of data. Core limitation: the quadratic shape is rigid and cannot represent the warm-up spike → plateau structure of real sessions.

---

### Level 2 — Better Basis Functions (Spline)
**Requires:** Replace `fitQuadratic()` with piecewise linear segments (structural break at warm-up plateau) or a natural cubic spline (tridiagonal solver).
**Effort:** Medium — 2–8 hours depending on approach.
**Complexity:** Low conceptual, medium implementation.
**Pros:** Captures warm-up curve shape the quadratic genuinely misses. No new libraries. Good talking point.
**Cons:** Break-point detection is heuristic. Marginal gain over the existing historical blend, which already smooths over shape errors. Harder to explain without sounding over-engineered.
**Verdict:** Nice-to-have, not the best return on effort.

---

### Level 3 — Time Series Model (ETS / Exponential Smoothing)
**Requires:** Switch modeled variable from cumulative calories to cal/min (the rate). Fit `forecast(t) = α·actual(t) + (1-α)·forecast(t-1)` where α minimizes squared training error. Full ARIMA additionally requires stationarity testing, differencing, ACF/PACF order selection.
**Effort:** ETS: medium (1–2 days). ARIMA: high (several days, meaningful risk of subtle errors).
**Complexity:** ETS: accessible to a QM/analytics background. ARIMA: graduate-level time series.
**Pros:** Explicitly models autocorrelation — the most common methodological critique of regression-based forecasting. Clean statistical story.
**Cons:** Requires architectural change to the data pipeline (cumulative → rate). ARIMA is easy to implement incorrectly in ways that look plausible but are wrong. The current blend already partially achieves ETS's stabilizing effect.
**Verdict:** ETS achievable and credible. ARIMA is overkill with high error risk for this use case.

---

### Level 4 — Personal Bias Correction ✅ IMPLEMENTED
**Requires:** After each session, compute `ratio = actual_calories / projected_calories_at_actual_duration`. Maintain a rolling mean of past ratios as a personal correction factor stored in SharedPreferences. Multiply outgoing projections by this factor at compute time.
**Effort:** Low — 2–4 hours. EvaluationViewModel already computes the residuals; this aggregates them into a scalar and applies it.
**Complexity:** Low. One-parameter multiplicative correction. Easy to explain and test.
**Pros:** Directly addresses the biggest known limitation (Keytel formula has individual-level bias). Self-personalizes over time — compelling narrative. Uses existing infrastructure. No schema changes.
**Cons:** Corrects mean bias only, not shape errors. Factor is meaningful only after ~10 sessions (same gate as blending). If training style changes, old factor becomes stale (mitigated by decay weighting on older sessions).
**Verdict:** Highest return on effort of all levels. Implement first.

---

### Level 5 — Prediction Intervals (Uncertainty Quantification) ✅ IMPLEMENTED
**Requires:** Store standard deviation of past projection errors alongside the correction factor. Expose a `projectionBand` value (±σ in calories) from the ViewModel. Render two additional dashed lines on `LiveCalorieChart` flanking the projection — upper and lower bounds scaled by forecast horizon (uncertainty grows further out).
**Effort:** Statistics: trivial (std dev of a list). UI: medium — adding two more series to the Vico chart, which has precedent in the existing multi-series implementation.
**Complexity:** Low statistics, medium UI. The band interpretation ("historical sessions fell within this range ~68% of the time") is easy to communicate.
**Pros:** Uncertainty quantification is what separates analytical forecasting from "a line on a chart." Immediately visible signal of statistical rigor. Directly answers the most common critique of point forecasts.
**Cons:** Assumes errors are roughly normally distributed — probably fine but not guaranteed. Band may look alarming early in a session when σ is large. Requires N ≥ 10 sessions before σ is meaningful.
**Verdict:** Strong analytical addition with high visual payoff. Implement second, after Level 4.

---

### Level 6 — Kalman Filter / State Space Model
**Requires:** Formulate HR and calories as latent states with transition and observation models. Specify process noise (Q) and observation noise (R) matrices. Implement predict/update loop in real time on the 1Hz sensor stream.
**Effort:** High — 1–2 weeks minimum. High risk of subtle errors in matrix formulation.
**Complexity:** High. Requires probabilistic reasoning, linear algebra, and careful numerical implementation.
**Pros:** Principled online learning; updates continuously with every sensor reading. Gold standard for real-time sensor fusion.
**Cons:** Noise matrix tuning requires empirical calibration data not available here. Massive complexity increase for marginal gain in a 60-minute, 1Hz session context. Very easy to implement incorrectly.
**Verdict:** Not recommended. Better left as a "future work" bullet.

*Implementation plan moved to [PRJ_IMP_PLAN.md](PRJ_IMP_PLAN.md).*
