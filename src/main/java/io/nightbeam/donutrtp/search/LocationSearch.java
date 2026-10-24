package io.nightbeam.donutrtp.search;

import io.nightbeam.donutrtp.config.Settings;
import io.nightbeam.donutrtp.destination.Destination;
import io.nightbeam.donutrtp.platform.Platform;
import io.nightbeam.donutrtp.platform.Scheduler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * Finds safe random spots without freezing the server: chunks load asynchronously on Paper and Folia, blocks are read
 * on the thread that owns them, and a few spots per destination are kept ready so most teleports start at once.
 */
public final class LocationSearch {
    /** How long one search may take before it gives up. Generating chunks far from anything is slow on busy servers. */
    private static final long TIMEOUT_TICKS = 20L * 40;
    /** Chunks a player's search loads at the same time on Paper and Folia; background refills load one. */
    private static final int PARALLEL = 2;
    /** Columns checked in each loaded chunk, so one expensive chunk generation is used fully. */
    private static final int COLUMNS_PER_CHUNK = 4;
    /** {@code -Ddonutrtp.debug=true} logs every step of every search. */
    private static final boolean DEBUG = Boolean.getBoolean("donutrtp.debug");

    private final Scheduler scheduler;
    private final Supplier<Settings> settings;
    private final Predicate<Location> isProtected;
    private final Logger log;
    private final Map<String, Deque<Location>> pools = new ConcurrentHashMap<>();
    /** When each destination last logged a failed search, so a bad config warns now and then, not on every /rtp. */
    private final Map<String, Long> lastWarning = new ConcurrentHashMap<>();
    private final AtomicBoolean refilling = new AtomicBoolean();
    /** Searches players are waiting for; background refills hold back while there are any. */
    private final AtomicInteger urgent = new AtomicInteger();
    private final AtomicInteger refillCursor = new AtomicInteger();
    private Scheduler.Task refillTask;

    public LocationSearch(Scheduler scheduler, Supplier<Settings> settings, Predicate<Location> isProtected, Logger log) {
        this.scheduler = scheduler;
        this.settings = settings;
        this.isProtected = isProtected;
        this.log = log;
    }

    /** Starts (or restarts, after a reload) the background refill of the ready spots. */
    public void start() {
        stop();
        pools.clear();
        int poolSize = settings.get().search().poolSize();
        if (poolSize > 0) {
            long period = 20L * settings.get().search().refillSeconds();
            refillTask = scheduler.globalTimer(this::refillOne, 40L, period);
        }
    }

    public void stop() {
        if (refillTask != null) {
            refillTask.cancel();
            refillTask = null;
        }
        refilling.set(false);
    }

    public int ready(String destinationId) {
        Deque<Location> pool = pools.get(destinationId);
        return pool == null ? 0 : pool.size();
    }

    /** A safe spot for the destination, or null when none was found. Completes on any thread. */
    public CompletableFuture<Location> find(Destination destination) {
        Deque<Location> pool = pools.get(destination.id());
        Location ready = pool == null ? null : pool.pollFirst();
        if (ready == null) {
            return search(destination, settings.get().search().maxAttempts(), true);
        }
        CompletableFuture<Location> out = new CompletableFuture<>();
        World world = ready.getWorld();
        int chunkX = ready.getBlockX() >> 4;
        int chunkZ = ready.getBlockZ() >> 4;
        load(world, chunkX, chunkZ, true).whenComplete((ignored, error) -> scheduler.region(world, chunkX, chunkZ, () -> {
            boolean ok = false;
            try {
                ok = error == null && SpotCheck.stillSafe(ready, destination) && !isProtected.test(ready);
            } catch (Throwable e) {
                log.log(Level.FINE, "Could not re-check a ready spot", e);
            }
            if (ok) {
                out.complete(ready);
            } else {
                search(destination, settings.get().search().maxAttempts(), true).whenComplete((spot, e) -> out.complete(spot));
            }
        }));
        return out;
    }

    /**
     * Searches now, ignoring the ready spots. An urgent search is for a waiting player: its chunks load first and it
     * holds back the background refills.
     */
    public CompletableFuture<Location> search(Destination destination, int maxAttempts, boolean playerWaiting) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        World world = destination.world();
        if (world == null) {
            result.complete(null);
            return result;
        }
        if (playerWaiting) {
            urgent.incrementAndGet();
            result.whenComplete((spot, error) -> urgent.decrementAndGet());
        }
        Attempts[] running = new Attempts[1];
        Scheduler.Task timeout = scheduler.globalLater(() -> {
            // A slow background refill is normal on a busy server; only a waiting player's timeout is worth a warning.
            if (result.complete(null) && running[0] != null && playerWaiting) {
                running[0].report("timed out after " + TIMEOUT_TICKS / 20 + "s");
            }
        }, TIMEOUT_TICKS);
        result.whenComplete((spot, error) -> timeout.cancel());
        int margin = settings.get().search().borderMargin();
        // The world border and spawn belong to the global region on Folia.
        scheduler.global(() -> {
            try {
                Sampler.Border border = border(world, margin);
                int centerX = destination.centerX();
                int centerZ = destination.centerZ();
                if (destination.centerOnSpawn()) {
                    Location spawn = world.getSpawnLocation();
                    centerX = spawn.getBlockX();
                    centerZ = spawn.getBlockZ();
                }
                Attempts attempts = new Attempts(world, destination, centerX, centerZ, border, maxAttempts, playerWaiting, result);
                running[0] = attempts;
                int chains = Platform.asyncChunks() && playerWaiting ? PARALLEL : 1;
                for (int i = 0; i < chains; i++) {
                    attempts.next();
                }
            } catch (Throwable e) {
                log.log(Level.WARNING, "Random teleport search failed for " + destination.id(), e);
                result.complete(null);
            }
        });
        return result;
    }

    /**
     * Spots for {@code count} players near {@code center}, at most {@code spread} blocks away and each on its own
     * block. Runs on the thread that owns the centre and only reads loaded chunks it owns; every column in reach is
     * tried, in random order. Players share the centre only when no other safe spot exists.
     */
    public CompletableFuture<List<Location>> spread(Location center, int count, int spread, Destination destination) {
        CompletableFuture<List<Location>> out = new CompletableFuture<>();
        World world = center.getWorld();
        scheduler.region(world, center.getBlockX() >> 4, center.getBlockZ() >> 4, () -> {
            List<Location> spots = new ArrayList<>(count);
            spots.add(center);
            Set<Long> used = new HashSet<>();
            used.add(blockKey(center));
            List<int[]> offsets = new ArrayList<>();
            for (int dx = -spread; dx <= spread; dx++) {
                for (int dz = -spread; dz <= spread; dz++) {
                    if (dx != 0 || dz != 0) {
                        offsets.add(new int[]{dx, dz});
                    }
                }
            }
            Collections.shuffle(offsets, ThreadLocalRandom.current());
            for (int[] offset : offsets) {
                if (spots.size() >= count) {
                    break;
                }
                int x = center.getBlockX() + offset[0];
                int z = center.getBlockZ() + offset[1];
                if (!world.isChunkLoaded(x >> 4, z >> 4) || !scheduler.ownsChunk(world, x >> 4, z >> 4)) {
                    continue;
                }
                try {
                    Location spot = SpotCheck.findNear(world, x, z, center.getBlockY(), destination);
                    if (spot != null && used.add(blockKey(spot))) {
                        spots.add(spot);
                    }
                } catch (Throwable e) {
                    log.log(Level.FINE, "Could not check a spread spot", e);
                }
            }
            while (spots.size() < count) {
                spots.add(center.clone());
            }
            out.complete(spots);
        });
        return out;
    }

    private static long blockKey(Location at) {
        return ((long) at.getBlockX() & 0x3FFFFFFL) << 38 | ((long) at.getBlockZ() & 0x3FFFFFFL) << 12 | (at.getBlockY() & 0xFFFL);
    }

    private void refillOne() {
        if (urgent.get() > 0 || Bukkit.getOnlinePlayers().isEmpty() || !refilling.compareAndSet(false, true)) {
            return;
        }
        Settings current = settings.get();
        List<Destination> destinations = new ArrayList<>(current.destinations().values());
        int size = current.search().poolSize();
        for (int i = 0; i < destinations.size(); i++) {
            Destination destination = destinations.get(Math.floorMod(refillCursor.getAndIncrement(), destinations.size()));
            Deque<Location> pool = pools.computeIfAbsent(destination.id(), id -> new ConcurrentLinkedDeque<>());
            if (pool.size() >= size || destination.world() == null) {
                continue;
            }
            search(destination, current.search().maxAttempts(), false).whenComplete((spot, error) -> {
                if (spot != null && pool.size() < size) {
                    pool.addLast(spot);
                }
                refilling.set(false);
            });
            return;
        }
        refilling.set(false);
    }

    private CompletableFuture<?> load(World world, int chunkX, int chunkZ, boolean playerWaiting) {
        if (Platform.asyncChunks()) {
            return world.getChunkAtAsync(chunkX, chunkZ, true, playerWaiting);
        }
        // Bukkit and Spigot: the chunk loads when its blocks are read on the main thread.
        return CompletableFuture.completedFuture(null);
    }

    static Sampler.Border border(World world, int margin) {
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double half = Math.min(border.getSize() / 2.0, 2.9E7) - margin;
        return new Sampler.Border(center.getX(), center.getZ(), Math.max(1, half));
    }

    /** The attempts of one search, shared by the parallel chains. Each column checked is one attempt. */
    private final class Attempts {
        private final World world;
        private final Destination destination;
        private final int centerX;
        private final int centerZ;
        private final Sampler.Border border;
        private final int max;
        private final boolean playerWaiting;
        private final CompletableFuture<Location> result;
        private final AtomicInteger started = new AtomicInteger();
        private final AtomicInteger running = new AtomicInteger();
        private final AtomicInteger unsafe = new AtomicInteger();
        private final AtomicInteger biome = new AtomicInteger();
        private final AtomicInteger protectedLand = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private final long begun = System.currentTimeMillis();

        Attempts(World world, Destination destination, int centerX, int centerZ, Sampler.Border border, int max,
                 boolean playerWaiting, CompletableFuture<Location> result) {
            this.world = world;
            this.destination = destination;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.border = border;
            this.max = max;
            this.playerWaiting = playerWaiting;
            this.result = result;
        }

        void next() {
            if (result.isDone()) {
                return;
            }
            if (started.incrementAndGet() > max) {
                if (running.get() == 0 && result.complete(null)) {
                    report("gave up after " + max + " tries");
                }
                return;
            }
            int[] column = Sampler.pick(destination.shape(), centerX, centerZ, destination.minRadius(),
                    destination.maxRadius(), border, ThreadLocalRandom.current());
            if (column == null) {
                result.complete(null);
                return;
            }
            int chunkX = column[0] >> 4;
            int chunkZ = column[1] >> 4;
            running.incrementAndGet();
            debug("load chunk " + chunkX + "," + chunkZ);
            load(world, chunkX, chunkZ, playerWaiting).whenComplete((chunk, error) -> {
                if (error != null || result.isDone()) {
                    if (error != null) {
                        failed.incrementAndGet();
                        debug("chunk " + chunkX + "," + chunkZ + " failed: " + error);
                    }
                    done(null);
                    return;
                }
                scheduler.region(world, chunkX, chunkZ, () -> done(checkChunk(column, chunkX, chunkZ)));
            });
        }

        /** The sampled column, then a few more in the same chunk that are also inside the area. */
        private Location checkChunk(int[] first, int chunkX, int chunkZ) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int x = first[0];
            int z = first[1];
            for (int i = 0; i < COLUMNS_PER_CHUNK; i++) {
                if (i > 0) {
                    x = (chunkX << 4) + random.nextInt(16);
                    z = (chunkZ << 4) + random.nextInt(16);
                    if (!border.contains(x, z) || !Sampler.inArea(destination.shape(), centerX, centerZ,
                            destination.minRadius(), destination.maxRadius(), x, z)) {
                        continue;
                    }
                    if (started.incrementAndGet() > max) {
                        return null;
                    }
                }
                Location spot = checkColumn(x, z);
                if (spot != null) {
                    return spot;
                }
            }
            return null;
        }

        private Location checkColumn(int x, int z) {
            try {
                Location spot = SpotCheck.find(world, x, z, destination);
                if (spot == null) {
                    unsafe.incrementAndGet();
                } else if (!SpotCheck.biomeAllowed(spot, destination)) {
                    biome.incrementAndGet();
                } else if (isProtected.test(spot)) {
                    protectedLand.incrementAndGet();
                } else {
                    debug("found " + spot.getBlockX() + " " + spot.getBlockY() + " " + spot.getBlockZ());
                    return spot;
                }
            } catch (Throwable e) {
                failed.incrementAndGet();
                log.log(Level.FINE, "Could not check a random spot", e);
            }
            return null;
        }

        private void debug(String message) {
            if (DEBUG) {
                log.info("[debug] " + destination.id() + (playerWaiting ? " (player)" : " (refill)") + " try "
                        + started.get() + ": " + message);
            }
        }

        /** Tells the admin why nothing was found, at most once every five minutes per destination. */
        void report(String how) {
            long now = System.currentTimeMillis();
            Long last = lastWarning.get(destination.id());
            if (last != null && now - last < 300_000L) {
                return;
            }
            lastWarning.put(destination.id(), now);
            log.warning("No safe spot for destination '" + destination.id() + "': " + how + " ("
                    + (now - begun) + " ms). Checked " + (unsafe.get() + biome.get() + protectedLand.get()) + " columns: "
                    + unsafe.get() + " without safe ground (water, lava, void, trees), " + biome.get() + " in denied biomes, "
                    + protectedLand.get() + " in claims or regions" + (failed.get() > 0 ? ", " + failed.get() + " failed to load" : "")
                    + (running.get() > 0 ? "; " + running.get() + " chunk(s) were still generating, so the server is busy" : "")
                    + ". A larger radius, fewer biome rules or a pre-generated world makes this rarer.");
        }

        private void done(Location spot) {
            running.decrementAndGet();
            if (spot != null) {
                result.complete(spot);
            } else if (Platform.asyncChunks()) {
                next();
            } else {
                // One synchronous chunk load per tick on Bukkit and Spigot.
                scheduler.globalLater(this::next, 1L);
            }
        }
    }
}
