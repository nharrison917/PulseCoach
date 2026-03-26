package com.pulsecoach.repository

import com.pulsecoach.data.ZoneConfigDao
import com.pulsecoach.data.ZoneConfigEntity
import com.pulsecoach.model.ZoneConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for zone configuration.
 *
 * Sits between the DAO (database layer) and the ViewModels (UI layer).
 * Translates ZoneConfigEntity (database row) ↔ ZoneConfig (domain model)
 * so that ViewModels never reference Room classes directly.
 *
 * This separation matters for testing: you can swap in a fake repository
 * without touching the database.
 */
class ZoneConfigRepository(private val dao: ZoneConfigDao) {

    /**
     * Observe the current zone configuration as a Flow.
     * Falls back to [ZoneConfig.defaults] if no config has been saved yet.
     * The Flow emits a new value automatically whenever the user saves changes.
     */
    val zoneConfig: Flow<ZoneConfig> = dao.getZoneConfig().map { entity ->
        entity?.toZoneConfig() ?: ZoneConfig.defaults
    }

    /** Persists the given configuration to the database. */
    suspend fun saveZoneConfig(config: ZoneConfig) {
        dao.upsert(config.toEntity())
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun ZoneConfigEntity.toZoneConfig() = ZoneConfig(
        zone1MaxBpm = zone1MaxBpm,
        zone2MaxBpm = zone2MaxBpm,
        zone3MaxBpm = zone3MaxBpm,
        zone4MaxBpm = zone4MaxBpm
    )

    private fun ZoneConfig.toEntity() = ZoneConfigEntity(
        zone1MaxBpm = zone1MaxBpm,
        zone2MaxBpm = zone2MaxBpm,
        zone3MaxBpm = zone3MaxBpm,
        zone4MaxBpm = zone4MaxBpm
    )
}
