# Changelog

All notable changes to PulseCoach are documented here, organized by development phase.

---

## Selectable App Themes

- Added `AppTheme` enum with three options: **Default** (light, blue primary), **Dark** (deep neutral backgrounds, soft blue primary), **Synthwave** (indigo-black, neon magenta + electric cyan — Outrun aesthetic)
- `ThemeRepository` persists the selection in SharedPreferences (`pulse_coach_theme`)
- `MainActivity` holds theme state as `mutableStateOf`; wraps the entire composition in `MaterialTheme(colorScheme = selectedTheme.colorScheme)` — theme changes take effect instantly without an Activity restart
- Status bar icon color is reactive: Default → dark icons; Dark/Synthwave → light icons via `SideEffect` + `enableEdgeToEdge`
- Theme selector (Material3 `ExposedDropdownMenuBox`) added to the bottom of the Settings screen under a new **Appearance** section
## Bug Fix — Spurious 0 BPM Readings Corrupting HR Chart Y-Axis

- Polar H10 occasionally emits a 0 (or near-zero) BPM sample during connection; these values collapsed the dynamic y-axis range to include 0, making the chart unreadable
- Added a `< 30 bpm` guard at the top of the HR sample collector in `LiveSessionViewModel` — sub-30 readings are skipped entirely via `return@collect`
- The guard drops the bad sample before it reaches chart history, zone classification, calorie accumulation, or Room — no downstream effects
- 30 bpm chosen as the threshold: physically impossible during any exercise, well clear of the lowest valid resting HR

---

## HR Chart — Dynamic Y-Axis Zoom

- Y-axis now tracks the actual data range for the current 5-minute window ±10 bpm on each side, so steady-state workouts fill the chart height instead of being squashed against a fixed 50 bpm floor
- Falls back to `minY = 50` / auto-top when fewer than 10 readings have been recorded (clean startup state)
- Axis labels move as the range shifts — intentional; accurate scale matters more than label stability

---

## HR Chart — 5-Minute Fixed Window

- `MAX_HR_HISTORY` increased from 60 → 300 samples (5 minutes at 1 Hz)
- Chart x-axis pinned to a fixed 0–300 second range; data fills left-to-right with empty space on the right for future readings, rolling once the buffer is full
- Tick marks every 30 seconds; minute labels only at full-minute positions (1m, 2m, 3m, 4m)
- Scroll disabled — window is fixed, not scrollable
- `CLAUDE.md` updated with `HorizontalAxis.ItemPlacer.aligned()` and `series(x, y)` API notes

---

## Session Max BPM

- `maxBpm: Int` added to `Session` domain model and `SessionEntity` (Room v4 → v5 migration: `ALTER TABLE sessions ADD COLUMN maxBpm INTEGER NOT NULL DEFAULT 0`)
- `LiveSessionViewModel` tracks `maxBpmSeen` during recording; resets on start, updated per HR sample, passed to `finishSession()`
- `SessionRepository.finishSession()` accepts and persists `maxBpm`
- `SessionHistoryScreen` card stats line now shows `max N bpm` between avg BPM and avg cal/min; silently omitted for pre-v5 sessions where `maxBpm == 0`

---

## Bug Fix — Zone Classification Using Stale Defaults

- `LiveSessionViewModel.zoneConfig` StateFlow was started with `SharingStarted.WhileSubscribed`,
  which only activates the upstream Room query while a UI composable subscribes to it.
  No composable in `LiveSessionScreen` collected `zoneConfig` directly, so the Flow never
  started and `zoneConfig.value` remained permanently at `ZoneConfig.defaults`.
- At runtime this caused every HR reading to be classified against the hardcoded default
  thresholds (e.g. `zone2MaxBpm = 135`) rather than the user's saved thresholds, misclassifying
  zones for any user who had customised their zone settings.
- Fixed by changing to `SharingStarted.Eagerly` so the Room query starts at ViewModel
  creation and the saved config is loaded before any HR data arrives.

---

## Phase 7 — Projection Calibration
*Commits: `2df01c9`, `445d8f1`, `2fee918`*

### Stage 1 — Personal Bias Correction
- New `ProjectionCalibrator` (util/) — singleton object matching existing util patterns
- After each completed session, computes `ratio = actual_calories / projected_calories_at_actual_duration` via linear interpolation of the projected curve
- Maintains a rolling mean correction factor in SharedPreferences (`pulse_coach_calibration`)
  - Keys: `proj_correction_factor` (Float), `proj_correction_n` (Int)
  - Outlier guard: ratios outside [0.5, 2.0] are discarded
  - No-op (factor = 1.0) until 3 valid ratios are accumulated
- `stopRecording()` snapshots `cumulativeCalories` and `_projectedCalorieCurve` before the coroutine, calls `updateFactor()` after `finishSession()`
- Per-minute projection block applies `getCorrectionFactor()` × `applyTo()` before assigning to `_projectedCalorieCurve`
- No UI changes — correction applied transparently

### Stage 2 — Prediction Intervals
- `ProjectionCalibrator` extended with ratio list (`proj_ratios`, comma-separated String, capped at 50 sessions)
- `getProjectionSigma()` returns sample standard deviation (Bessel-corrected) of stored ratios, or null if fewer than 5 sessions
- `_projectionBand: MutableStateFlow<Float?>` added to `LiveSessionViewModel`; set each minute via `getProjectionSigma()`, cleared on `stopRecording()`
- `LiveCalorieChart` expanded to 4-series Vico layout:
  - Series 0: actual (solid primary)
  - Series 1: projected (45% opacity)
  - Series 2/3: upper/lower confidence band (20% opacity)
  - All 4 series always present in model; 2-point dummies used when inactive
  - Band formula: `upper(t) = projection(t) × (1 + σ)`, `lower(t) = projection(t) × (1 − σ)`
  - Caption extended: shows `±N% historical range` when band is active
- `projectionBand` threaded through `ConnectedContent` and `LiveSessionScreen`

### Testing
- 21 pure JVM unit tests in `ProjectionCalibratorTest` covering `applyTo`, `interpolateProjection`, `computeRollingMean`, `computeSigma`
- Added Robolectric 4.13 (`testImplementation`); configured `testOptions.unitTests.isIncludeAndroidResources = true`
- 11 Robolectric tests in `ProjectionCalibratorPrefsTest` covering SharedPreferences round-trip, `n < 3` guard, outlier rejection, sigma gate

---

## Phase 6 — Live Zone Time Tracking
*Commit: `33bd59d`*

- `zoneSecondsArray: IntArray` accumulator added to `LiveSessionViewModel`; incremented each second inside the recording guard
- `zoneSeconds: StateFlow<Map<Int, Int>>` exposed to UI
- `ZoneTimeSummary` composable on `LiveSessionScreen` while recording: proportional color bar + M:SS time-in-zone labels for all 5 zones
- Room v3 → v4 migration: adds `zone1Seconds` through `zone5Seconds` (INTEGER NOT NULL DEFAULT 0) to `sessions` table
- `finishSession()` writes zone splits at session end
- `SessionHistoryScreen` shows compact 6dp zone bar on session cards (hidden for pre-v4 sessions with all-zero splits)

---

## Phase 5 — BLE Reconnection
*Commit: `b165ff5`*

- `LiveSessionViewModel` tracks `lastConnectedDeviceId`
- On `Disconnected` event: fires `reconnectJob` — up to 5 retries with 3-second delay between attempts
- `isReconnecting: StateFlow<Boolean>` exposed to UI
- Voluntary `disconnect()` clears `lastConnectedDeviceId` first to suppress reconnect loop
- `ReconnectingContent` composable: spinner + "Recording paused" note when session is active during reconnect
- `onCleared()` cancels `reconnectJob` to prevent memory leak

---

## Phase 4 — Session Intent Classification
*Commit: `b165ff5`*

### Stage 1 — Data Layer
- `SessionType` enum: `RECOVERY`, `STEADY`, `PUSH` (stored as String in Room, nullable)
- Room v2 → v3 migration: `ALTER TABLE sessions ADD COLUMN sessionType TEXT`
- `SessionDao`: `getQualifyingSessionsByType(type)`, `updateSessionType(id, type)`
- `SessionRepository`: wrappers for both new DAO queries

### Stage 2 — Projection Filtering
- `HistoricalAverager.durationBucketFor()`: maps actual session duration to nearest bucket (20/30/45/60 min)
  - ≤ 25 min → 20, 26–37 → 30, 38–52 → 45, ≥ 53 → 60
- `HistoricalAverager.getFilteredCurve()`: hierarchical fallback ladder
  - Tier 1: same type + same duration bucket, N ≥ 10
  - Tier 2: same type, any duration, N ≥ 10
  - Tier 3: null (polynomial only)
  - Types are never mixed
- 7 unit tests covering all fallback tiers

### Stage 3 — Live Session UI
- 20 min chip added to duration picker (now 20/30/45/60)
- `IntensityPicker` chip row on `LiveSessionScreen`: Recovery / Steady / Push; tapping selected chip deselects it
- `flatMapLatest` in `LiveSessionViewModel.init` switches qualifying session query when type changes

### Stage 4 — History & Post-Session
- `stopRecording()` stamps `targetDurationMs` using `durationBucketFor(actualDuration)`
- `SessionHistoryScreen`: intensity chip on each card (colored); tap opens edit dialog
- Edit dialog: RadioButtons for RECOVERY/STEADY/PUSH + Clear + Save/Cancel
- `SessionHistoryViewModel.updateSessionType()`

### Evaluation update
- `EvaluationScreen` Panel 4 added: per-type poly vs. typed-blend accuracy
- `TypedMetrics` data class; `testParamTypes` assigns held-out sessions a type
- Observation windows reduced to 10/15/20 min (5-min window removed — projector requires ≥ 10 min)

---

## Phase 3 — Projection Engine
*Commits: `dd6afc4`, `c7a2f0e`, `6d2b336`*

### Stage 1 — Polynomial Projection
- `PolynomialProjector`: degree-2 least-squares fit via closed-form normal equations solved by Gaussian elimination with partial pivoting; no external math library
- Monotonicity safety: if quadratic curves back down before target time, falls back to linear extrapolation from last 2 minutes of actual slope
- Activates after 10 minutes of recorded data
- Duration picker: 20/30/45/60 min `FilterChip`s on `LiveSessionScreen`
- `LiveCalorieChart`: actual line (solid primary) + projected line (45% opacity); 2-point dummy series when no projection
- 12 unit tests for `PolynomialProjectorTest`

### Stage 2 — Synthetic Seeder
- `SyntheticSessionGenerator`: parameterized HR curves — logistic warm-up, Gaussian steady-state with cardiovascular drift (+5 bpm linear), exponential cooldown
- Seed button (debug only) writes 12 synthetic sessions across Zone 2/3/4, varied durations
- "SYN" tag on history cards where `session.notes == "synthetic"`

### Stage 3 — Historical Blend
- `HistoricalAverager.buildCurve()`: per-minute average cumulative calorie curve across qualifying sessions
- Minutes with fewer than 3 contributing sessions filled by linear extrapolation
- Blend formula: `projection(t) = 0.4 × polynomial(t) + 0.6 × historical(t)`
- Activates at ≥ 10 qualifying sessions
- Chart caption: "Polynomial projection" vs "Historical blend" depending on mode
- 8 unit tests for `HistoricalAverager`

### Stage 4 — Evaluation Screen (debug builds only)
- `EvaluationViewModel`: hold-out accuracy test on session history; observation windows 10/15/20 min
- `EvaluationScreen`: MAE/MAPE accuracy tables, projected vs. actual overlay chart, poly vs. blend comparison
- Accessible via debug button in `SessionHistoryScreen`

---

## Phase 2 — Session Recording
*Commits: `ac90fb0`, `dd6afc4`*

### Core
- User profile setup (age, weight, sex) — SharedPreferences; first-launch onboarding + editable from Settings
- Calorie estimation: Keytel et al. (2005) formula; applied per 1Hz BPM sample; suppressed below 90 BPM
- Session start/stop recording — writes to Room (`sessions` + `hr_samples` tables)
- Live cal/min display in zone banner (always on while connected)
- Session total calories + elapsed time + recording indicator on live screen
- Session history list (newest first): date/time, duration, total cal, avg BPM
- CSV export to Downloads folder via MediaStore API (Android 10+)

### Polish
- Running avg BPM and avg cal/min in recording stats row
- Avg cal/min derived stat on history cards (`totalCalories / durationMinutes`)
- Multi-select delete with confirmation dialog
- HR chart y-axis floor fixed at 50 BPM; x-axis labeled in seconds
- BPM display font replaced with explicit 96sp value
- Spurious "Check sensor contact" warning removed (`contactStatus` unreliable on H10)

### Testing
- `CalorieCalculatorTest`: Keytel formula (male/female), sub-90 BPM guard, negative clamp, `calPerSample`
- `ZoneCalculatorTest`: every zone boundary, custom `ZoneConfig`, color opacity, name fallbacks

---

## Phase 1 — Live HR + Zones
*Commit: `b48f505`*

- Polar H10 BLE scan, connect, disconnect
- Live HR stream (1 reading/sec) via `callbackFlow` bridge from RxJava Flowable
- Zone classification (1–5) against user-configurable BPM thresholds
- Zone color banner — screen background updates with current zone
- Live scrolling 60-second HR chart (Vico) with BPM y-axis labels
- Zone strip indicator (Z1–Z5) below chart
- Settings screen — zone threshold sliders, persisted to Room (`zone_config` table)
- BLE permissions: `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` (Android 12+) with runtime checks
