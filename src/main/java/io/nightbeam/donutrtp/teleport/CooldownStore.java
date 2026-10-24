package io.nightbeam.donutrtp.teleport;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Cooldown end times per player and key ({@code *} for the shared cooldown, a destination id, or {@code zone:<name>}).
 * Kept in data/cooldowns.yml so restarts do not reset them.
 */
public final class CooldownStore {
    public static final String SHARED = "*";

    private final Map<UUID, Map<String, Long>> ends = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final File file;
    private final LongSupplier clock;

    public CooldownStore(File file, LongSupplier clock) {
        this.file = file;
        this.clock = clock;
    }

    public static String zoneKey(String zone) {
        return "zone:" + zone;
    }

    public long remainingMillis(UUID player, String key) {
        Map<String, Long> map = ends.get(player);
        Long end = map == null ? null : map.get(key);
        return end == null ? 0 : Math.max(0, end - clock.getAsLong());
    }

    public void start(UUID player, String key, int seconds) {
        if (seconds <= 0) {
            return;
        }
        ends.computeIfAbsent(player, id -> new ConcurrentHashMap<>()).put(key, clock.getAsLong() + seconds * 1000L);
        dirty.set(true);
    }

    public void clear(UUID player) {
        if (ends.remove(player) != null) {
            dirty.set(true);
        }
    }

    public void load(Logger log) {
        ends.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        long now = clock.getAsLong();
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(id);
                for (String key : section.getKeys(false)) {
                    long end = section.getLong(key);
                    if (end > now) {
                        ends.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>()).put(unescape(key), end);
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warning("Skipping a bad entry in " + file.getName() + ": " + id);
            }
        }
    }

    /** Writes the file if anything changed. Safe to call from any thread. */
    public synchronized void saveIfDirty(Logger log) {
        if (!dirty.getAndSet(false)) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        long now = clock.getAsLong();
        for (Map.Entry<UUID, Map<String, Long>> player : ends.entrySet()) {
            player.getValue().values().removeIf(end -> end <= now);
            for (Map.Entry<String, Long> entry : player.getValue().entrySet()) {
                yaml.set(player.getKey() + "." + escape(entry.getKey()), entry.getValue());
            }
        }
        ends.values().removeIf(Map::isEmpty);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new java.io.IOException("could not create " + parent);
            }
            yaml.save(file);
        } catch (Exception e) {
            dirty.set(true);
            log.warning("Could not save cooldowns: " + e.getMessage());
        }
    }

    /** YAML paths use dots, so the keys keep them out. */
    private static String escape(String key) {
        return key.replace(".", "%2E").replace(":", "%3A");
    }

    private static String unescape(String key) {
        return key.replace("%2E", ".").replace("%3A", ":");
    }
}
