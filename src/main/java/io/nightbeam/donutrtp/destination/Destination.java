package io.nightbeam.donutrtp.destination;

import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.permissions.Permissible;

/** A place /rtp can send players: an area of one world, with its own rules. */
public record Destination(String id, String worldName, Shape shape, boolean centerOnSpawn, int centerX, int centerZ,
                          int minRadius, int maxRadius, int minY, int maxY, Set<String> biomeAllow,
                          Set<String> biomeDeny, String permission, int cooldown, double cost, MenuItem item) {
    public enum Shape {
        SQUARE, CIRCLE
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    /** The menu name without colours, or the id. */
    public String displayName() {
        String stripped = item == null ? "" : ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', item.name()));
        return stripped == null || stripped.isBlank() ? id : stripped.trim();
    }

    public boolean canUse(Permissible who) {
        return permission.isEmpty() || who.hasPermission(permission);
    }

    /** Biome keys are lower case with a namespace, like {@code minecraft:ocean}. */
    public boolean allowsBiome(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (biomeDeny.contains(normalized)) {
            return false;
        }
        return biomeAllow.isEmpty() || biomeAllow.contains(normalized);
    }

    public boolean filtersBiomes() {
        return !biomeAllow.isEmpty() || !biomeDeny.isEmpty();
    }
}
