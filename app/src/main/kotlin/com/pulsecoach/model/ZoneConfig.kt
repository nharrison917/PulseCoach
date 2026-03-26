package com.pulsecoach.model

/**
 * User-configurable heart rate zone thresholds.
 *
 * Each field is the MAXIMUM bpm for that zone (except zone 5, which is everything above zone 4).
 * Stored in Room (Phase 2). For Phase 1, use [ZoneConfig.defaults].
 *
 * @param zone1MaxBpm  Upper bound of Zone 1 (Recovery). Below this = Zone 1.
 * @param zone2MaxBpm  Upper bound of Zone 2 (Aerobic).
 * @param zone3MaxBpm  Upper bound of Zone 3 (Tempo).
 * @param zone4MaxBpm  Upper bound of Zone 4 (Threshold). Above this = Zone 5 (Max).
 */
data class ZoneConfig(
    val zone1MaxBpm: Int = 114,
    val zone2MaxBpm: Int = 135,
    val zone3MaxBpm: Int = 155,
    val zone4MaxBpm: Int = 175
) {
    companion object {
        /** Default thresholds per CLAUDE.md spec. Used until the user customises them. */
        val defaults = ZoneConfig()
    }
}
