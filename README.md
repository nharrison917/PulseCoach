# PulseCoach

Personal Android training companion for the Polar H10 heart rate monitor.
Built with Kotlin + Jetpack Compose. Fully offline — no accounts, no cloud.

**Stack:** Kotlin · Jetpack Compose · Polar BLE SDK · Vico charts · Room · Coroutines/Flow

---

## Build & Run

**Prerequisites**
- Android Studio installed (provides JDK, Android SDK, Gradle)
- `JAVA_HOME` set to Android Studio's JBR — add to `~/.bashrc` in Git Bash:
  ```bash
  export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
  ```
- Developer Options + USB Debugging enabled on your Android device
  (Settings → About Phone → tap Build Number 7 times)

**Commands** (run from Git Bash in project root)
```bash
./gradlew assembleDebug     # build only
./gradlew installDebug      # build and install to connected device (preserves data)
./gradlew test              # JVM unit tests (no device needed)
```

---

## Phase 1 — Live HR + Zones ✅

- [x] Polar H10 BLE scan, connect, disconnect
- [x] Live HR stream (1 reading/sec) via callbackFlow bridge from RxJava
- [x] Zone classification (1–5) using user-configurable thresholds
- [x] Zone color banner — screen background updates with current zone
- [x] Live scrolling 60-second HR chart (Vico) with y-axis bpm labels
- [x] Zone strip indicator (Z1–Z5) below chart
- [x] Settings screen — sliders for zone thresholds, persisted to Room
- [x] BLE permission handling (Android 12+ BLUETOOTH_SCAN / BLUETOOTH_CONNECT)

---

## Phase 2 — Session Recording ✅

- [x] User profile setup (age, weight, sex) — stored in SharedPreferences
- [x] First-launch onboarding flow; editable from Settings thereafter
- [x] Calorie/min calculation — Keytel et al. (2005) formula
- [x] Session start/stop recording — writes to Room (`sessions` + `hr_samples` tables)
- [x] Live cal/min display in zone banner (always on while connected)
- [x] Session total calories + recording indicator on live screen
- [x] Session history list screen (newest first, with date/time, duration, cal, avg bpm)
- [x] CSV export to Downloads folder (MediaStore API, Android 10+)
- [x] Fixed: chart y-axis floor at 50 bpm; x-axis labeled in seconds
- [x] Fixed: BPM font replaced with explicit 96sp value
- [x] Fixed: spurious "Check sensor contact" warning removed

**Storage:** ~150 KB per 30-minute session in Room (~1,800 rows at 1 sample/sec).
CSV export of the same session is ~90 KB. 100 sessions ≈ 15 MB total.

**Phase 2 Polish**
- [x] Live session screen: running avg BPM and avg cal/min in recording stats row
- [x] Session history cards: avg cal/min derived stat (totalCalories / durationMinutes)
- [ ] Session history: multi-select delete with confirmation dialog

**Known issues to address at Phase 3 start**
- No BLE reconnection logic — user must scan again after a signal drop.
- No session target duration input yet — needed as the endpoint for Phase 3 projection.

---

## Unit Tests

JVM tests (no device required) covering pure utility functions:

- `CalorieCalculatorTest` — male/female Keytel formula against known values, sub-90 bpm guard, negative clamp, `calPerSample` relationship
- `ZoneCalculatorTest` — every zone boundary at/above/below threshold, custom `ZoneConfig`, color opacity, name fallbacks

Run with `./gradlew test`. Output at `app/build/reports/tests/testDebugUnitTest/index.html`.

---

## Phase 3 — Projection Engine

- [ ] Session target duration input (set before or at session start)
- [ ] Phase 3a: polynomial fit to cumulative calorie curve → project to target end time
- [ ] Phase 3a: display projection as dashed line extension on cal graph
- [ ] Phase 3b: blend projection with historical session average (after 10+ sessions)
- [ ] Developer tuning knobs for blend weights (debug builds only)

---

## Project Structure

```
app/src/main/kotlin/com/pulsecoach/
  ble/          PolarBleManager — BLE scan, connect, HR stream
  data/         Room entities (Session, HrSample, ZoneConfig), DAOs, PulseCoachDatabase v2
  model/        Pure data classes (HrReading, Session, HrSample, ZoneConfig, UserProfile)
  repository/   SessionRepository, ZoneConfigRepository, UserProfileRepository
  ui/           LiveSessionScreen, SessionHistoryScreen, ProfileSetupScreen,
                SettingsScreen, LiveHrChart
  util/         ZoneCalculator, CalorieCalculator, CsvExporter
  viewmodel/    LiveSessionViewModel, SessionHistoryViewModel,
                SettingsViewModel, ProfileViewModel
  MainActivity  NavHost — routes: live_session, settings, profile_setup,
                profile_edit, session_history
```

## Architecture Notes

- **MVVM** with Repository layer. One ViewModel per screen.
- **StateFlow** everywhere — no LiveData.
- **BLE rule:** all Polar SDK operations run on background coroutines via `callbackFlow`.
  The RxJava Flowables from the SDK are never exposed outside `PolarBleManager`.
- **Room version:** 2. Migration 1→2 adds `sessions` and `hr_samples` tables.
  Increment version + add Migration on any schema change.
- **Vico version:** 2.0.0-beta.3. Key gotchas documented in `CLAUDE.md`.
- **User profile:** SharedPreferences (not Room) — single key-value record, not tabular data.
- **CSV export:** MediaStore API (Android 10+). Pre-API-29 shows a graceful error.

---

## AI Collaboration

Built with Claude (Anthropic) via Claude Code.
See `CLAUDE.md` for full project instructions and constraints.
