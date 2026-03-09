package io.nightbeam.donutrtp.rtp;

import io.nightbeam.donutrtp.config.ConfigManager;
import io.nightbeam.donutrtp.config.Settings;
import io.nightbeam.donutrtp.config.WorldSettings;
import io.nightbeam.donutrtp.util.FoliaCompat;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RtpManager {

    private final JavaPlugin plugin;
    private final FoliaCompat foliaCompat;
    private final ConfigManager configManager;
    private final SafeLocationFinder locationFinder;

    private final Map<UUID, Long> cooldownUntilEpoch = new ConcurrentHashMap<>();
    private final Map<UUID, WarmupTask> warmups = new ConcurrentHashMap<>();

    public RtpManager(JavaPlugin plugin, FoliaCompat foliaCompat, ConfigManager configManager) {
        this.plugin = plugin;
        this.foliaCompat = foliaCompat;
        this.configManager = configManager;
        this.locationFinder = new SafeLocationFinder(foliaCompat);
    }

    public void startTeleport(Player player, WorldType type) {
        Settings settings = configManager.settings();
        long now = Instant.now().getEpochSecond();
        long cooldownUntil = cooldownUntilEpoch.getOrDefault(player.getUniqueId(), 0L);

        if (cooldownUntil > now) {
            long wait = cooldownUntil - now;
            player.sendMessage(configManager.message("cooldown").replace("%time%", String.valueOf(wait)));
            return;
        }

        WorldSettings worldSettings = settings.worlds().get(type);
        World world = Bukkit.getWorld(worldSettings.worldName());
        if (world == null) {
            player.sendMessage(configManager.message("world-not-found"));
            return;
        }

        WarmupTask previous = warmups.remove(player.getUniqueId());
        if (previous != null) {
            previous.cancel(false);
        }

        WarmupTask warmup = new WarmupTask(
                foliaCompat,
                configManager,
                player,
                settings.warmupSeconds(),
                () -> doTeleport(player, world, worldSettings, settings),
                () -> player.sendMessage(configManager.message("cancelled-move"))
        );
        warmups.put(player.getUniqueId(), warmup);
        warmup.start();
    }

    public void cancelWarmupIfMoving(Player player) {
        WarmupTask warmup = warmups.remove(player.getUniqueId());
        if (warmup != null && warmup.isActive()) {
            warmup.cancel(true);
        }
    }

    public void shutdown() {
        warmups.values().forEach(task -> task.cancel(false));
        warmups.clear();
        cooldownUntilEpoch.clear();
    }

    private void doTeleport(Player player, World world, WorldSettings worldSettings, Settings settings) {
        if (!player.isOnline()) {
            warmups.remove(player.getUniqueId());
            return;
        }

        locationFinder.findSafeLocation(world, worldSettings, settings.maxAttempts())
                .thenAccept(location -> foliaCompat.runForEntity(player, () -> {
                    warmups.remove(player.getUniqueId());

                    if (!player.isOnline()) {
                        return;
                    }

                    if (location == null) {
                        player.sendMessage(configManager.message("no-safe-location"));
                        return;
                    }

                    teleport(player, location);
                    long expiresAt = Instant.now().getEpochSecond() + settings.cooldownSeconds();
                    cooldownUntilEpoch.put(player.getUniqueId(), expiresAt);
                    player.sendMessage(configManager.message("teleported"));
                }));
    }

    private void teleport(Player player, Location location) {
        foliaCompat.teleport(player, location);
    }
}
