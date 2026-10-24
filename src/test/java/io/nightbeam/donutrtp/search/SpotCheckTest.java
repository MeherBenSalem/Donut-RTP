package io.nightbeam.donutrtp.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nightbeam.donutrtp.destination.Destination;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

class SpotCheckTest {
    private static final Destination ANYWHERE = new Destination("test", "world", Destination.Shape.SQUARE, false, 0, 0,
            0, 1000, -64, 320, Set.of(), Set.of(), "", 0, 0, null);

    /** A world of air with the given blocks. Reading a block outside chunk 0,0 fails the test. */
    private static final class FakeWorld {
        final Map<String, Material> blocks = new HashMap<>();
        final World.Environment environment;

        FakeWorld(World.Environment environment) {
            this.environment = environment;
        }

        FakeWorld set(int x, int y, int z, Material type) {
            blocks.put(x + "," + y + "," + z, type);
            return this;
        }

        FakeWorld fill(int x, int fromY, int toY, int z, Material type) {
            for (int y = fromY; y <= toY; y++) {
                set(x, y, z, type);
            }
            return this;
        }

        Material type(int x, int y, int z) {
            if (x >> 4 != 0 || z >> 4 != 0) {
                throw new AssertionError("read a block outside the spot's chunk: " + x + " " + y + " " + z);
            }
            return blocks.getOrDefault(x + "," + y + "," + z, Material.AIR);
        }

        World world() {
            return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (proxy, method, args) ->
                    switch (method.getName()) {
                        case "getMinHeight" -> -64;
                        case "getMaxHeight" -> 320;
                        case "getEnvironment" -> environment;
                        case "getHighestBlockYAt" -> highest((int) args[0], (int) args[1]);
                        case "getBlockAt" -> block((int) args[0], (int) args[1], (int) args[2]);
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private int highest(int x, int z) {
            for (int y = 319; y > -64; y--) {
                if (type(x, y, z) != Material.AIR) {
                    return y;
                }
            }
            return -64;
        }

        private Block block(int x, int y, int z) {
            Material type = type(x, y, z);
            BlockData data = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[]{BlockData.class},
                    (proxy, method, args) -> {
                        throw new UnsupportedOperationException(method.getName());
                    });
            return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class}, (proxy, method, args) ->
                    switch (method.getName()) {
                        case "getType" -> type;
                        case "isPassable" -> !type.isSolid();
                        case "isLiquid" -> type == Material.WATER || type == Material.LAVA;
                        case "getBlockData" -> data;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static FakeWorld ground(World.Environment environment) {
        FakeWorld world = new FakeWorld(environment);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                world.set(x, 64, z, Material.STONE);
            }
        }
        return world;
    }

    @Test
    void standsOnTheGround() {
        Location spot = SpotCheck.find(ground(World.Environment.NORMAL).world(), 5, 5, ANYWHERE);
        assertNotNull(spot);
        assertEquals(65, spot.getBlockY());
    }

    @Test
    void neverLandsBesideLava() {
        // A lava source beside the feet would pour onto the player as soon as the chunk ticks.
        assertNull(SpotCheck.find(ground(World.Environment.NORMAL).set(6, 65, 5, Material.LAVA).world(), 5, 5, ANYWHERE));
        assertNull(SpotCheck.find(ground(World.Environment.NORMAL).set(4, 67, 6, Material.LAVA).world(), 5, 5, ANYWHERE));
        // Lava two blocks away is left alone.
        assertNotNull(SpotCheck.find(ground(World.Environment.NORMAL).set(7, 65, 5, Material.LAVA).world(), 5, 5, ANYWHERE));
    }

    @Test
    void neverLandsUnderOpenLava() {
        FakeWorld nether = ground(World.Environment.NETHER).fill(5, 90, 127, 5, Material.NETHERRACK);
        assertNotNull(SpotCheck.find(nether.world(), 5, 5, ANYWHERE));
        nether.set(5, 80, 5, Material.LAVA);
        assertNull(SpotCheck.find(nether.world(), 5, 5, ANYWHERE));
        // Behind a roof it cannot fall.
        nether.set(5, 70, 5, Material.NETHERRACK);
        Location spot = SpotCheck.find(nether.world(), 5, 5, ANYWHERE);
        assertNotNull(spot);
        assertEquals(65, spot.getBlockY());
    }

    @Test
    void readsOnlyTheSpotsOwnChunk() {
        // Column 15 sits on the chunk's edge: its neighbours at x 16 are in another chunk and are not read.
        assertNotNull(SpotCheck.find(ground(World.Environment.NORMAL).world(), 15, 0, ANYWHERE));
        assertTrue(SpotCheck.stillSafe(new Location(ground(World.Environment.NORMAL).world(), 0.5, 65, 15.5), ANYWHERE));
    }
}
