package io.nightbeam.donutrtp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZoneTriggerModeTest {

    @Test
    void fromStringParsesKnownModesAndFallsBack() {
        assertEquals(ZoneTriggerMode.ENTER, ZoneTriggerMode.fromString("enter", ZoneTriggerMode.BOTH));
        assertEquals(ZoneTriggerMode.INTERACT, ZoneTriggerMode.fromString(" INTERACT ", ZoneTriggerMode.ENTER));
        assertEquals(ZoneTriggerMode.BOTH, ZoneTriggerMode.fromString("BOTH", ZoneTriggerMode.ENTER));
        assertEquals(ZoneTriggerMode.ENTER, ZoneTriggerMode.fromString("nope", ZoneTriggerMode.ENTER));
        assertEquals(ZoneTriggerMode.ENTER, ZoneTriggerMode.fromString(" ", ZoneTriggerMode.ENTER));
        assertEquals(ZoneTriggerMode.ENTER, ZoneTriggerMode.fromString(null, ZoneTriggerMode.ENTER));
    }

    @Test
    void allowsEnterMatchesTriggerModes() {
        assertTrue(ZoneTriggerMode.ENTER.allowsEnter());
        assertTrue(ZoneTriggerMode.BOTH.allowsEnter());
        assertFalse(ZoneTriggerMode.INTERACT.allowsEnter());
    }
}
