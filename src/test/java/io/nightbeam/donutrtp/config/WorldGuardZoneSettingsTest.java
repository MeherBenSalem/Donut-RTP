package io.nightbeam.donutrtp.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nightbeam.donutrtp.rtp.WorldType;
import org.junit.jupiter.api.Test;

class WorldGuardZoneSettingsTest {

    @Test
    void regionConfiguredRequiresWorldAndRegion() {
        WorldGuardZoneSettings emptyRegion = settings("", "world");
        WorldGuardZoneSettings emptyWorld = settings("spawn", " ");
        WorldGuardZoneSettings configured = settings("spawn", "world");

        assertFalse(emptyRegion.hasRegionConfigured());
        assertFalse(emptyRegion.isWorldGuardFeatureActive());
        assertFalse(emptyWorld.hasRegionConfigured());
        assertTrue(configured.hasRegionConfigured());
        assertTrue(configured.isWorldGuardFeatureActive());
    }

    private static WorldGuardZoneSettings settings(String region, String world) {
        return new WorldGuardZoneSettings(
                true,
                true,
                region,
                world,
                ZoneTriggerMode.ENTER,
                true,
                300,
                "wait",
                true,
                "donutrtp.cooldown.bypass",
                true,
                5,
                false,
                WorldType.OVERWORLD,
                "",
                "",
                "",
                "",
                ""
        );
    }
}
