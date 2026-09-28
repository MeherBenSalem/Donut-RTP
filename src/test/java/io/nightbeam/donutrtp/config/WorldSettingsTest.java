package io.nightbeam.donutrtp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorldSettingsTest {

    @Test
    void radiusIsClampedToAtLeastOne() {
        assertEquals(1, new WorldSettings("world", 0, 60).radius());
        assertEquals(1, new WorldSettings("world", -50, 60).radius());
        assertEquals(5000, new WorldSettings("world", 5000, 60).radius());
    }
}
