package io.nightbeam.donutrtp.rtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RandomLocationSamplerTest {

    @Test
    void nextOffsetStaysInsideInclusiveRadius() {
        Random random = new Random(42L);
        int radius = 5000;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < 50_000; i++) {
            int offset = RandomLocationSampler.nextOffset(radius, random);
            assertTrue(offset >= -radius && offset <= radius, "offset " + offset);
            min = Math.min(min, offset);
            max = Math.max(max, offset);
        }
        assertTrue(min < 0, "should sample negative offsets");
        assertTrue(max > 0, "should sample positive offsets");
    }

    @Test
    void nextOffsetCoversFullRangeForSmallRadius() {
        Random random = new Random(7L);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(RandomLocationSampler.nextOffset(1, random));
        }
        assertEquals(Set.of(-1, 0, 1), seen);
    }

    @Test
    void nextOffsetRejectsNonPositiveRadius() {
        Random random = new Random(1L);
        assertThrows(IllegalArgumentException.class, () -> RandomLocationSampler.nextOffset(0, random));
        assertThrows(IllegalArgumentException.class, () -> RandomLocationSampler.nextOffset(-4, random));
    }
}
