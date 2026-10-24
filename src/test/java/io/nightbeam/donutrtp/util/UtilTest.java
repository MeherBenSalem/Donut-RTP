package io.nightbeam.donutrtp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nightbeam.donutrtp.config.Messages;
import io.nightbeam.donutrtp.config.SoundSpec;
import org.junit.jupiter.api.Test;

class UtilTest {
    @Test
    void durations() {
        assertEquals("0s", Durations.format(0));
        assertEquals("45s", Durations.format(45));
        assertEquals("4m 30s", Durations.format(270));
        assertEquals("5m", Durations.format(300));
        assertEquals("1h 5m", Durations.format(3900));
        assertEquals("2h", Durations.format(7200));
        assertEquals(1, Durations.ceilSeconds(1));
        assertEquals(1, Durations.ceilSeconds(1000));
        assertEquals(2, Durations.ceilSeconds(1001));
        assertEquals(0, Durations.ceilSeconds(-5));
    }

    @Test
    void placeholdersInBothStyles() {
        assertEquals("Wait 4m now 4m", Messages.format("Wait {time} now %time%", "time", "4m"));
        assertEquals("a 1 b 2", Messages.format("a {x} b {y}", "x", 1, "y", 2));
        assertEquals("", Messages.format(null, "x", 1));
    }

    @Test
    void soundNamesBecomeKeys() {
        assertEquals("minecraft:entity.enderman.teleport", SoundSpec.toKey("ENTITY_ENDERMAN_TELEPORT"));
        assertEquals("minecraft:block.note_block.hat", SoundSpec.toKey("BLOCK_NOTE_BLOCK_HAT"));
        assertEquals("minecraft:entity.experience_orb.pickup", SoundSpec.toKey("entity.experience_orb.pickup"));
        assertEquals("custom:my.sound", SoundSpec.toKey("custom:my.sound"));
        assertEquals("", SoundSpec.toKey(" "));
        assertTrue(SoundSpec.of("", 1, 1) == SoundSpec.NONE);
        assertEquals(2.0f, SoundSpec.of("UI_BUTTON_CLICK", 1, 5).pitch());
    }

    @Test
    void versions() {
        assertTrue(UpdateChecker.compare("2.0.10", "2.0.9") > 0);
        assertTrue(UpdateChecker.compare("2.0.0", "1.5.2") > 0);
        assertEquals(0, UpdateChecker.compare("v2.0.0", "2.0.0-beta"));
        assertTrue(UpdateChecker.compare("1.5.2", "2.0") < 0);
        assertEquals("2.1.0", UpdateChecker.highestVersion(
                "[{\"version_number\":\"2.0.0\"},{\"version_number\":\"2.1.0\"},{\"version_number\":\"1.5.2\"}]"));
    }
}
