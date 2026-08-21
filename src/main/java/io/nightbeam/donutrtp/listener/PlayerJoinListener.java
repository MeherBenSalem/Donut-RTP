package io.nightbeam.donutrtp.listener;

import io.nightbeam.donutrtp.DonutRTPPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Notifies admins when a Modrinth update was detected at startup.
 */
public final class PlayerJoinListener implements Listener {

    private final DonutRTPPlugin plugin;

    public PlayerJoinListener(DonutRTPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.hasUpdateAvailable()) {
            return;
        }
        if (!plugin.getConfig().getBoolean("update-check.notify-ops-on-join", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("donutrtp.admin")) {
            return;
        }

        String template = plugin.getConfig().getString(
                "update-check.join-message",
                "&e[DonutRTP] &7Update available: &f{0} &7— &b{1}");
        String message = template
                .replace("{0}", plugin.getUpdateLatestVersion())
                .replace("{1}", plugin.getUpdateDownloadUrl());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
