package io.nightbeam.donutrtp.rtp;

import java.util.random.RandomGenerator;

/**
 * Picks X/Z offsets for RTP inside a square of the configured radius.
 */
public final class RandomLocationSampler {

    private RandomLocationSampler() {
    }

    /**
     * Inclusive offset in {@code [-radius, radius]}.
     */
    public static int nextOffset(int radius, RandomGenerator random) {
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be >= 1, got " + radius);
        }
        return random.nextInt(-radius, radius + 1);
    }
}
