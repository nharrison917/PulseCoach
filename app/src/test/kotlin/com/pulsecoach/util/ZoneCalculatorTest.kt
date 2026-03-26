package com.pulsecoach.util

import com.pulsecoach.model.ZoneConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for ZoneCalculator.
 *
 * Default thresholds per ZoneConfig.defaults:
 *   Z1: <= 114 bpm
 *   Z2: 115–135 bpm
 *   Z3: 136–155 bpm
 *   Z4: 156–175 bpm
 *   Z5: > 175 bpm
 */
class ZoneCalculatorTest {

    private val config = ZoneConfig.defaults  // zone1Max=114, z2=135, z3=155, z4=175

    // ── zoneForBpm: boundary cases ────────────────────────────────────────────

    @Test
    fun `bpm 0 returns zone 0`() {
        assertEquals(0, ZoneCalculator.zoneForBpm(0, config))
    }

    @Test
    fun `negative bpm returns zone 0`() {
        assertEquals(0, ZoneCalculator.zoneForBpm(-1, config))
    }

    @Test
    fun `bpm 1 returns zone 1`() {
        assertEquals(1, ZoneCalculator.zoneForBpm(1, config))
    }

    @Test
    fun `bpm at zone1 max boundary returns zone 1`() {
        assertEquals(1, ZoneCalculator.zoneForBpm(114, config))
    }

    @Test
    fun `bpm just above zone1 max returns zone 2`() {
        assertEquals(2, ZoneCalculator.zoneForBpm(115, config))
    }

    @Test
    fun `bpm at zone2 max boundary returns zone 2`() {
        assertEquals(2, ZoneCalculator.zoneForBpm(135, config))
    }

    @Test
    fun `bpm just above zone2 max returns zone 3`() {
        assertEquals(3, ZoneCalculator.zoneForBpm(136, config))
    }

    @Test
    fun `bpm at zone3 max boundary returns zone 3`() {
        assertEquals(3, ZoneCalculator.zoneForBpm(155, config))
    }

    @Test
    fun `bpm just above zone3 max returns zone 4`() {
        assertEquals(4, ZoneCalculator.zoneForBpm(156, config))
    }

    @Test
    fun `bpm at zone4 max boundary returns zone 4`() {
        assertEquals(4, ZoneCalculator.zoneForBpm(175, config))
    }

    @Test
    fun `bpm just above zone4 max returns zone 5`() {
        assertEquals(5, ZoneCalculator.zoneForBpm(176, config))
    }

    @Test
    fun `very high bpm returns zone 5`() {
        assertEquals(5, ZoneCalculator.zoneForBpm(220, config))
    }

    // ── zoneForBpm: typical mid-zone values ──────────────────────────────────

    @Test
    fun `midpoint of each zone resolves correctly`() {
        assertEquals(1, ZoneCalculator.zoneForBpm(90,  config))  // deep Z1
        assertEquals(2, ZoneCalculator.zoneForBpm(125, config))  // mid Z2
        assertEquals(3, ZoneCalculator.zoneForBpm(145, config))  // mid Z3
        assertEquals(4, ZoneCalculator.zoneForBpm(165, config))  // mid Z4
        assertEquals(5, ZoneCalculator.zoneForBpm(190, config))  // deep Z5
    }

    // ── Custom ZoneConfig ─────────────────────────────────────────────────────

    @Test
    fun `custom zone config is respected`() {
        val custom = ZoneConfig(zone1MaxBpm = 100, zone2MaxBpm = 130,
                                zone3MaxBpm = 160, zone4MaxBpm = 180)
        assertEquals(1, ZoneCalculator.zoneForBpm(100, custom))
        assertEquals(2, ZoneCalculator.zoneForBpm(101, custom))
        assertEquals(5, ZoneCalculator.zoneForBpm(181, custom))
    }

    // ── colorForZone ──────────────────────────────────────────────────────────

    @Test
    fun `colorForZone returns a non-transparent color for all valid zones`() {
        for (zone in 1..5) {
            val color = ZoneCalculator.colorForZone(zone)
            // Alpha == 1f means the color is fully opaque (not accidental transparent)
            assertEquals("zone $zone color should be fully opaque", 1f, color.alpha)
        }
    }

    @Test
    fun `colorForZone returns grey for zone 0`() {
        // Zone 0 = no data — should be a neutral grey, not one of the zone colors
        val noDataColor = ZoneCalculator.colorForZone(0)
        val z1Color     = ZoneCalculator.colorForZone(1)
        assertFalse("zone 0 color should differ from zone 1", noDataColor == z1Color)
    }

    // ── nameForZone ───────────────────────────────────────────────────────────

    @Test
    fun `nameForZone returns non-empty string for all valid zones`() {
        for (zone in 1..5) {
            val name = ZoneCalculator.nameForZone(zone)
            assertFalse("zone $zone name should not be empty", name.isBlank())
        }
    }

    @Test
    fun `nameForZone returns fallback dash for zone 0`() {
        assertEquals("—", ZoneCalculator.nameForZone(0))
    }

    @Test
    fun `nameForZone returns fallback dash for out-of-range zone`() {
        assertEquals("—", ZoneCalculator.nameForZone(6))
        assertEquals("—", ZoneCalculator.nameForZone(-1))
    }
}
