# PulseCoach

A personal Android training companion for the **Polar H10** heart rate monitor.
Real-time HR zones, caloric effort tracking, and a self-personalizing projection engine
that learns your workout patterns over time.

Built with Kotlin + Jetpack Compose. Fully offline — no accounts, no cloud, no analytics SDKs.

**Stack:** Kotlin · Jetpack Compose · Polar BLE SDK 5.4.0 · Vico Charts · Room · Coroutines/Flow

---

*I built this because I was doing long elliptical sessions and mentally tracking
tracking and projecting the whole time, which gets harder the more you push. 
Polar Flow's more useful features are paywalled, and little of what I actually wanted existed
in a single tool: live and average calorie rates, a projection of where I'd end up given my
current effort, and a model that learns from my own sessions rather than population averages. 
This is that tool — and also a learning project in Android development and applied analytics.*

---

## What It Does

Connect your Polar H10, tap Start, and PulseCoach:

- Streams heart rate at 1 Hz over BLE and classifies each reading into one of five
  configurable HR zones in real time
- Estimates calorie burn per second using the Keytel et al. (2005) formula,
  personalized to your age, weight, and sex
- After 10 minutes of recording, projects your cumulative calorie total to the end
  of your target session duration
- Blends that projection with your personal training history for a more stable estimate
- Applies a self-calibrating bias correction that learns how your actual sessions
  compare to their projections over time
- Displays a confidence band around the projection reflecting your historical
  session-to-session variability

Sessions are saved to a local Room database. Export any session as a CSV to Downloads.

---

## Analytical Methods

This project is also a portfolio piece demonstrating applied quantitative methods
on real hardware. The projection engine is a four-layer pipeline:

- **Physiological regression** — Keytel et al. (2005) formula accumulated via 1Hz
  numerical integration (left Riemann sum)
- **In-session forecasting** — degree-2 polynomial OLS fit via closed-form normal
  equations, solved by Gaussian elimination with partial pivoting; monotonicity
  constraint with linear fallback on concave-down failure
- **Historical blend** — weighted ensemble (0.4 polynomial / 0.6 historical mean),
  stratified by session intent and duration with a hierarchical data-sufficiency
  fallback ladder
- **Bias correction + uncertainty** — self-personalizing multiplicative correction
  factor (rolling mean of actual/projected ratios); proportional prediction intervals
  scaled by sample standard deviation of past errors (Bessel-corrected)

Accuracy is evaluated in-app via MAE/MAPE on held-out sessions. No external math
libraries — implemented from scratch in Kotlin.

See [`ANALYSIS.md`](ANALYSIS.md) for full trade-off analysis of approaches considered,
including ETS, splines, and Kalman filtering.

---

---

## Projection Engine

The calorie projection runs a four-layer pipeline:

**1. Polynomial fit**
Every minute, a degree-2 polynomial (`y = a + b·t + c·t²`) is fit to the
cumulative calorie curve via closed-form least-squares normal equations, solved
by Gaussian elimination with partial pivoting. The fitted curve is extrapolated
to the target session end time. If the parabola curves back down (concave-down
with peak before target — a failure mode with sparse early data), the projector
falls back to linear extrapolation from the recent slope.

**2. Historical blend**
Once you have 10+ sessions of the same type and duration, the polynomial is blended
with a per-minute average curve from your past sessions:
`projection(t) = 0.4 × polynomial(t) + 0.6 × historical_average(t)`
Sessions are stratified by intent (Recovery / Steady / Push) and duration bucket
(20 / 30 / 45 / 60 min). A hierarchical fallback ladder relaxes the filter if a
stratum is too sparse.

**3. Personal bias correction**
After each session, `ratio = actual_calories / projected_calories_at_actual_duration`
is folded into a rolling mean correction factor stored in SharedPreferences.
The factor is a no-op until 3 valid ratios are accumulated; outlier ratios outside
[0.5, 2.0] are discarded. All future projections are multiplied by this factor.
The ratio is Keytel/Keytel — it does not correct the formula's absolute accuracy,
it learns how your actual session trajectories compare to your own average trajectory.

**4. Prediction intervals**
The standard deviation of past ratios (Bessel-corrected, active after 5 sessions)
is used to render a proportional confidence band:
`upper(t) = projection(t) × (1 + σ)`,  `lower(t) = projection(t) × (1 − σ)`.
The band widens at later minutes because σ scales with the projected calorie value —
uncertainty compounds over the forecast horizon.

The full analytical rationale and trade-off analysis is in [`ANALYSIS.md`](ANALYSIS.md).

---

## Features

### Live Session Screen
- Zone color banner (5 configurable zones, Z1–Z5)
- Scrolling 60-second HR chart
- Live cal/min display
- Cumulative calorie chart with projection + confidence band (after 10 min)
- Target duration picker (20 / 30 / 45 / 60 min)
- Session intent picker (Recovery / Steady / Push) — affects historical blend
- Zone time summary bar — proportional color segments with M:SS time-in-zone labels
- Auto-reconnect on BLE signal drop (up to 5 retries, 3-second backoff)

> **Note — Polar Flow conflict:** The H10 supports only one active BLE connection at a time.
> If the official Polar Flow app is installed and running in the background, it holds the H10's
> HR notification subscription, causing PulseCoach to fail on stream start with
> `PolarServiceNotAvailable`. Force-close Polar Flow before using PulseCoach, or uninstall it.
> Polar Flow is only needed for firmware updates.

### Session History
- Chronological session cards with zone distribution bar, intensity chip, total calories,
  avg BPM, duration
- Tap intensity chip to edit session classification
- Multi-select delete with confirmation
- CSV export via MediaStore API (Android 10+)

### Settings & Profile
- Per-zone threshold sliders (persisted to Room)
- User profile (age, weight, sex) — persisted to SharedPreferences; used by Keytel formula

---

## Architecture

**Pattern:** MVVM with a Repository layer. One ViewModel per screen.

```
app/src/main/kotlin/com/pulsecoach/
  ble/          PolarBleManager — BLE scan, connect, HR stream (callbackFlow bridge)
  data/         Room entities, DAOs, PulseCoachDatabase (v5)
  model/        Pure data classes (HrReading, Session, HrSample, ZoneConfig, UserProfile, SessionType)
  repository/   SessionRepository, ZoneConfigRepository, UserProfileRepository
  ui/           LiveSessionScreen, SessionHistoryScreen, ProfileSetupScreen,
                SettingsScreen, EvaluationScreen (debug),
                LiveHrChart, LiveCalorieChart, ZoneTimeSummary
  util/         ZoneCalculator, CalorieCalculator, CsvExporter,
                PolynomialProjector, HistoricalAverager,
                SyntheticSessionGenerator, ProjectionCalibrator
  viewmodel/    LiveSessionViewModel, SessionHistoryViewModel,
                SettingsViewModel, ProfileViewModel, EvaluationViewModel
  MainActivity  NavHost — routes: live_session, settings, profile_setup,
                profile_edit, session_history, evaluation (debug only)
```

**Key decisions:**
- StateFlow everywhere — no LiveData
- All Polar SDK / BLE operations run on background coroutines via `callbackFlow`;
  RxJava Flowables from the SDK never escape `PolarBleManager`
- Room v5 — migrations 1→2, 2→3 (sessionType column), 3→4 (zone split columns), 4→5 (maxBpm column)
- User profile in SharedPreferences, not Room — single record, not tabular data
- Calibration state in SharedPreferences (`pulse_coach_calibration`) — scalar factor
  and ratio list, no schema version dependency

---

## Build & Run

**Prerequisites**
- Android Studio (provides JDK, Android SDK, Gradle)
- `JAVA_HOME` pointing to Android Studio's JBR — add to `~/.bashrc` in Git Bash:
  ```bash
  export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
  ```
- Developer Options + USB Debugging enabled on your Android device
  (Settings → About Phone → tap Build Number 7×)

**Commands** (run from Git Bash in project root)
```bash
./gradlew assembleDebug     # build only
./gradlew installDebug      # build + install to connected device (preserves data)
./gradlew test              # all JVM unit tests, no device needed
```

Test report: `app/build/reports/tests/testDebugUnitTest/index.html`

---

## Tests

75 JVM unit tests — no device or emulator required.

| Test class | What it covers |
|---|---|
| `CalorieCalculatorTest` | Keytel formula (male/female), sub-90 BPM guard, negative clamp, `calPerSample` |
| `ZoneCalculatorTest` | Every zone boundary, custom `ZoneConfig`, color opacity, name fallbacks |
| `PolynomialProjectorTest` | Gaussian elimination, quadratic fit recovery, monotonicity, guard conditions |
| `HistoricalAveragerTest` | Per-minute averaging, linear extrapolation, blend weighting |
| `HistoricalAveragerFallbackTest` | Tier 1/2/3 fallback ladder, typed filtering |
| `ProjectionCalibratorTest` | `applyTo`, `interpolateProjection`, `computeRollingMean`, `computeSigma` (pure functions) |
| `ProjectionCalibratorPrefsTest` | SharedPreferences round-trip, n < 3 guard, outlier rejection, sigma gate — via Robolectric |
| `EvaluationViewModelTest` | MAE/MAPE accuracy calculations, per-type metrics |

Robolectric 4.13 is configured for tests requiring Android context (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`).

---

## Debug Tools

A debug-only **Evaluation Screen** is accessible from the session history menu. It:
- Runs a held-out accuracy test on your session history
- Reports MAE and MAPE for polynomial-only vs. blended projections
- Breaks results down by session type (Recovery / Steady / Push)
- Shows an overlay chart of projected vs. actual curves
- Observation windows: 10, 15, and 20 minutes

A **synthetic session seeder** (debug only) generates parameterized HR curves
(logistic warm-up → Gaussian steady-state → exponential cooldown) and writes them
to Room. Synthetic sessions are tagged "SYN" in the history list. Useful for
populating the historical baseline without running real workouts.

---

## AI Collaboration

Built with [Claude Code](https://claude.ai/code) (Anthropic).
See [`CLAUDE.md`](CLAUDE.md) for full project architecture constraints and SDK gotchas.