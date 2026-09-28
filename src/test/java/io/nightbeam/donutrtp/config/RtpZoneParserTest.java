package io.nightbeam.donutrtp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nightbeam.donutrtp.rtp.WorldType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RtpZoneParserTest {

    @Test
    void parseAcceptsDefaultCuboidZone() {
        List<String> warnings = new ArrayList<>();
        Optional<RtpZoneSettings> parsed = RtpZoneParser.parse(
                "spawn_pad",
                true,
                "world",
                "OVERWORLD",
                0.5D,
                64.0D,
                0.5D,
                3.0D,
                2.0D,
                3.0D,
                10,
                " donutrtp.zone.spawn_pad ",
                warnings::add
        );

        assertTrue(parsed.isPresent());
        assertTrue(warnings.isEmpty());
        RtpZoneSettings zone = parsed.get();
        assertEquals("spawn_pad", zone.id());
        assertEquals("world", zone.worldName());
        assertEquals(WorldType.OVERWORLD, zone.worldType());
        assertEquals(3.0D, zone.halfSizeX());
        assertEquals(10, zone.countdownSeconds());
        assertEquals("donutrtp.zone.spawn_pad", zone.permission());
        assertTrue(zone.hasPermission());
    }

    @Test
    void parseSkipsMissingWorldAndInvalidWorldType() {
        List<String> warnings = new ArrayList<>();
        assertTrue(RtpZoneParser.parse(
                "no_world", true, "  ", "OVERWORLD", 0, 64, 0, 1, 1, 1, 5, null, warnings::add
        ).isEmpty());
        assertTrue(RtpZoneParser.parse(
                "bad_type", true, "world", "AETHER", 0, 64, 0, 1, 1, 1, 5, null, warnings::add
        ).isEmpty());
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("missing world"));
        assertTrue(warnings.get(1).contains("Invalid world-type"));
    }

    @Test
    void parseClampsHalfSizeAndCountdownAndBlankPermission() {
        RtpZoneSettings zone = RtpZoneParser.parse(
                "tiny",
                false,
                " world_the_end ",
                "end",
                10,
                20,
                30,
                0.1D,
                0.0D,
                -2.0D,
                0,
                "   ",
                warning -> {
                    throw new AssertionError("unexpected warning: " + warning);
                }
        ).orElseThrow();

        assertFalse(zone.enabled());
        assertEquals("world_the_end", zone.worldName());
        assertEquals(WorldType.END, zone.worldType());
        assertEquals(RtpZoneParser.MIN_HALF_SIZE, zone.halfSizeX());
        assertEquals(RtpZoneParser.MIN_HALF_SIZE, zone.halfSizeY());
        assertEquals(RtpZoneParser.MIN_HALF_SIZE, zone.halfSizeZ());
        assertEquals(RtpZoneParser.MIN_COUNTDOWN_SECONDS, zone.countdownSeconds());
        assertNull(zone.permission());
        assertFalse(zone.hasPermission());
    }
}
