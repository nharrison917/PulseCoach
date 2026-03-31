Project Identity
•	Name: PulseCoach
•	Type: Personal Android application (Kotlin + Jetpack Compose)
•	Hardware: Polar H10 heart rate monitor via Bluetooth LE
•	Owner: Solo developer — beginner learning Android with AI assistance
•	Goal: Real-time HR zones, caloric effort tracking, personal projection engine

Developer Context
I am a graduate student in quantitative business analytics learning to code. I am comfortable with data concepts and logic, but new to Android, Kotlin, and mobile architecture. When helping me:
•	Explain why, not just what. If you write a pattern I haven't seen before, add a one-line comment explaining the concept.
•	Prefer simple and readable over clever and concise. I will trade 5 lines for clarity every time.
•	Flag when something is a beginner trap — e.g., doing network/BLE work on the main thread, memory leaks from unregistered listeners.
•	When I ask a vague question, ask one clarifying question before writing code.
•	Do not refactor working code unless I ask. Stability matters more than perfection right now.

Architecture
Pattern
MVVM (Model-View-ViewModel) with a Repository layer. Do not suggest other patterns without a strong reason.
Package Structure
com.pulsecoach/
  ui/           Compose screens and components
  viewmodel/    ViewModels — one per screen
  repository/   Data access layer (BLE + Room)
  data/         Room entities, DAOs, Database
  ble/          Polar SDK wrapper and HR stream logic
  model/        Pure data classes (Session, HrSample, HrReading, ZoneConfig, UserProfile, SessionType)
  util/         CalorieCalculator, ZoneCalculator, CsvExporter, PolynomialProjector,
                HistoricalAverager, SyntheticSessionGenerator, ProjectionCalibrator
State Management
Use StateFlow and collectAsState() in Compose. Avoid LiveData — StateFlow is the current standard.

Key Libraries & Versions
Pinned versions (do not change without a reason — these are known to compile together):
•	AGP: 8.7.3
•	Kotlin: 2.1.0
•	KSP: 2.1.0-1.0.29
•	Compose BOM: 2025.02.00
•	Room: 2.6.1
•	Coroutines: 1.9.0
•	Polar BLE SDK: com.github.polarofficial:polar-ble-sdk:5.4.0 (JitPack)
•	RxJava3: 3.1.9 / RxAndroid: 3.0.2 (required by Polar SDK)
•	Vico: com.patrykandpatrick.vico:compose-m3:2.0.0-beta.3
•	Material: com.google.android.material:material:1.12.0

BLE & Hardware Rules
•	All BLE operations must run on a background coroutine. Never block the main thread.
•	The Polar SDK uses RxJava internally. Wrap emissions in callbackFlow to bridge to Kotlin coroutines.
•	Always unregister the BLE callback in onDestroy / onCleared to prevent memory leaks.
•	The H10 sends HR once per second. Do not attempt to poll faster.
•	BLE permissions required: BLUETOOTH_SCAN, BLUETOOTH_CONNECT (Android 12+). Add runtime permission checks.

Calorie Calculation
Use the Keytel et al. (2005) formula. User inputs (age, weight, sex) are stored in SharedPreferences under UserProfile.
Male formula
cal/min = (-55.0969 + 0.6309*HR + 0.1988*weight_kg + 0.2017*age) / 4.184
Female formula
cal/min = (-20.4022 + 0.4472*HR - 0.1263*weight_kg + 0.074*age) / 4.184
Clamp output to >= 0. Do not apply this formula below HR = 90 bpm (unreliable range).
Each H10 sample covers 1 second = 1/60 minute; use CalorieCalculator.calPerSample() to accumulate.

Zone Logic
Zones are user-defined. ZoneConfig is stored in Room. The defaults below are suggestions only — the user may change them:
•	Zone 1 (Recovery): < 115 bpm
•	Zone 2 (Aerobic): 115-135 bpm
•	Zone 3 (Tempo): 136-155 bpm
•	Zone 4 (Threshold): 156-175 bpm
•	Zone 5 (Max): > 175 bpm
Zone colors: use a consistent palette. Suggested: Z1=#80B4FF, Z2=#80E27E, Z3=#FFD54F, Z4=#FF8A65, Z5=#EF5350

Data Storage
•	Room database: PulseCoachDatabase (version 5, increment on schema change with migration).
•	Tables: sessions, hr_samples, zone_config.
•	Migrations: 1→2, 2→3 (sessionType TEXT), 3→4 (zone1Seconds–zone5Seconds INTEGER NOT NULL DEFAULT 0), 4→5 (maxBpm INTEGER NOT NULL DEFAULT 0).
•	User profile (age, weight, sex): SharedPreferences ("user_profile"), not Room.
•	Calibration state: SharedPreferences ("pulse_coach_calibration") — keys: proj_correction_factor (Float), proj_correction_n (Int), proj_ratios (String, comma-separated).
•	Never delete session data without explicit user confirmation.
•	Storage estimate: ~150 KB per 30-minute session in Room; ~90 KB as CSV export.

CSV Export Format
Export one file per session. Filename format: pulsecoach_YYYYMMDD_HHMMSS.csv
Required columns:
timestamp_ms, bpm, zone, cal_per_min, cumulative_cal
Write to the Downloads folder using MediaStore API (Android 10+). Pre-API-29 devices get a graceful error message — do not add WRITE_EXTERNAL_STORAGE complexity.

Projection Engine
•	After 10 minutes of data, PolynomialProjector fits a degree-2 least-squares curve to the cumulative calorie series and extrapolates to the target session end time. Falls back to linear if the quadratic is non-monotonic.
•	After 10+ qualifying sessions, HistoricalAverager blends the polynomial (0.4) with a per-minute historical average (0.6). Sessions are stratified by SessionType and duration bucket (20/30/45/60 min) with a three-tier fallback ladder.
•	ProjectionCalibrator applies a personal bias correction factor (rolling mean of actual/projected ratios) and a fractional confidence band (sample σ of past ratios). Both activate only after a minimum number of sessions. See ANALYSIS.md for full mathematical detail.

Testing Strategy
•	JVM unit tests in src/test/ — run with ./gradlew test (no device needed).
•	JUnit 4: testImplementation("junit:junit:4.13.2").
•	Robolectric: testImplementation("org.robolectric:robolectric:4.13"). Use @RunWith(RobolectricTestRunner::class) + @Config(sdk = [34]) for any test requiring Android context (SharedPreferences, etc.). testOptions.unitTests.isIncludeAndroidResources = true is set.
•	Use a mock BLE data source (emitting fake HR values on a timer) for UI testing without hardware.
•	UI instrumentation tests are available if needed.

Code Style
•	Kotlin idioms preferred: use data classes, sealed classes for state, extension functions for utilities.
•	No Hungarian notation. No abbreviations in variable names except well-known ones (hr, bpm, id).
•	Every public function gets a one-line KDoc comment.
•	If a function exceeds ~30 lines, suggest a refactor and ask before proceeding.

What NOT to Do
•	Do not add Firebase, analytics SDKs, or any external network calls. This app is fully offline.
•	Do not suggest Fragments — this app uses Compose navigation only.
•	Do not use GlobalScope for coroutines. Use viewModelScope or lifecycleScope.
•	Do not add dependencies without explaining what they are and why they are needed.
•	Do not generate placeholder UI with Lorem Ipsum — use realistic training data in previews.

Token Efficiency (lessons from this project)
•	Grep before you read. A targeted grep to find the exact lines you need is almost always cheaper than reading a whole file.
•	Infer schema from mapping helpers. SessionRepository's toDomain() / toEntity() already shows every field — no need to also read the entity files.
•	Check for existing usage before adding new APIs. Before importing BuildConfig, grep the codebase for it. No hits → check build.gradle.kts for the opt-in flag before writing any code.
•	Write imports before code, not after. Discovering a missing import mid-implementation forces an extra edit round.
•	State assumptions rather than asking. For low-stakes design choices (reactive vs one-shot query, timestamp-based vs field-based duration), state the assumption and proceed. Only stop to ask when the decision affects scope or is hard to reverse.
•	Grep return types instead of reading whole files. To understand what a function returns, grep for "fun functionName" — the signature is almost always on one line. No need to read the full file.

Key StateFlow grep targets (avoids full ViewModel reads):
•	Recording state: _isRecording
•	Projection output: _projectedCalorieCurve
•	Confidence band: _projectionBand
•	Calorie curve data: _actualCalorieCurve
•	Target duration: _targetDurationMinutes
•	Historical blend state: _qualifyingSessionCount, historicalCurve
•	Zone time splits: _zoneSeconds

SDK Gotchas (hard-won — read before touching these APIs)
Vico 2.0.0-beta.3
•	ShapeComponent constructor: use ShapeComponent(fill = Fill(color.toArgb())) — there is no color parameter. Fill wraps an ARGB int. Import: com.patrykandpatrick.vico.core.common.Fill
•	HorizontalLayout does not exist in this version. Do not import or use it.
•	HorizontalBox exists but DO NOT use it for zone band decorations. Vico does not clip decoration draws to the chart canvas in this version — any HorizontalBox with y bounds outside the visible range will paint over UI elements above and below the chart. Zone context is provided by the ZoneStrip composable below the chart instead.
•	CartesianLayerRangeProvider is in com.patrykandpatrick.vico.core.cartesian.data (not .layer). Use CartesianLayerRangeProvider.fixed(minY = 50.0) to set a y-axis floor. The fixed() parameters are nullable Double? (not Double with NaN).
•	CartesianValueFormatter is a fun interface in com.patrykandpatrick.vico.core.cartesian.data — pass a lambda directly: CartesianValueFormatter { _, value, _ -> "${value.toInt()}" }
•	rememberLine is a Composable extension on LineCartesianLayer.Companion, defined in com.patrykandpatrick.vico.compose.cartesian.layer. Call it as LineCartesianLayer.rememberLine(fill = ...). Import: import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
•	LineFill.single(fill(color)) is the correct constructor — NOT LineFill.Solid(...). The fill() helper is in com.patrykandpatrick.vico.compose.common and wraps a Compose Color into a core Fill object.
•	Multi-series with different x ranges: use series(x = listOf(...), y = listOf(...)) with explicit x values. Both series in one LineCartesianLayer with LineProvider.series(line0, line1) — series index maps to line style.
•	x values must have at most 4 decimal places of precision (Vico validates via getXDeltaGcd). NEVER use fractional offsets like + 0.01f as dummy x values — IEEE 754 float representation of 0.01 is imprecise and will trigger IllegalArgumentException. Use whole-number offsets like + 1f instead (integers are always exact in float).
•	Axis label TextComponent: rememberTextComponent does NOT exist in com.patrykandpatrick.vico.compose.common for this version. Use TextComponent directly from com.patrykandpatrick.vico.core.common.component.TextComponent. Pass an ARGB int, not a Compose Color: val onSurface = MaterialTheme.colorScheme.onSurface.toArgb(); val axisLabel = remember(onSurface) { TextComponent(color = onSurface) }
•	LiveCalorieChart uses 4 series (actual / projected / upper band / lower band). All 4 must always be present in the model — use 2-point dummies at the last actual position when a series has no real data.
•	HorizontalAxis.ItemPlacer: use HorizontalAxis.ItemPlacer.aligned(spacing = N) to place ticks and label slots every N x-units. Pass as itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = N) } to rememberBottom(). NEVER return "" from a CartesianValueFormatter — Vico throws IllegalStateException ("format returned an empty string"). To suppress labels at certain positions, set ItemPlacer spacing so slots only land where you want labels, not at every tick. For explicit x-values use series(x = listOf(...), y = listOf(...)); both params accept Collection<Number>.

Polar BLE SDK 5.4.0
•	PolarBleApiCallback.deviceDisconnected takes ONE parameter (polarDeviceInfo: PolarDeviceInfo). There is no reason: Int second parameter in this version. Adding it causes a compile error.
•	PolarHrSample.contactStatus is frequently false during normal H10 wear even when readings are valid. Do not surface it as a user-facing warning — the BPM updating is the real indicator of contact.

Material XML Theme
•	The app theme in res/values/themes.xml uses Theme.Material3.DayNight.NoActionBar. This requires the com.google.android.material:material:1.12.0 library even though the rest of the UI is Compose. Without it, processDebugResources fails.
