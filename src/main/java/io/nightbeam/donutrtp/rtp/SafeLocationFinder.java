package io.nightbeam.donutrtp.rtp;

import io.nightbeam.donutrtp.config.WorldSettings;
import io.nightbeam.donutrtp.util.FoliaCompat;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final FoliaCompat foliaCompat;

    public SafeLocationFinder(FoliaCompat foliaCompat) {
        this.foliaCompat = foliaCompat;
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

            // Yield to avoid long chained work in one tick.
            foliaCompat.runLaterGlobal(() -> tryAttempt(world, worldSettings, maxAttempts, attempts, future), 1L);
        });
    }

    private Location findAt(World world, int x, int z, int minY) {
        int highest = world.getHighestBlockYAt(x, z);
        if (highest <= Math.max(minY, world.getMinHeight())) {
            return null;
        }

        int floorY = highest - 1;
        Block floor = world.getBlockAt(x, floorY, z);
        Block feet = world.getBlockAt(x, floorY + 1, z);
        Block head = world.getBlockAt(x, floorY + 2, z);

        if (floor.getY() < minY) {
            return null;
        }
        if (BANNED_FLOOR.contains(floor.getType())) {
            return null;
        }
        if (!floor.getType().isSolid()) {
            return null;
        }
        if (!isAirLike(feet.getType()) || !isAirLike(head.getType())) {
            return null;
        }

        return new Location(world, x + 0.5D, floorY + 1.0D, z + 0.5D);
    }

    private boolean isAirLike(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }
}
