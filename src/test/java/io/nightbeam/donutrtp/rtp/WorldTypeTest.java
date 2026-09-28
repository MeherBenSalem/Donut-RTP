package io.nightbeam.donutrtp.rtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldTypeTest {

    @Test
    void fromStringAcceptsTrimmedNames() {
        assertEquals(WorldType.OVERWORLD, WorldType.fromString(" overworld ").orElseThrow());
        assertEquals(WorldType.NETHER, WorldType.fromString("NETHER").orElseThrow());
        assertEquals(WorldType.END, WorldType.fromString("end").orElseThrow());
        assertTrue(WorldType.fromString("aether").isEmpty());
        assertTrue(WorldType.fromString(" ").isEmpty());
        assertTrue(WorldType.fromString(null).isEmpty());
    }
}
