package io.nightbeam.donutrtp.zone;

/** Where a zone is: a box of blocks, or a WorldGuard region. */
public sealed interface ZoneArea permits ZoneArea.Cuboid, ZoneArea.Region {
    String world();

    /** Every block from min to max, both included. */
    record Cuboid(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) implements ZoneArea {
        public static Cuboid of(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
            return new Cuboid(world, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                    Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
        }

        /**
         * Whether a player's feet at x/y/z are inside. The top gets two extra blocks so jumping in place never counts
         * as leaving.
         */
        public boolean contains(String worldName, double x, double y, double z) {
            return world.equals(worldName)
                    && x >= minX && x < maxX + 1
                    && z >= minZ && z < maxZ + 1
                    && y >= minY && y < maxY + 2;
        }

        public String describe() {
            return minX + ", " + minY + ", " + minZ + " to " + maxX + ", " + maxY + ", " + maxZ;
        }
    }

    record Region(String world, String region) implements ZoneArea {
    }
}
