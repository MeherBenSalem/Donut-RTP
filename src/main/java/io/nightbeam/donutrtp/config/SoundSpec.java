package io.nightbeam.donutrtp.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;

/**
 * A configured sound. Names can be Bukkit names ({@code ENTITY_ENDERMAN_TELEPORT}) or keys
 * ({@code entity.enderman.teleport}, {@code minecraft:entity.enderman.teleport}). Played through the
 * String overload, so it works whether {@code Sound} is an enum (before 1.21.3) or an interface.
 */
public record SoundSpec(String key, float volume, float pitch) {
    public static final SoundSpec NONE = new SoundSpec("", 0, 0);

    private static volatile Map<String, String> registryByFlatName;

    public static SoundSpec of(String name, double volume, double pitch) {
        String key = toKey(name);
        return key.isEmpty() ? NONE : new SoundSpec(key, clamp(volume, 0, 10), clamp(pitch, 0.5, 2));
    }

    public boolean enabled() {
        return !key.isEmpty() && volume > 0;
    }

    public void play(Player player) {
        play(player, player.getLocation());
    }

    public void play(Player player, Location at) {
        if (enabled()) {
            player.playSound(at, key, volume, pitch);
        }
    }

    /** {@code ENTITY_ENDERMAN_TELEPORT} and {@code entity.enderman.teleport} both become {@code minecraft:entity.enderman.teleport}. */
    public static String toKey(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if (lower.contains(":")) {
            return lower;
        }
        if (lower.contains(".")) {
            return "minecraft:" + lower;
        }
        String fromRegistry = registryKey(lower);
        return fromRegistry != null ? fromRegistry : "minecraft:" + guessKey(lower);
    }

    /** Whether the server knows this sound. True when it cannot tell (no server, or no sound registry). */
    public static boolean known(String key) {
        try {
            if (Bukkit.getServer() == null || key.isEmpty()) {
                return true;
            }
            NamespacedKey namespaced = NamespacedKey.fromString(key);
            return namespaced == null || Registry.SOUNDS.get(namespaced) != null;
        } catch (Throwable e) {
            return true;
        }
    }

    /**
     * Enum names flatten dots and underscores into one underscore ({@code BLOCK_NOTE_BLOCK_HAT}), so the real key is
     * looked up in the server's sound registry by its flattened form.
     */
    private static String registryKey(String flat) {
        try {
            if (Bukkit.getServer() == null) {
                return null;
            }
            Map<String, String> map = registryByFlatName;
            if (map == null) {
                map = new HashMap<>();
                for (Object sound : (Iterable<?>) Registry.SOUNDS) {
                    NamespacedKey key = ((Keyed) sound).getKey();
                    map.putIfAbsent(key.getKey().replace('.', '_'), key.toString());
                }
                registryByFlatName = map;
            }
            return map.get(flat);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Without a server: category.source.event, which is right for nearly every sound. */
    static String guessKey(String flat) {
        String[] parts = flat.split("_");
        if (parts.length < 3) {
            return flat.replace('_', '.');
        }
        StringBuilder source = new StringBuilder();
        for (int i = 1; i < parts.length - 1; i++) {
            if (source.length() > 0) {
                source.append('_');
            }
            source.append(parts[i]);
        }
        return parts[0] + "." + source + "." + parts[parts.length - 1];
    }

    private static float clamp(double value, double min, double max) {
        return (float) Math.max(min, Math.min(max, value));
    }
}
