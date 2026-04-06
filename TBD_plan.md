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
