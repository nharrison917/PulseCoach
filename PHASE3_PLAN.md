# Phase 3 Implementation Plan — Projection Engine

## Key observation: no Room schema change needed

`targetDurationMs` is already a column in the `sessions` table (null since Phase 2).
The `notes` field flags synthetic sessions (`notes = "synthetic"`). Stay at Room v2.

---

## New files to create

```
util/
  PolynomialProjector.kt       — least-squares quadratic fit + extrapolation
  SyntheticSessionGenerator.kt — parameterized HR curve -> List<HrSample>
  HistoricalAverager.kt        — per-minute avg calorie curve from past sessions

ui/
  LiveCalorieChart.kt          — Vico chart: solid actual line + dashed projection
  EvaluationScreen.kt          — debug-only accuracy evaluation screen

viewmodel/
  EvaluationViewModel.kt       — runs test sessions, computes MAE/MAPE
```

---

## Existing files to modify

| File | Change |
|---|---|
| `SessionDao.kt` | Add `getQualifyingSessions()` query (completed, avgBpm > 100, duration > 10 min) |
| `HrSampleDao.kt` | Add `getSamplesForSessions(ids: List<Long>)` for bulk historical load |
| `SessionRepository.kt` | Add `getQualifyingSessions()`, `seedSyntheticSessions()` (debug), `getSamplesForSessions()` |
| `LiveSessionViewModel.kt` | Add target duration state, projection computation, calorie curve StateFlow |
| `LiveSessionScreen.kt` | Add duration picker (30/45/60 chips); add `LiveCalorieChart` when recording |
| `MainActivity.kt` | Add `evaluation` route |
| `SessionHistoryScreen.kt` | Add "SYN" tag on synthetic cards; debug-only "Seed" + "Evaluate" buttons |

---

## Stage 1 — Phase 3a: Polynomial projection

### PolynomialProjector

Input: `List<Pair<Float, Float>>` — (elapsedMinutes, cumulativeCalories)

Fit a **degree-2 polynomial** (`y = a + bx + cx^2`) via least-squares normal equations.
Solved as a 3x3 linear system (Gaussian elimination) — no math library needed, ~30 lines.

```
Build Vandermonde matrix X where each row = [1, x_i, x_i^2].
Solve (X^T X) * beta = X^T * y  for  beta = [a, b, c].
Extrapolate to targetMinutes by evaluating the polynomial.
```

**Safety rule:** if the extrapolated curve is non-monotonic or goes negative at any point,
fall back to **linear extrapolation** from the slope of the last 2 minutes of data.
This prevents wild quadratic behaviour when only a few minutes of data exist.

Projection only activates after **10 minutes** of recording.

### LiveCalorieChart

- X axis: elapsed minutes
- Y axis: cumulative calories
- Solid series: actual recorded points
- Projection series: projected points (distinct color, ~60% opacity)

> **Vico risk to resolve early:** Vico 2.0.0-beta.3 `LineCartesianLayer` may support a
> custom `LineProvider` with a dashed `PathEffect`. Test first. If not available, use a
> visually distinct color for the projection line (semi-transparent) rather than a workaround.
> A label ("Projected") on the chart makes the distinction clear regardless.

### Target duration picker

- Three `FilterChip`s near the Record button: **30 min / 45 min / 60 min** (default: 45)
- `_targetDurationMinutes: MutableStateFlow<Int>` in `LiveSessionViewModel`
- Passed to `startSession()` as `targetDurationMs = targetDurationMinutes * 60_000L`
- Chips hidden once recording starts

---

## Stage 2 — Synthetic seeder (complete)

### SyntheticSessionGenerator — HR curve model

Each session is parameterized by `(targetHr, durationMin, userProfile)`. One sample/second.

```
Warm-up (0 to warmupMin, default 6 min):
  HR(t) = restHr + (targetHr - restHr) / (1 + exp(-1.2 * (t - warmupMin/2)))
  // Logistic: starts near restHr, reaches ~targetHr by warmupMin

Steady state (warmupMin to cooldownStart):
  HR(t) = targetHr + drift(t) + noise
  // drift: linear +5 bpm over the steady-state period (cardiovascular drift)
  // noise: Gaussian with sigma=4 bpm, clamped to +/-10 bpm

Cooldown (last 6 min):
  HR(t) = restHr + (peakHr - restHr) * exp(-0.4 * (t - cooldownStart))
  // Exponential decay back toward restHr

Constants: restHr = 68 bpm, warmupMin = 6, cooldownMin = 6
```

Apply `CalorieCalculator.calPerSample()` to each second -> accumulate `cumulativeCalories`.
Write as real `Session` + `HrSample` rows with `notes = "synthetic"`.

### Seeder parameters (12 sessions, varied but realistic)

| Zone | Target HR | Durations |
|---|---|---|
| Zone 2 | ~125 bpm | 28, 32, 35, 40, 45 min |
| Zone 3 | ~147 bpm | 30, 35, 40, 45, 50 min |
| Zone 4 | ~165 bpm | 25, 30 min |

All sessions use the user's saved `UserProfile` so calories are personalized.

### UI integration

- **"SYN" tag** on history cards where `session.notes == "synthetic"` — small colored label
  next to the date, always visible so synthetic sessions are distinguishable at a glance.
- **Seed button** in `SessionHistoryScreen`, shown only when `BuildConfig.DEBUG`.
  One tap -> coroutine seeds all 12 sessions -> snackbar "12 synthetic sessions seeded".

---

## Stage 3 — Phase 3b: Historical blend (COMPLETE)

### HistoricalAverager

1. Query qualifying sessions: `endTimeMs != null`, `avgBpm > 100`, duration > 10 min
   - In development: include synthetic sessions so Phase 3b can be tested immediately
   - In production intent: only real sessions contribute to the blend
2. Load all `HrSample` rows for qualifying sessions (bulk query)
3. Resample each session to 1 point per minute (last sample in each minute window)
4. Average point-by-minute across all sessions -> `historicalCurve: List<Float>`
   - Only average over minutes where >= 3 sessions contribute data
   - Extrapolate linearly beyond that point
5. Blend: `projection[t] = 0.4 * polynomial[t] + 0.6 * historical[t]`

### Activation logic in LiveSessionViewModel

- `qualifyingSessionCount: StateFlow<Int>` — exposed to UI
- `< 10 sessions`: polynomial only, label "Polynomial projection"
- `>= 10 sessions`: blended, label "Historical blend"

---

## Stage 4 — Evaluation screen (debug builds only, `BuildConfig.DEBUG`) ← NEXT

### EvaluationViewModel

Generates **10 held-out test sessions** in memory (same generator, never written to Room).

For each test session x each observation window (5, 10, 15, 20 min):
- Feed first N minutes to `PolynomialProjector`
- Get projected final calories at the session's actual duration
- Compare to synthetic ground truth

Metrics:
```
MAE  = mean( |projected_i - actual_i| )
MAPE = mean( |projected_i - actual_i| / actual_i ) * 100
```

Exposes results as `StateFlow<EvaluationResult?>` where `EvaluationResult` holds:
- `maeByWindow: Map<Int, Float>`   // key = observation minutes
- `mapeByWindow: Map<Int, Float>`
- `testCurves: List<TestCurveData>` // for the overlay chart

### EvaluationScreen — three panels

**1. Overlay chart**
5 sample test sessions. Each shows:
- Actual cumulative cal curve (solid)
- Projected curve from 10-minute mark (distinct color)
Gives immediate visual intuition for how well the projection fits.

**2. Accuracy vs elapsed time**
X axis: observation window (5 / 10 / 15 / 20 min)
Y axis: MAE (left) and MAPE% (right)
This is the key portfolio chart — "accuracy improves as the session progresses."

**3. Phase 3a vs 3b comparison**
Side-by-side MAE for polynomial-only vs blended at each window.
Only populated when >= 10 qualifying sessions exist.

### Navigation

`evaluation` route registered in `MainActivity`.
In `SessionHistoryScreen`: `TextButton("Evaluate Projections")` shown only when `BuildConfig.DEBUG`.

---

## Risks and tradeoffs

| Risk | Severity | Mitigation |
|---|---|---|
| Vico dashed lines not supported | Medium | Test in Stage 1; fall back to distinct color if needed |
| Polynomial overfits at short windows | Low | Monotonicity fallback to linear already planned |
| Historical curve length mismatch | Low | Only average minutes with >= 3 contributing sessions |
| Bulk hr_samples load (~26k rows) | Low | Background coroutine via Room; ~2-3 MB, handles fine |
| BuildConfig.DEBUG in Compose | None | Compile-time constant; stripped from release builds |

---

## Build order summary

```
Stage 1  PolynomialProjector -> LiveCalorieChart -> duration chips -> wire LiveSessionViewModel
Stage 2  SyntheticSessionGenerator -> SYN tag -> seed button -> verify in history
Stage 3  HistoricalAverager -> blend logic -> projection mode label on chart
Stage 4  EvaluationViewModel -> EvaluationScreen -> accuracy charts
```

Each stage is independently testable. Stage 1 works with a single real 15-minute session.
Stages 2-4 work entirely on synthetic data.

---

## Decisions already made

- Duration picker: three chips (30 / 45 / 60 min)
- Synthetic session indicator: "SYN" tag on history cards
- Evaluation screen: debug builds only (`BuildConfig.DEBUG`)
- No new Room schema version needed
- No external math libraries — polynomial fit via pure Kotlin Gaussian elimination
