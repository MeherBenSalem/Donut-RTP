package io.nightbeam.donutrtp.rtp;

import io.nightbeam.donutrtp.config.ConfigManager;
import io.nightbeam.donutrtp.config.RtpZoneSettings;
import io.nightbeam.donutrtp.config.Settings;
import io.nightbeam.donutrtp.util.FoliaCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class RtpZoneManager {

    private final FoliaCompat foliaCompat;
    private final ConfigManager configManager;
    private final RtpManager rtpManager;

    private final Map<UUID, ZoneCountdownTask> activeCountdowns = new HashMap<>();
    private Map<String, List<RtpZoneSettings>> zonesByWorld = Map.of();

    public RtpZoneManager(FoliaCompat foliaCompat, ConfigManager configManager, RtpManager rtpManager) {
        this.foliaCompat = foliaCompat;
        this.configManager = configManager;
        this.rtpManager = rtpManager;
        reload();
    }

    public void reload() {
        Settings settings = configManager.settings();
        Map<String, List<RtpZoneSettings>> map = new HashMap<>();
        for (RtpZoneSettings zone : settings.rtpZones()) {
            if (!zone.enabled()) {
                continue;
            }
            map.computeIfAbsent(zone.worldName(), ignored -> new ArrayList<>()).add(zone);
        }
        zonesByWorld = Map.copyOf(map);
    }

    public void onPlayerMove(Player player, Location from, Location to) {
        Settings settings = configManager.settings();
        if (!settings.rtpZonesEnabled()) {
            return;
        }

        ZoneCountdownTask active = activeCountdowns.get(player.getUniqueId());
        if (active != null) {
            if (!active.zone().contains(to)) {
                cancelCountdown(player, true);
            }
            return;
        }

        RtpZoneSettings fromZone = findZoneAt(from, settings);
        RtpZoneSettings toZone = findZoneAt(to, settings);
        if (fromZone == null && toZone != null) {
            tryStartCountdown(player, toZone);
        }
    }

    public void onPlayerQuit(Player player) {
        cancelCountdown(player, false);
    }

    public void shutdown() {
        activeCountdowns.values().forEach(task -> task.cancel(false));
        activeCountdowns.clear();
    }

    private void tryStartCountdown(Player player, RtpZoneSettings zone) {
        if (activeCountdowns.containsKey(player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission("donutrtp.zone.use")) {
            return;
        }
        if (zone.hasPermission() && !player.hasPermission(zone.permission())) {
            return;
        }

        Settings settings = configManager.settings();
        UUID playerId = player.getUniqueId();
        ZoneCountdownTask task = new ZoneCountdownTask(
                foliaCompat,
                configManager,
                rtpManager,
                settings.actionBarCooldownSound(),
                zone,
                player,
                () -> player.sendMessage(configManager.message("zone-countdown-cancelled")),
                () -> activeCountdowns.remove(playerId)
        );
        activeCountdowns.put(playerId, task);
        task.start();
    }

    private void cancelCountdown(Player player, boolean sendCallback) {
        ZoneCountdownTask task = activeCountdowns.get(player.getUniqueId());
        if (task != null && task.isActive()) {
            task.cancel(sendCallback);
        }
    }

    private RtpZoneSettings findZoneAt(Location location, Settings settings) {
        if (location == null || location.getWorld() == null || !settings.rtpZonesEnabled()) {
            return null;
        }

        List<RtpZoneSettings> zones = zonesByWorld.get(location.getWorld().getName());
        if (zones == null) {
            return null;
        }

        for (RtpZoneSettings zone : zones) {
            if (zone.contains(location)) {
                return zone;
            }
        }
        return null;
    }
}
