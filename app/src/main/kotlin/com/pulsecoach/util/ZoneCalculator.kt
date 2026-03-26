package com.pulsecoach.util

import androidx.compose.ui.graphics.Color
import com.pulsecoach.model.ZoneConfig

/**
 * Pure functions for HR zone logic. No state, no side effects — easy to unit test.
 *
 * "object" in Kotlin is a singleton. Think of it like a Python module with only
 * static methods — you call ZoneCalculator.zoneForBpm(...) directly.
 */
object ZoneCalculator {

    /**
     * Returns the HR zone number (1–5) for a given bpm and zone configuration.
     * Returns 0 if bpm is 0 (no reading yet).
     */
    fun zoneForBpm(bpm: Int, config: ZoneConfig = ZoneConfig.defaults): Int = when {
        bpm <= 0              -> 0  // No reading
        bpm <= config.zone1MaxBpm -> 1
        bpm <= config.zone2MaxBpm -> 2
        bpm <= config.zone3MaxBpm -> 3
        bpm <= config.zone4MaxBpm -> 4
        else                  -> 5
    }

    /**
     * Returns the display color for a given zone number.
     * Colors match the palette defined in CLAUDE.md.
     */
    fun colorForZone(zone: Int): Color = when (zone) {
        1    -> Color(0xFF80B4FF) // Blue   — Recovery
        2    -> Color(0xFF80E27E) // Green  — Aerobic
        3    -> Color(0xFFFFD54F) // Yellow — Tempo
        4    -> Color(0xFFFF8A65) // Orange — Threshold
        5    -> Color(0xFFEF5350) // Red    — Max
        else -> Color(0xFF9E9E9E) // Grey   — No data
    }

    /** Returns a dark color that stays readable on top of any zone background. */
    fun textColorForZone(zone: Int): Color = when (zone) {
        1, 2, 3 -> Color(0xFF1A1A1A) // Dark text on light backgrounds
        else    -> Color(0xFFFFFFFF) // White on orange/red
    }

    /** Returns the human-readable zone name. */
    fun nameForZone(zone: Int): String = when (zone) {
        1    -> "Zone 1 — Recovery"
        2    -> "Zone 2 — Aerobic"
        3    -> "Zone 3 — Tempo"
        4    -> "Zone 4 — Threshold"
        5    -> "Zone 5 — Max"
        else -> "—"
    }
}
