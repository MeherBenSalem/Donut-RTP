package io.nightbeam.donutrtp.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nightbeam.donutrtp.rtp.WorldType;
import org.junit.jupiter.api.Test;

class RtpZoneSettingsTest {

    private static RtpZoneSettings spawnPad() {
        return new RtpZoneSettings(
                "spawn_pad",
                true,
                "world",
                0.5D,
                64.0D,
                0.5D,
                3.0D,
                2.0D,
                3.0D,
                10,
                WorldType.OVERWORLD,
                null
        );
    }

    @Test
    void containsIncludesCenterAndInclusiveEdges() {
        RtpZoneSettings zone = spawnPad();
        assertTrue(zone.contains("world", 0.5D, 64.0D, 0.5D));
        assertTrue(zone.contains("world", 0.5D + 3.0D, 64.0D, 0.5D));
        assertTrue(zone.contains("world", 0.5D, 64.0D + 2.0D, 0.5D));
        assertTrue(zone.contains("world", 0.5D, 64.0D, 0.5D - 3.0D));
    }

    @Test
    void containsRejectsOutsideBoundsAndOtherWorlds() {
        RtpZoneSettings zone = spawnPad();
        assertFalse(zone.contains("world", 0.5D + 3.01D, 64.0D, 0.5D));
        assertFalse(zone.contains("world", 0.5D, 64.0D + 2.01D, 0.5D));
        assertFalse(zone.contains("world", 0.5D, 64.0D, 0.5D - 3.01D));
        assertFalse(zone.contains("world_nether", 0.5D, 64.0D, 0.5D));
        assertFalse(zone.contains(null, 0.5D, 64.0D, 0.5D));
    }

    @Test
    void jumpingStaysInsideWhenHorizontalPositionUnchanged() {
        RtpZoneSettings zone = spawnPad();
        assertTrue(zone.contains("world", 0.5D, 64.0D, 0.5D));
        assertTrue(zone.contains("world", 0.5D, 65.4D, 0.5D));
        assertFalse(zone.contains("world", 0.5D, 67.0D, 0.5D));
    }
}
