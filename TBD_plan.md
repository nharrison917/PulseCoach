# TBD — Parked Features

Read `.ai-codex/lib.md` and `.ai-codex/structure.md` at session start (per CLAUDE.md).

---

## Zone 3 color contrast against card backgrounds — needs visual confirmation

Zone 3 yellow (#FFD54F) may have insufficient contrast against card backgrounds in the
Dark and Synthwave themes. Needs visual review before any code changes.

**Action:** User to check against Dark and Synthwave themes first, then revisit.

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
