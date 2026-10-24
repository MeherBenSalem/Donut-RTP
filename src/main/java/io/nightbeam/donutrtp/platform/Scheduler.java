package io.nightbeam.donutrtp.platform;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Runs work on the right thread: Folia's region, entity and global schedulers, or the Bukkit main thread elsewhere.
 * The Folia implementation is a separate class, so Bukkit and Spigot never load Folia's scheduler types.
 */
public abstract class Scheduler {
    /** A scheduled task that can be cancelled. */
    public interface Task {
        void cancel();
    }

    protected final Plugin plugin;

    protected Scheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public static Scheduler create(Plugin plugin) {
        return Platform.folia() ? new FoliaScheduler(plugin) : new BukkitScheduler(plugin);
    }

    /** As soon as possible on the global thread (Folia) or the main thread. */
    public abstract void global(Runnable task);

    public abstract Task globalLater(Runnable task, long ticks);

    public abstract Task globalTimer(Runnable task, long delay, long period);

    /** On the thread that owns the chunk (Folia) or the main thread. */
    public abstract void region(World world, int chunkX, int chunkZ, Runnable task);

    /** On the thread that owns the entity (Folia) or the main thread. Dropped if the entity is gone. */
    public abstract void entity(Entity entity, Runnable task);

    public abstract Task entityLater(Entity entity, Runnable task, long ticks);

    public abstract void async(Runnable task);

    /** Whether the current thread may touch {@code entity} right now. */
    public abstract boolean owns(Entity entity);

    /** Whether the current thread may touch blocks in this chunk right now. */
    public abstract boolean ownsChunk(World world, int chunkX, int chunkZ);

    private static final class BukkitScheduler extends Scheduler {
        BukkitScheduler(Plugin plugin) {
            super(plugin);
        }

        @Override
        public void global(Runnable task) {
            Bukkit.getScheduler().runTask(plugin, task);
        }

        @Override
        public Task globalLater(Runnable task, long ticks) {
            BukkitTask scheduled = Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1, ticks));
            return scheduled::cancel;
        }

        @Override
        public Task globalTimer(Runnable task, long delay, long period) {
            BukkitTask scheduled = Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(1, delay), Math.max(1, period));
            return scheduled::cancel;
        }

        @Override
        public void region(World world, int chunkX, int chunkZ, Runnable task) {
            Bukkit.getScheduler().runTask(plugin, task);
        }

        @Override
        public void entity(Entity entity, Runnable task) {
            Bukkit.getScheduler().runTask(plugin, task);
        }

        @Override
        public Task entityLater(Entity entity, Runnable task, long ticks) {
            BukkitTask scheduled = Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1, ticks));
            return scheduled::cancel;
        }

        @Override
        public void async(Runnable task) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }

        @Override
        public boolean owns(Entity entity) {
            return Bukkit.isPrimaryThread();
        }

        @Override
        public boolean ownsChunk(World world, int chunkX, int chunkZ) {
            return Bukkit.isPrimaryThread();
        }
    }
}
