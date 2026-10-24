package io.nightbeam.donutrtp.search;

import io.nightbeam.donutrtp.destination.Destination;
import java.util.Set;
import org.bukkit.HeightMap;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

/** Finds a spot a player can stand on in one column. Must run on the thread that owns the chunk. */
public final class SpotCheck {
    /** Never stand on these. */
    private static final Set<Material> BAD_FLOOR = Set.of(
            Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.POINTED_DRIPSTONE, Material.POWDER_SNOW, Material.BEDROCK, Material.BARRIER,
            Material.SCAFFOLDING, Material.HONEY_BLOCK, Material.SLIME_BLOCK);

    /** Never put feet or head in these, even though they are not solid. */
    private static final Set<Material> BAD_BODY = Set.of(
            Material.LAVA, Material.WATER, Material.FIRE, Material.SOUL_FIRE, Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH, Material.COBWEB, Material.WITHER_ROSE, Material.CACTUS,
            Material.NETHER_PORTAL, Material.END_PORTAL, Material.END_GATEWAY, Material.POINTED_DRIPSTONE,
            Material.SEAGRASS, Material.TALL_SEAGRASS, Material.KELP, Material.KELP_PLANT, Material.BUBBLE_COLUMN,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE);

    /** Blocks under the surface that are checked in case the top one is snow, a carpet or tall grass. */
    private static final int SURFACE_DEPTH = 4;

    private SpotCheck() {
    }

    /** The feet position of a safe spot in column x/z, or null. Biomes are checked separately ({@link #biomeAllowed}). */
    public static Location find(World world, int x, int z, Destination destination) {
        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight();
        int minY = Math.max(destination.minY(), worldMin + 1);
        int maxY = Math.min(destination.maxY(), worldMax - 3);
        if (world.getEnvironment() == World.Environment.NETHER) {
            // Scan down from under the bedrock roof, so players never land on top of it.
            for (int floorY = Math.min(maxY, 125); floorY >= minY; floorY--) {
                Location spot = check(world, x, floorY, z, destination);
                if (spot != null) {
                    return spot;
                }
            }
            return null;
        }
        int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (top < minY || top > maxY) {
            return null;
        }
        for (int floorY = top; floorY >= Math.max(minY, top - SURFACE_DEPTH); floorY--) {
            Location spot = check(world, x, floorY, z, destination);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** A safe spot in column x/z with its floor close to {@code nearY} (for players landing together). */
    public static Location findNear(World world, int x, int z, int nearY, Destination destination) {
        int low = Math.max(world.getMinHeight() + 1, nearY - 7);
        int high = Math.min(world.getMaxHeight() - 3, nearY + 2);
        for (int floorY = high; floorY >= low; floorY--) {
            Location spot = check(world, x, floorY, z, destination);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** Whether a spot found earlier is still safe (blocks change). */
    public static boolean stillSafe(Location feet, Destination destination) {
        World world = feet.getWorld();
        return world != null
                && check(world, feet.getBlockX(), feet.getBlockY() - 1, feet.getBlockZ(), destination) != null;
    }

    private static Location check(World world, int x, int floorY, int z, Destination destination) {
        Block floor = world.getBlockAt(x, floorY, z);
        Material floorType = floor.getType();
        if (!floorType.isSolid() || BAD_FLOOR.contains(floorType) || waterlogged(floor.getBlockData())) {
            return null;
        }
        if (!clear(world.getBlockAt(x, floorY + 1, z)) || !clear(world.getBlockAt(x, floorY + 2, z))) {
            return null;
        }
        return new Location(world, x + 0.5, floorY + 1, z + 0.5);
    }

    private static boolean clear(Block block) {
        Material type = block.getType();
        return !BAD_BODY.contains(type) && block.isPassable() && !block.isLiquid() && !waterlogged(block.getBlockData());
    }

    private static boolean waterlogged(BlockData data) {
        return data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    public static boolean biomeAllowed(Location feet, Destination destination) {
        return !destination.filtersBiomes()
                || destination.allowsBiome(biomeKey(feet.getWorld(), feet.getBlockX(), feet.getBlockY(), feet.getBlockZ()));
    }

    /** Biome became an interface in 1.21.3; both forms are Keyed. */
    static String biomeKey(World world, int x, int y, int z) {
        Object biome = world.getBiome(x, y, z);
        return biome instanceof Keyed keyed ? keyed.getKey().toString() : String.valueOf(biome).toLowerCase(java.util.Locale.ROOT);
    }
}
