package io.nightbeam.donutrtp.util;

import java.lang.reflect.Method;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class FoliaCompat {

    private final Plugin plugin;
    private final boolean folia;

    public FoliaCompat(Plugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
    }

    public boolean isFolia() {
        return folia;
    }

    public void runAsync(Runnable task) {
        if (folia) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public void runAtRegion(World world, int chunkX, int chunkZ, Runnable task) {
        Objects.requireNonNull(world, "world");
        if (folia) {
            Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, task);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void runForEntity(Entity entity, Runnable task) {
        if (folia) {
            entity.getScheduler().execute(plugin, task, null, 0L);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void runLaterForEntity(Entity entity, Runnable task, long ticks) {
        if (folia) {
            entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, ticks);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }

    public void runLaterGlobal(Runnable task, long ticks) {
        if (folia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), ticks);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }

    public void teleport(Player player, Location location) {
        if (folia) {
            player.teleportAsync(location);
            return;
        }
        player.teleport(location);
    }

    public void shutdown() {
        // Nothing to close for Bukkit/Folia schedulers.
    }

    private boolean detectFolia() {
        try {
            Class<?> clazz = Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            Method method = clazz.getDeclaredMethod("isGlobalTickThread");
            return method != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
