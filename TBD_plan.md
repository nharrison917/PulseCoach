# Implementation Plan — Next Feature Wave

---

## Cold-start notes for incoming agent

Read `.ai-codex/lib.md` and `.ai-codex/structure.md` before exploring the codebase (per CLAUDE.md instructions).

**Critical override — CLAUDE.md Vico gotcha is being intentionally retired by Branch 1:**

CLAUDE.md currently contains this rule:
> *LiveCalorieChart uses 4 series (actual / projected / upper band / lower band). All 4 must always be present in the model — use 2-point dummies at the last actual position when a series has no real data.*

Branch 1 Feature A drops the chart to **2 series** (actual + projected only). The band lines are being removed entirely in favour of a caption-level endpoint annotation. This rule should be **deleted from CLAUDE.md** as part of Feature A. Do not attempt to maintain 4 series — that is the change being made.

**Dead code to remove in LiveCalorieChart.kt during Feature A:**

Two items were added in the previous session and will become unused once the band lines are removed. Remove both:
- `import androidx.compose.ui.graphics.Color` — added for the band color fix, no longer needed once band lines are gone. Verify no other usage in the file before removing.
- `val bandColor = Color.White.copy(alpha = 0.45f)` — the band color variable, now dead code.

**Branch 1 is the immediate next task.** The plan below is fully detailed and verified against current code. No architectural unknowns remain for Branch 1. Branch 2 has one design decision pre-resolved (RecordingStateHolder singleton). Branch 3 is standalone.

---

## Branches and order of execution

| Branch | Features | Status |
|---|---|---|
| `feature/live-session-improvements` | Calorie graph Option C, First projection callout, Eval caption fix, Stop connecting button | **MERGED** |
| `feature/profile-settings-improvements` | Weight unit toggle (lbs/kg), Lock fields during recording | **NEXT** |
| `feature/expandable-history-cards` | Expandable history cards | Pending |

Execute in order: Branch 2 → Branch 3.
Zone 3 contrast and selectable HR window remain parked — no new branches.

---

## Branch 1 — `feature/live-session-improvements`

### Feature A — Calorie graph Option C (confidence band → endpoint annotation)

**What changes:**
Drop the 4-series chart model to 2 series (actual + projected). The band is no longer drawn as chart lines. Instead, when `projectionBand` is active, compute the calorie range at the target endpoint and surface it in the caption.

**Files:**
- `ui/LiveCalorieChart.kt`
  - Remove `upperBandLine` and `lowerBandLine` — delete their `rememberLine` calls
  - Change `LineCartesianLayer` from `LineProvider.series(actual, projected, upper, lower)` to `LineProvider.series(actual, projected)`
  - Drop the 2-point dummy series logic for band series (no longer needed)
  - Keep `projectionBand: Float?` parameter — still needed for caption math
  - Caption update (when band active + `projectedFinalCalories` not null):
    - Compute `upperCal = projectedFinalCalories * (1 + projectionBand)`
    - Compute `lowerCal = projectedFinalCalories * (1 - projectionBand)`
    - Append to caption: `~${lowerCal.toInt()}–${upperCal.toInt()} cal at ${targetDurationMinutes}m`
    - Full example: `Historical blend from min 11  •  ~660–730 cal at 30m`
  - Update the 4-series model note in the KDoc to 2-series
  - Update CLAUDE.md gotcha section: remove the "4 series always present" rule, note it is now 2 series

- `ui/LiveSessionScreen.kt` — no change expected (projectionBand already threaded through)

**Verify before coding:** Confirm `projectedFinalCalories` is already passed into `LiveCalorieChart` (it is, per existing signature). Confirm the caption block location (`startMinute`/`mode` lines around line 170).

---

### Feature B — First projection callout (min-10 projection stored and displayed)

**What it shows:** A third caption line when the session is over — "Projected at min 10: ~X cal" — showing what the very first projection said, for retrospective ratio tracking.

**Files:**
- `viewmodel/LiveSessionViewModel.kt`
  - Add `private val _firstProjectedCalories = MutableStateFlow<Float?>(null)`
  - Add `val firstProjectedCalories: StateFlow<Float?> = _firstProjectedCalories.asStateFlow()`
  - In the projection block (where `_projectedCalorieCurve` is first set after 10 min of data), add a one-time capture:
    ```kotlin
    if (_firstProjectedCalories.value == null) {
        _firstProjectedCalories.value = ProjectionCalibrator.interpolateProjection(
            rawProjection, _targetDurationMinutes.value.toFloat()
        )
    }
    ```
  - Reset to `null` in `startRecording()` alongside other state resets

- `ui/LiveSessionScreen.kt`
  - Collect `firstProjectedCalories` from ViewModel
  - Pass as new param to `LiveCalorieChart`

- `ui/LiveCalorieChart.kt`
  - Add `firstProjectedCalories: Float? = null` parameter
  - In the session-over caption block (where `caloriesAtTarget` is shown), add line 3:
    `"Projected at min 10: ~${firstProjectedCalories.toInt()} cal"` — only when non-null

**Verify before coding:** Find the exact projection-first-fire location in LiveSessionViewModel (grep `_projectedCalorieCurve.value =` — around line 574). Confirm `interpolateProjection` signature matches intended call.

---

### Feature C — Evaluation panel 1 caption fix

**One-line change.** The caption says "Polynomial projection from min 32" where 32 is `actualPoints.last().first` (session duration, not observation window start). Misleading phrasing.

**Files:**
- `ui/LiveCalorieChart.kt` — lines ~176–178
  - Change: `"$mode from min $startMinute$projectedLabel"`
  - To: `"$mode  •  ${startMinute}-min session$projectedLabel"`
  - Same fix on the band-active variant (~line 173) where applicable

**Note:** `startMinute` variable name stays accurate after this — it IS the session length in minutes. Only the display string changes.

---

### Feature D — "Stop Connecting" button

**Context:** `BleConnectionState.Connecting` is emitted by PolarBleManager when a connection attempt is in progress. The `ConnectingContent` composable (LiveSessionScreen ~line 326) currently just shows a spinner and text — no escape. Cancelling is achieved by calling `disconnectFromDevice()` on the BleManager, which the existing `disconnect()` VM function already wraps.

**Files:**
- `viewmodel/LiveSessionViewModel.kt`
  - Add `fun stopConnecting()`:
    ```kotlin
    fun stopConnecting() {
        val deviceId = (connectionState.value as? BleConnectionState.Connecting)?.deviceId
            ?: return
        bleManager.disconnectFromDevice(deviceId)
    }
    ```
  - This reuses the existing BLE disconnect path — no new BLE logic needed.

- `ui/LiveSessionScreen.kt`
  - `ConnectingContent` (~line 326): add `onStopConnecting: () -> Unit` parameter
  - Add a `TextButton("Stop connecting")` below the spinner, calling `onStopConnecting`
  - At the call site (~line 168): pass `onStopConnecting = { viewModel.stopConnecting() }`

**Verify before coding:** Confirm `disconnectFromDevice()` in PolarBleManager sets state back to `Disconnected` (it does — line 168 in PolarBleManager.kt). Confirm `ConnectingContent` call site receives the ViewModel reference.

---

## Branch 2 — `feature/profile-settings-improvements`

### Feature A — Weight unit toggle (lbs / kg)

**Design decision:** Always store weight in kg internally (CalorieCalculator never changes). Display and accept input in the user's chosen unit. Convert at the UI boundary only.

**Files:**
- `model/UserProfile.kt`
  - Add `val useLbs: Boolean = false` field (default false = kg)
  - `weightKg` field name stays — it is always kg regardless of display unit

- `repository/UserProfileRepository.kt`
  - Add `KEY_USE_LBS = "use_lbs"` constant
  - Persist and load `useLbs` via `getBoolean` / `putBoolean`

- `viewmodel/ProfileViewModel.kt`
  - Add `_useLbs: MutableStateFlow<Boolean>` initialized from saved profile
  - Add `fun setUseLbs(value: Boolean)` — updates state
  - Weight input state: store as display string; on save, convert to kg if `useLbs` is true (`displayValue / 2.20462f`)
  - On load: convert stored kg to lbs for display if `useLbs` is true

- `ui/ProfileSetupScreen.kt`
  - Collect `useLbs` from ViewModel
  - Add `Row` with two `RadioButton`s: "kg" and "lbs", above the weight `TextField`
  - Weight `TextField` label changes: "Weight (kg)" vs "Weight (lbs)" based on selection
  - Tapping a radio button calls `viewModel.setUseLbs(...)` and re-formats the displayed weight value

**Verify before coding:** Grep `ProfileViewModel` for existing weight field name and how it is currently exposed as state. Confirm `CalorieCalculator` receives `weightKg` directly (no change needed there).

---

### Feature B — Lock settings/profile fields during active recording

**Architectural approach:** Introduce a minimal `RecordingStateHolder` Kotlin object — a module-level singleton with a single `MutableStateFlow<Boolean>`. `LiveSessionViewModel` writes to it on start/stop. `SettingsViewModel` and `ProfileViewModel` read from it. No new dependencies, no DI changes. Appropriate for an offline solo app.

**Files:**
- `util/RecordingStateHolder.kt` *(new file)*
  ```kotlin
  object RecordingStateHolder {
      val isRecording: MutableStateFlow<Boolean> = MutableStateFlow(false)
  }
  ```

- `viewmodel/LiveSessionViewModel.kt`
  - In `startRecording()`: add `RecordingStateHolder.isRecording.value = true`
  - In `stopRecording()`: add `RecordingStateHolder.isRecording.value = false`
  - In `onCleared()`: add `RecordingStateHolder.isRecording.value = false` (safety reset)

- `viewmodel/SettingsViewModel.kt`
  - Add `val isRecording: StateFlow<Boolean> = RecordingStateHolder.isRecording.asStateFlow()`

- `viewmodel/ProfileViewModel.kt`
  - Add `val isRecording: StateFlow<Boolean> = RecordingStateHolder.isRecording.asStateFlow()`

- `ui/SettingsScreen.kt`
  - Collect `isRecording` from ViewModel
  - When true: show a `Banner` or `Text` note at top of screen ("A session is in progress — settings locked")
  - Disable zone threshold sliders and Save button (`enabled = !isRecording`)

- `ui/ProfileSetupScreen.kt`
  - Same pattern: collect `isRecording`, disable all TextFields and Save button when true, show note

**Verify before coding:** Grep `SettingsScreen` for Save button and slider components to confirm the `enabled` parameter is available on those composables (it is on `Slider` and `Button`). Confirm `ProfileSetupScreen`'s Save button uses a standard `Button`.

---

## Branch 3 — `feature/expandable-history-cards`

### Feature — Expandable session history cards

**Design:** Collapsed state shows the essential summary row. Expanded state reveals full detail. Expand/collapse is local composable state (no ViewModel involvement — purely UI).

**Collapsed card shows:**
- Date / time
- Duration
- Total calories
- Session type chip (colored)

**Expanded card additionally shows:**
- Avg BPM / max BPM
- Avg cal/min
- Zone time bar (already built — move here from collapsed)
- Export CSV button (currently wrapping — move here)
- Session notes (if non-null and not "synthetic"/"synthetic-r")

**Files:**
- `ui/SessionHistoryScreen.kt`
  - Add `var expanded by remember { mutableStateOf(false) }` inside the card composable
  - Wrap card content in `AnimatedVisibility` or a simple `if (expanded)` block for the detail section
  - Tap target: the entire collapsed row (use `Modifier.clickable { expanded = !expanded }`)
  - Collapsed row: date, duration, calories, type chip — all on one line
  - Expanded section: BPM stats, cal/min, zone bar, export button, notes
  - Add a small expand/collapse indicator (e.g. a `▾` / `▴` icon) on the right edge of the collapsed row
  - Multi-select mode: tapping a card in multi-select toggles selection rather than expanding — existing `selectedIds` logic takes priority; `expanded` click handler only fires when `selectedIds.isEmpty()`

- `viewmodel/SessionHistoryViewModel.kt` — no changes expected

**Verify before coding:** Read the existing card composable in `SessionHistoryScreen.kt` to map what is currently in the collapsed row and what needs to move. Confirm `AnimatedVisibility` is already imported or add it (it's in `androidx.compose.animation`).

---

## Items remaining parked (no branch yet)

- **Zone 3 contrast** — user to confirm visually against Dark/Synthwave themes first
- **Selectable HR chart time window** — complex (5 files), lower priority
- **Evaluation panel 1 caption fix** — bundled into Branch 1 Feature C above (resolved)
