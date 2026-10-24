package io.nightbeam.donutrtp.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nightbeam.donutrtp.destination.Destination;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class SamplerTest {
    private final SplittableRandom random = new SplittableRandom(42);

    @Test
    void circleStaysInTheRing() {
        for (int i = 0; i < 20_000; i++) {
            int[] p = Sampler.pick(Destination.Shape.CIRCLE, 100, -50, 300, 1000, Sampler.Border.NONE, random);
            double distance = Math.hypot(p[0] + 0.5 - 100, p[1] + 0.5 + 50);
            assertTrue(distance >= 298 && distance <= 1002, "distance " + distance);
        }
    }

    @Test
    void squareStaysInTheRing() {
        for (int i = 0; i < 20_000; i++) {
            int[] p = Sampler.pick(Destination.Shape.SQUARE, 0, 0, 300, 1000, Sampler.Border.NONE, random);
            int chebyshev = Math.max(Math.abs(p[0]), Math.abs(p[1]));
            assertTrue(chebyshev >= 299 && chebyshev <= 1000, "distance " + chebyshev);
        }
    }

    @Test
    void squareIsEvenlySpreadOverItsArea() {
        // With min 0, a quarter of the points should fall within half the radius (area ratio 1/4).
        int inner = 0;
        int total = 40_000;
        for (int i = 0; i < total; i++) {
            int[] p = Sampler.pick(Destination.Shape.SQUARE, 0, 0, 0, 1000, Sampler.Border.NONE, random);
            if (Math.max(Math.abs(p[0]), Math.abs(p[1])) < 500) {
                inner++;
            }
        }
        assertEquals(0.25, inner / (double) total, 0.02);
    }

    @Test
    void circleIsEvenlySpreadOverItsArea() {
        int inner = 0;
        int total = 40_000;
        for (int i = 0; i < total; i++) {
            int[] p = Sampler.pick(Destination.Shape.CIRCLE, 0, 0, 0, 1000, Sampler.Border.NONE, random);
            if (Math.hypot(p[0], p[1]) < 500) {
                inner++;
            }
        }
        assertEquals(0.25, inner / (double) total, 0.02);
    }

    @Test
    void areaCheckMatchesTheShape() {
        assertTrue(Sampler.inArea(Destination.Shape.SQUARE, 0, 0, 100, 200, 150, -199));
        assertTrue(!Sampler.inArea(Destination.Shape.SQUARE, 0, 0, 100, 200, 50, 50));
        assertTrue(!Sampler.inArea(Destination.Shape.SQUARE, 0, 0, 100, 200, 201, 0));
        // Inside the square corner, outside the circle.
        assertTrue(!Sampler.inArea(Destination.Shape.CIRCLE, 0, 0, 100, 200, 180, 180));
        assertTrue(Sampler.inArea(Destination.Shape.CIRCLE, 1000, 1000, 100, 200, 1120, 1090));
    }

    @Test
    void respectsTheWorldBorder() {
        Sampler.Border border = new Sampler.Border(0, 0, 400);
        for (int i = 0; i < 5_000; i++) {
            int[] p = Sampler.pick(Destination.Shape.SQUARE, 0, 0, 100, 5000, border, random);
            assertNotNull(p);
            assertTrue(border.contains(p[0], p[1]), p[0] + ", " + p[1]);
        }
    }

    @Test
    void fallsBackInsideASmallBorder() {
        // The whole ring is outside the border: still pick somewhere inside it.
        Sampler.Border border = new Sampler.Border(0, 0, 50);
        int[] p = Sampler.pick(Destination.Shape.CIRCLE, 0, 0, 1000, 3000, border, random);
        assertNotNull(p);
        assertTrue(border.contains(p[0], p[1]));
    }
}
