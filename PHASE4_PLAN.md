# Phase 4 Implementation Plan — Session Intent Classification

## What this adds

The user pre-classifies each session by intensity before starting.
Historical projections are filtered to matching sessions only, with a
graceful fallback ladder when a cell has insufficient data.

---

## Design decisions (locked)

| Decision | Choice |
|---|---|
| Intensity labels | Recovery / Steady / Push |
| Duration buckets | 20 / 30 / 45 / 60 min |
| Duration reclassification | Auto at session end based on actual duration |
| Intensity edit | Post-session via dialog on history card |
| Null display | "--" on history cards for unclassified sessions |
| Old data | Destructive migration acceptable — all current data is synthetic/test |
| Room version | v2 → v3 |

### Duration bucket boundaries

| Actual duration | Files as |
|---|---|
| ≤ 25 min | 20 |
| 26 – 37 min | 30 |
| 38 – 52 min | 45 |
| ≥ 53 min | 60 |

### Fallback ladder (projection)

When blending, filter history in this order until a tier has >= 10 sessions:

1. Same intensity + same duration bucket
2. Same intensity, any duration (extrapolate curve to target length)
3. Polynomial only (no historical blend)

Recovery/Steady/Push histories are **never** mixed.

---

## New model

```
model/
  SessionType.kt    — enum: RECOVERY, STEADY, PUSH
```

Stored as a String in Room (Kotlin enum → `name` string). Nullable — null = "--".

---

## Files to create

| File | Purpose |
|---|---|
| `model/SessionType.kt` | Enum with display label and color |

---

## Files to modify

| File | Change |
|---|---|
| `data/SessionEntity.kt` | Add `sessionType: String?` column |
| `data/PulseCoachDatabase.kt` | Bump to version 3; add migration (ALTER TABLE + fallbackToDestructiveMigration as safety net) |
| `data/SessionDao.kt` | Add `getQualifyingSessionsByType(type: String)` query; add `updateSessionType(id, type)` |
| `model/Session.kt` | Add `sessionType: SessionType?` field |
| `repository/SessionRepository.kt` | Add `updateSessionType()`, `getQualifyingSessionsByType()` |
| `util/HistoricalAverager.kt` | Add `buildCurve()` overload that accepts a `sessionType` filter; implement fallback ladder |
| `viewmodel/LiveSessionViewModel.kt` | Add `_sessionType: MutableStateFlow<SessionType?>` state; pass to `startSession()`; auto-reclassify duration bucket at session end |
| `viewmodel/SessionHistoryViewModel.kt` | Add `updateSessionType(sessionId, type)` |
| `viewmodel/EvaluationViewModel.kt` | Pass sessionType filter through to HistoricalAverager |
| `ui/LiveSessionScreen.kt` | Add 20m chip to duration picker; add intensity chip row below |
| `ui/SessionHistoryScreen.kt` | Pass `onUpdateSessionType` callback to SessionCard |
| `ui/SessionHistoryScreen.kt` (SessionCard) | Show intensity label chip; tap → 3-option dialog |

---

## Stage breakdown

### Stage 1 — Data layer (no UI yet)
1. `SessionType.kt` enum
2. `SessionEntity` + `Session` domain model updated
3. Room v3 migration
4. DAO queries: `getQualifyingSessionsByType`, `updateSessionType`
5. Repository wrappers
6. Unit test: migration doesn't lose non-type columns

### Stage 2 — Projection filtering
1. `HistoricalAverager.buildCurve()` accepts optional `SessionType?` filter
2. Fallback ladder logic inside `HistoricalAverager`
3. `LiveSessionViewModel` passes current `sessionType` through to averager
4. Unit tests: fallback ladder covers all three tiers

### Stage 3 — Session start UI
1. Add 20m chip to duration row in `LiveSessionScreen`
2. Add intensity chip row (Recovery / Steady / Push) below duration chips
3. Both rows hidden once recording starts
4. `_sessionType` StateFlow wired to chips

### Stage 4 — Session end reclassification
1. At `stopSession()`, compute actual duration and reclassify into the
   correct bucket (update `targetDurationMinutes` StateFlow before saving)
2. Save `sessionType` to Room alongside session end data

### Stage 5 — History card edit
1. `SessionCard` shows intensity label (or "--") as a small chip
2. Tap → `AlertDialog` with three `RadioButton` options + "Clear" option
3. `SessionHistoryViewModel.updateSessionType()` writes to Room
4. History list refreshes automatically (Room Flow)

---

## HistoricalAverager fallback ladder — pseudocode

```
fun getProjectionCurve(sessionType, durationBucket, targetMinutes, allSessions, allSamples):

  tier1 = sessions where type == sessionType AND durationBucket == durationBucket
  if tier1.size >= BLEND_MIN_SESSIONS:
      return buildCurve(tier1, allSamples, targetMinutes)

  tier2 = sessions where type == sessionType   // any duration
  if tier2.size >= BLEND_MIN_SESSIONS:
      return buildCurve(tier2, allSamples, targetMinutes)

  return null   // polynomial only; no blend
```

---

## What does NOT change

- `BLEND_MIN_SESSIONS` stays at 10 — same threshold, applied per tier
- `MIN_CONTRIBUTING_SESSIONS` (3) stays the same
- Blend weights (0.4 poly / 0.6 historical) unchanged
- EvaluationScreen logic is the same; it just uses typed sessions when available
- SyntheticSessionGenerator is unchanged — seeded sessions remain null type ("--")

---

## Known tradeoffs

| Tradeoff | Accepted? |
|---|---|
| 12 cells fills slowly — many sessions needed before Tier 1 unlocks | Yes — Tier 2 fallback handles early period |
| Synthetic/seeded sessions are untyped — won't contribute to typed projections | Yes — real sessions will replace them over time |
| Losing all current DB data on migration | Yes — all data is synthetic/test |
