package io.nightbeam.donutrtp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModrinthUpdateCheckerTest {

    @Test
    void compareVersionsOrdersSemver() {
        assertTrue(ModrinthUpdateChecker.compareVersions("1.5.1", "1.5.0") > 0);
        assertTrue(ModrinthUpdateChecker.compareVersions("1.5.0", "1.5.1") < 0);
        assertEquals(0, ModrinthUpdateChecker.compareVersions("1.5.1", "1.5.1"));
    }

    @Test
    void compareVersionsStripsSuffixes() {
        assertEquals(0, ModrinthUpdateChecker.compareVersions("1.5.1+paper-1.21.1", "1.5.1"));
        assertTrue(ModrinthUpdateChecker.compareVersions("1.5.1+paper", "1.5.0") > 0);
        assertEquals(0, ModrinthUpdateChecker.compareVersions("1.5.1-SNAPSHOT", "1.5.1"));
    }

    @Test
    void normalizeVersionRemovesBuildMetadata() {
        assertEquals("1.5.1", ModrinthUpdateChecker.normalizeVersion("1.5.1+paper-1.21.1"));
        assertEquals("1.5.0", ModrinthUpdateChecker.normalizeVersion("1.5.0-beta"));
    }

    @Test
    void pickNewestVersionNumberUsesDatePublished() {
        String json = """
                [
                  {"version_number":"1.4.0","date_published":"2026-08-07T00:00:00Z"},
                  {"version_number":"1.5.1","date_published":"2026-08-21T12:00:00Z"},
                  {"version_number":"1.5.0","date_published":"2026-08-12T00:00:00Z"}
                ]
                """;
        assertEquals("1.5.1", ModrinthUpdateChecker.pickNewestVersionNumber(json));
    }

    @Test
    void pickNewestVersionNumberReturnsNullWhenEmpty() {
        assertNull(ModrinthUpdateChecker.pickNewestVersionNumber("[]"));
        assertNull(ModrinthUpdateChecker.pickNewestVersionNumber(""));
    }
}
