package io.nightbeam.donutrtp.rtp;

import io.nightbeam.donutrtp.config.WorldSettings;
import io.nightbeam.donutrtp.util.FoliaCompat;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class SafeLocationFinder {

    private static final Set<Material> BANNED_FLOOR = Set.of(
            Material.LAVA,
            Material.WATER,
            Material.CACTUS,
            Material.MAGMA_BLOCK,
            Material.FIRE,
            Material.SOUL_FIRE
    );

    private static final Set<Material> BANNED_STANDING = Set.of(
            Material.LAVA,
            Material.WATER,
            Material.CACTUS,
            Material.MAGMA_BLOCK,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH,
            Material.COBWEB,
            Material.WITHER_ROSE
    );

    private final FoliaCompat foliaCompat;
    private final Logger logger;

    public SafeLocationFinder(FoliaCompat foliaCompat) {
        this.foliaCompat = foliaCompat;
        this.logger = Bukkit.getLogger();
    }

    public CompletableFuture<Location> findSafeLocation(World world, WorldSettings worldSettings, int maxAttempts) {
        CompletableFuture<Location> future = new CompletableFuture<>();
        AtomicInteger attempts = new AtomicInteger(0);
        tryAttempt(world, worldSettings, maxAttempts, attempts, future);
        return future;
    }

    private void tryAttempt(World world, WorldSettings worldSettings, int maxAttempts, AtomicInteger attempts,
                            CompletableFuture<Location> future) {
        if (future.isDone()) {
            return;
        }

        int current = attempts.incrementAndGet();
        if (current > maxAttempts) {
            logger.warning("RTP failed to find a safe location in world '" + world.getName()
                    + "' after " + maxAttempts + " attempts (radius=" + worldSettings.radius()
                    + ", min-y=" + worldSettings.minY() + ")");
            future.complete(null);
            return;
        }

        int radius = worldSettings.radius();
        int x = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        int z = ThreadLocalRandom.current().nextInt(-radius, radius + 1);

        foliaCompat.runAtRegion(world, x >> 4, z >> 4, () -> {
            if (future.isDone()) {
                return;
            }

            Location safe = findAt(world, x, z, worldSettings.minY());
            if (safe != null) {
                future.complete(safe);
                return;
            }

            foliaCompat.runLaterGlobal(() -> tryAttempt(world, worldSettings, maxAttempts, attempts, future), 1L);
        });
    }

    private Location findAt(World world, int x, int z, int minY) {
        ensureChunkLoaded(world, x, z);

        return switch (world.getEnvironment()) {
            case NETHER -> findAtNether(world, x, z, minY);
            case THE_END, NORMAL -> findAtSurface(world, x, z, minY);
            default -> findAtSurface(world, x, z, minY);
        };
    }

    private Location findAtSurface(World world, int x, int z, int minY) {
        int highest = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        if (highest < minY) {
            return null;
        }

        int floorY = highest - 1;
        return checkSpot(world, x, floorY, z, minY);
    }

    private Location findAtNether(World world, int x, int z, int minY) {
        int maxScan = Math.min(120, world.getMaxHeight() - 2);
        for (int floorY = maxScan; floorY >= minY; floorY--) {
            Location location = checkSpot(world, x, floorY, z, minY);
            if (location != null) {
                return location;
            }
        }
        return null;
    }

    private Location checkSpot(World world, int x, int floorY, int z, int minY) {
        if (floorY < minY) {
            return null;
        }

        Block floor = world.getBlockAt(x, floorY, z);
        Block feet = world.getBlockAt(x, floorY + 1, z);
        Block head = world.getBlockAt(x, floorY + 2, z);

        if (BANNED_FLOOR.contains(floor.getType())) {
            return null;
        }
        if (!floor.getType().isSolid()) {
            return null;
        }
        if (!isPassable(feet.getType()) || !isPassable(head.getType())) {
            return null;
        }

        return new Location(world, x + 0.5D, floorY + 1.0D, z + 0.5D);
    }

    private void ensureChunkLoaded(World world, int x, int z) {
        Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
        if (!chunk.isLoaded()) {
            chunk.load(true);
        }
    }

    private boolean isPassable(Material material) {
        if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
            return true;
        }
        if (BANNED_STANDING.contains(material) || isLiquid(material)) {
            return false;
        }
        return !material.isSolid();
    }

    private boolean isLiquid(Material material) {
        return material == Material.LAVA || material == Material.WATER;
    }
}
