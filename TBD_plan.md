# TBD — Parked Features

Read `.ai-codex/lib.md` and `.ai-codex/structure.md` at session start (per CLAUDE.md).

---

## Force-kill startup repair (low priority)

A hard OS kill (OOM, battery pull, crash) fires before `onCleared()` can run, so
`finalizeSession()` never executes. The session row is left with no `endTimeMs`,
zero totals, and zero zone splits — showing as "Incomplete" in history.

The graceful-close case (swipe-away, back navigation) is already handled by
`onCleared()`. This item covers only the hard-kill edge case.

**Proposed:** on app launch, scan Room for sessions where `endTimeMs` is null.
For each dangling session:
- Set `endTimeMs` to the timestamp of the last HR sample for that session
- Replay HR samples through `CalorieCalculator` to recover `totalCalories` and `avgBpm`
- Zone splits cannot be recovered without replaying zone logic — leave as zero or skip

**Scope:** new `repairDanglingSessions()` function in `SessionRepository`, called once
from app startup (e.g. `MainActivity.onCreate` or an `Application` subclass).

---

## Estimated zone autofill for users without HR data

The Karvonen autofill in Settings already works, but it requires both resting HR and
max HR to be set in the user profile. Users who don't know either value have no path
to autofilled zones.

**Proposed:** add an "Estimate zones for me" fallback in `SettingsScreen` that uses:
- **Max HR:** `220 - age` (classic age-predicted formula)
- **Resting HR:** fall back to a population default (70 bpm) if not set in profile

Feed both into the existing `ZoneCalculator.karvonenZones()` — no new zone math needed.

**Scope:** ~2 files.
- `SettingsScreen`: show the estimate button when `karvonenZonesOrNull()` returns null
  (i.e., profile is missing one or both HR values). Button label should communicate
  it's an estimate, e.g. *"Estimate from age (220 − age formula)"*. Populate drafts
  exactly as the existing Karvonen checkbox does, so the user can still tweak before
  saving.
- `SettingsViewModel` (or `ZoneCalculator`): add a `estimatedZones(age)` helper that
  calls `karvonenZones(restingHr = 70, maxHr = 220 - age)`. Keep it clearly labelled
  as an estimate — not a replacement for real values.

**UX note:** if the user later fills in real HR values in their profile, the Karvonen
checkbox path takes over. No special migration needed.

---

## Selectable HR chart time window

Allow the user to choose the HR chart's rolling window (1 / 5 / 10 / 15 min) via a
setting, rather than the hardcoded 5-minute window.

**Scope:** ~5 files, no schema changes. Setting stored in SharedPreferences.
- `SettingsViewModel` + `SettingsScreen`: add a chip/dropdown picker.
- `LiveSessionViewModel`: `MAX_HR_HISTORY` buffer size derived from setting; must be
  observed reactively so a mid-session change takes effect without a restart.
- `LiveHrChart`: `WINDOW_SECONDS` becomes a parameter; `maxX` and x-axis label spacing
  both derive from it (spacing needs a per-window lookup to avoid crowding or gaps).
- `LiveSessionScreen`: threads the window value down to `LiveHrChart`.

**Main fiddly part:** x-axis label spacing — `HorizontalAxis.ItemPlacer.aligned(spacing)`
must be tuned per window size (e.g. 30s / 60s / 120s / 180s for 1/5/10/15 min windows).

---

## Adaptive ensemble weights (Level 3a)

The current 0.6 historical / 0.4 polynomial blend uses fixed weights chosen by
judgment. The Bates & Granger (1969) optimal formula derives weights from relative
forecast error variances: `w_poly = σ_hist² / (σ_poly² + σ_hist²)`. This would
make the blend personalized — users with inconsistent history would lean more on
the polynomial; users with stable history would lean more on the baseline.

`EvaluationViewModel` already computes polynomial-only and blended errors separately,
so the variance estimates are achievable without new infrastructure.

**Proposed:**
- After each session, record polynomial error and historical error separately
  alongside the existing calibration ratio in SharedPreferences
- Accumulate running variances for each; compute weights as the Bates & Granger ratio
- Apply the same N ≥ 10 gate before activating (default to 0.6/0.4 until then)

**Scope:** `ProjectionCalibrator` (store two extra running variances),
`LiveSessionViewModel` (pass derived weights into `HistoricalAverager`),
SharedPreferences keys under `pulse_coach_calibration`.

**Prerequisite:** enough session history to make variance estimates stable. Gate
behind the same N threshold used by blending. See `ANALYSIS.md` Level 3a.

---

## Exponential decay weighting for stale sessions (Level 6)

The bias correction factor and historical baseline both use equal-weight cumulative
means. If training style shifts substantially (e.g. base-building → race-pace work),
old sessions distort the correction factor and historical curve.

**Proposed:** weight sessions by `λ^(n−i)` where i is chronological index and
λ ∈ (0, 1), so recent sessions have more influence. Two application points:

- `ProjectionCalibrator`: replace the cumulative mean update with a
  decay-weighted mean over the stored ratio list
- `HistoricalAverager`: weight each session's per-minute contribution by its
  recency when computing the baseline curve (requires session timestamps from Room,
  which are already stored as `startTimeMs`)

**Blocker:** λ is a new hyperparameter with no obvious default. Needs the evaluation
screen extended to compare decay rates before any value can be justified.

**Scope:** `ProjectionCalibrator`, `HistoricalAverager`, SharedPreferences (store λ
or derive it from a user-facing "memory window" setting in sessions). See
`ANALYSIS.md` Level 6.
