package io.nightbeam.donutrtp.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** Folia's schedulers. Only loaded on Folia. */
final class FoliaScheduler extends Scheduler {
    FoliaScheduler(Plugin plugin) {
        super(plugin);
    }

    @Override
    public void global(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public Task globalLater(Runnable task, long ticks) {
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), Math.max(1, ticks));
        return scheduled::cancel;
    }

    @Override
    public Task globalTimer(Runnable task, long delay, long period) {
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> task.run(), Math.max(1, delay), Math.max(1, period));
        return scheduled::cancel;
    }

    @Override
    public void region(World world, int chunkX, int chunkZ, Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, task);
    }

    @Override
    public void entity(Entity entity, Runnable task) {
        entity.getScheduler().execute(plugin, task, null, 0L);
    }

    @Override
    public Task entityLater(Entity entity, Runnable task, long ticks) {
        ScheduledTask scheduled = entity.getScheduler().runDelayed(plugin, t -> task.run(), null, Math.max(1, ticks));
        return scheduled == null ? () -> {
        } : scheduled::cancel;
    }

    @Override
    public void async(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    @Override
    public boolean owns(Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(entity);
    }

    @Override
    public boolean ownsChunk(World world, int chunkX, int chunkZ) {
        return Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ);
    }
}
