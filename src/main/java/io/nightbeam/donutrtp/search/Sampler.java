package io.nightbeam.donutrtp.search;

import io.nightbeam.donutrtp.destination.Destination;
import java.util.random.RandomGenerator;

/**
 * Picks random columns between a minimum and maximum distance from a centre, evenly spread over the area, and inside
 * the world border.
 */
public final class Sampler {
    /** The world border as a square, already shrunk by the configured margin. */
    public record Border(double centerX, double centerZ, double halfSize) {
        public static final Border NONE = new Border(0, 0, 3.0E7);

        public boolean contains(int x, int z) {
            return Math.abs(x + 0.5 - centerX) <= halfSize && Math.abs(z + 0.5 - centerZ) <= halfSize;
        }
    }

    private static final int TRIES = 32;

    private Sampler() {
    }

    /** Returns {x, z}, or null when no point of the area is inside the border. */
    public static int[] pick(Destination.Shape shape, int centerX, int centerZ, int minRadius, int maxRadius,
                             Border border, RandomGenerator random) {
        for (int i = 0; i < TRIES; i++) {
            int[] point = shape == Destination.Shape.CIRCLE
                    ? circle(centerX, centerZ, minRadius, maxRadius, random)
                    : square(centerX, centerZ, minRadius, maxRadius, random);
            if (border.contains(point[0], point[1])) {
                return point;
            }
        }
        // The border cuts off most of the area: pick inside the border instead, as close to the rules as it allows.
        double half = Math.min(border.halfSize(), maxRadius);
        if (half < 1) {
            return null;
        }
        int x = (int) Math.floor(border.centerX() + (random.nextDouble() * 2 - 1) * half);
        int z = (int) Math.floor(border.centerZ() + (random.nextDouble() * 2 - 1) * half);
        return border.contains(x, z) ? new int[]{x, z} : null;
    }

    /** Whether column x/z is between the two distances from the centre, measured the way the shape measures. */
    public static boolean inArea(Destination.Shape shape, int centerX, int centerZ, int minRadius, int maxRadius, int x, int z) {
        double dx = x + 0.5 - centerX;
        double dz = z + 0.5 - centerZ;
        double distance = shape == Destination.Shape.CIRCLE ? Math.hypot(dx, dz) : Math.max(Math.abs(dx), Math.abs(dz));
        return distance >= minRadius && distance <= maxRadius;
    }

    /** Uniform over the ring: the distance is drawn so that outer rings, which are larger, are picked more often. */
    static int[] circle(int centerX, int centerZ, int minRadius, int maxRadius, RandomGenerator random) {
        double r = Math.sqrt(minRadius * (double) minRadius
                + random.nextDouble() * (maxRadius * (double) maxRadius - minRadius * (double) minRadius));
        double angle = random.nextDouble() * Math.PI * 2;
        return new int[]{
                (int) Math.floor(centerX + Math.cos(angle) * r),
                (int) Math.floor(centerZ + Math.sin(angle) * r)};
    }

    /** Uniform over the square ring: pick a square outline by area, then a point on it. */
    static int[] square(int centerX, int centerZ, int minRadius, int maxRadius, RandomGenerator random) {
        double d = Math.sqrt(minRadius * (double) minRadius
                + random.nextDouble() * (maxRadius * (double) maxRadius - minRadius * (double) minRadius));
        double t = random.nextDouble() * 8 * d;
        double x;
        double z;
        if (t < 2 * d) {
            x = -d + t;
            z = -d;
        } else if (t < 4 * d) {
            x = d;
            z = -d + (t - 2 * d);
        } else if (t < 6 * d) {
            x = d - (t - 4 * d);
            z = d;
        } else {
            x = -d;
            z = d - (t - 6 * d);
        }
        return new int[]{(int) Math.floor(centerX + x), (int) Math.floor(centerZ + z)};
    }
}
