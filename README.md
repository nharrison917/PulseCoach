# PulseCoach

Personal Android training companion for the Polar H10 heart rate monitor.
Built with Kotlin + Jetpack Compose. Fully offline — no accounts, no cloud.

**Stack:** Kotlin · Jetpack Compose · Polar BLE SDK · Vico charts · Room · Coroutines/Flow

---

## Build & Run

**Prerequisites**
- Android Studio installed (provides JDK, Android SDK, Gradle)
- `JAVA_HOME` set to Android Studio's JDR — add to `~/.bashrc` in Git Bash:
  ```bash
  export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
  ```
- Developer Options + USB Debugging enabled on your Android device
  (Settings → About Phone → tap Build Number 7 times)

**Commands** (run from Git Bash in project root)
```bash
./gradlew assembleDebug     # build only
./gradlew installDebug      # build and install to connected device
./gradlew test              # unit tests
```

---

## Phase 1 — Live HR + Zones ✅

**Checklist**
- [x] Polar H10 BLE scan, connect, disconnect
- [x] Live HR stream (1 reading/sec) via callbackFlow bridge from RxJava
- [x] Zone classification (1–5) using user-configurable thresholds
- [x] Zone color banner — screen background updates with current zone
- [x] Live scrolling 60-second HR chart (Vico) with zone color bands
- [x] Zone strip indicator (Z1–Z5) below chart
- [x] Settings screen — sliders for zone thresholds, persisted to Room
- [x] BLE permission handling (Android 12+ BLUETOOTH_SCAN / BLUETOOTH_CONNECT)

**Known issues to fix at the start of Phase 2**
- Chart y-axis auto-ranges to session data — zone bands look cramped if HR stays in a narrow range. Fix: set a fixed y-axis floor (~50 bpm).
- BPM display uses `fontSize * 2` multiplier — works but fragile. Replace with a proper `sp` value.
- No reconnection logic — if the H10 drops signal mid-session the user must scan again manually.
- `JAVA_HOME` must be set manually in each Git Bash session until the env var is saved permanently.

---

## Phase 2 — Session Recording (next)

- [ ] User profile setup (age, weight, sex) — stored in SharedPreferences
- [ ] Calorie/min calculation — Keytel et al. (2005) formula
- [ ] Session start/stop recording — writes to Room (`sessions` + `hr_samples` tables)
- [ ] Cal/min live graph alongside HR graph
- [ ] Session history list screen
- [ ] CSV export to Downloads folder (MediaStore API)

## Phase 3 — Projection Engine (later)

- [ ] Phase 3a: Polynomial fit to cumulative calorie curve → project to session end time
- [ ] Phase 3b: Blend projection with historical session average (after 10+ sessions)
- [ ] Developer tuning knobs for blend weights (debug builds only)

---

## Project Structure

```
app/src/main/kotlin/com/pulsecoach/
  ble/          PolarBleManager — BLE scan, connect, HR stream
  data/         Room entities, DAOs, PulseCoachDatabase
  model/        Pure data classes (HrReading, ZoneConfig, BleConnectionState)
  repository/   ZoneConfigRepository — bridges Room and ViewModels
  ui/           LiveSessionScreen, SettingsScreen, LiveHrChart
  util/         ZoneCalculator — pure zone/color/name functions
  viewmodel/    LiveSessionViewModel, SettingsViewModel
  MainActivity  NavHost with live_session and settings routes
```

## Architecture Notes

- **MVVM** with Repository layer. One ViewModel per screen.
- **StateFlow** everywhere — no LiveData.
- **BLE rule:** all Polar SDK operations run on background coroutines via `callbackFlow`.
  The RxJava Flowables from the SDK are never exposed outside `PolarBleManager`.
- **Room version:** 1. Increment version + add Migration on any schema change.
- **Vico version:** 2.0.0-beta.3. `ShapeComponent` takes `fill: Fill(argbInt)`, not `color`.
  `HorizontalLayout` does not exist in this version.

---

## AI Collaboration

Built with Claude (Anthropic) via Claude Code.
See `CLAUDE.md` for full project instructions and constraints.
