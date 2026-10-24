package io.nightbeam.donutrtp.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Turns a 1.x config.yml into the 2.0 layout. Pure: it reads the old file and the new default file and returns the new
 * config plus the zones that 1.x kept in config.yml (the WorldGuard zone and the cuboid zones).
 */
public final class ConfigMigration {
    public record Result(YamlConfiguration config, YamlConfiguration zones, List<String> notes) {
    }

    private ConfigMigration() {
    }

    /** A 1.x config has no config-version and uses the old top-level keys. */
    public static boolean isLegacy(ConfigurationSection config) {
        return !config.contains("config-version")
                && (config.contains("warmup-seconds") || config.contains("worlds") || config.contains("rtp-zone")
                || config.contains("cooldown-seconds") || config.contains("gui.items"));
    }

    public static Result migrate(ConfigurationSection old, String defaultConfigText) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(defaultConfigText);
        YamlConfiguration zones = new YamlConfiguration();
        zones.createSection("zones");
        List<String> notes = new ArrayList<>();

        if (old.contains("warmup-seconds")) {
            config.set("warmup.seconds", Math.max(0, old.getInt("warmup-seconds")));
        }
        if (old.getBoolean("instant-teleport", false)) {
            config.set("warmup.seconds", 0);
            notes.add("instant-teleport was on, so warmup.seconds is 0.");
        }
        if (old.contains("cooldown-seconds")) {
            config.set("cooldown.seconds", Math.max(0, old.getInt("cooldown-seconds")));
        }
        if (old.contains("max-attempts")) {
            config.set("search.max-attempts", Math.max(1, old.getInt("max-attempts")));
        }

        ConfigurationSection tick = old.getConfigurationSection("actionbar-cooldown");
        if (tick != null) {
            config.set("warmup.tick-sound", tick.getBoolean("enabled", false) ? tick.getString("sound", "") : "");
        }
        ConfigurationSection landingSound = old.getConfigurationSection("teleport-sound");
        if (landingSound != null) {
            config.set("landing.sound", landingSound.getBoolean("enabled", true)
                    ? landingSound.getString("sound", "ENTITY_ENDERMAN_TELEPORT") : "");
            config.set("landing.volume", landingSound.getDouble("volume", 1.0));
            config.set("landing.pitch", landingSound.getDouble("pitch", 1.0));
        }

        if (old.contains("update-check.enabled")) {
            config.set("update-check.enabled", old.getBoolean("update-check.enabled"));
        }
        if (old.contains("update-check.notify-ops-on-join")) {
            config.set("update-check.notify-ops-on-join", old.getBoolean("update-check.notify-ops-on-join"));
        }

        if (old.contains("gui.fill-empty-slots")) {
            config.set("menu.fill-empty-slots", old.getBoolean("gui.fill-empty-slots"));
        }

        for (String type : List.of("overworld", "nether", "end")) {
            String path = "destinations." + type;
            ConfigurationSection world = old.getConfigurationSection("worlds." + type);
            if (world != null) {
                if (world.contains("world-name")) {
                    config.set(path + ".world", world.getString("world-name"));
                }
                if (world.contains("radius")) {
                    int radius = Math.max(16, world.getInt("radius"));
                    config.set(path + ".max-radius", radius);
                    // 1.x had no minimum distance; keep the new default unless it would eat most of the area.
                    int min = config.getInt(path + ".min-radius", 0);
                    config.set(path + ".min-radius", Math.min(min, radius / 4));
                }
                if (world.contains("min-y")) {
                    config.set(path + ".min-y", world.getInt("min-y"));
                }
            }
            ConfigurationSection item = old.getConfigurationSection("gui.items." + type);
            if (item != null) {
                for (String key : List.of("slot", "name", "lore", "material")) {
                    if (item.contains(key)) {
                        config.set(path + ".gui." + key, item.get(key));
                    }
                }
                ConfigurationSection head = item.getConfigurationSection("head");
                if (head != null) {
                    for (String key : List.of("texture", "uuid", "player")) {
                        String value = head.getString(key, "");
                        if (value != null && !value.isBlank()) {
                            config.set(path + ".gui.head." + key, value);
                        }
                    }
                }
            }
        }

        migrateWorldGuardZone(old.getConfigurationSection("rtp-zone"), zones, notes);
        migrateCuboidZones(old.getConfigurationSection("rtp-zones"), zones, notes);
        return new Result(config, zones, notes);
    }

    private static void migrateWorldGuardZone(ConfigurationSection zone, YamlConfiguration zones, List<String> notes) {
        if (zone == null) {
            return;
        }
        String region = zone.getString("worldguard.region", "");
        if (region == null || region.isBlank()) {
            return;
        }
        String id = uniqueId(zones, sanitize(region));
        String path = "zones." + id;
        boolean entryTriggers = !"INTERACT".equalsIgnoreCase(zone.getString("trigger.mode", "ENTER"));
        boolean enabled = zone.getBoolean("enabled", true) && zone.getBoolean("worldguard.enabled", true) && entryTriggers;
        zones.set(path + ".enabled", enabled);
        zones.set(path + ".destination", destinationFor(zone.getString("destination-world-type", "OVERWORLD")));
        zones.set(path + ".mode", "INDIVIDUAL");
        boolean countdown = zone.getBoolean("countdown.enabled", true);
        zones.set(path + ".countdown", countdown ? Math.max(0, zone.getInt("countdown.seconds", 5)) : 0);
        zones.set(path + ".cooldown", Math.max(0, zone.getInt("cooldown.seconds", 300)));
        zones.set(path + ".cancel-on-move", zone.getBoolean("countdown.cancel-on-move", false));
        zones.set(path + ".display.title", true);
        zones.set(path + ".display.actionbar", true);
        zones.set(path + ".display.bossbar", false);
        zones.set(path + ".area.world", zone.getString("worldguard.world", "world"));
        zones.set(path + ".area.region", region);
        notes.add("The WorldGuard zone on region '" + region + "' is now zone '" + id + "' in zones.yml"
                + (enabled ? "." : " (disabled, as it was in 1.x)."));
    }

    private static void migrateCuboidZones(ConfigurationSection section, YamlConfiguration zones, List<String> notes) {
        ConfigurationSection list = section == null ? null : section.getConfigurationSection("zones");
        if (list == null) {
            return;
        }
        boolean allEnabled = section.getBoolean("enabled", false);
        for (String name : list.getKeys(false)) {
            ConfigurationSection zone = list.getConfigurationSection(name);
            if (zone == null) {
                continue;
            }
            String id = uniqueId(zones, sanitize(name));
            String path = "zones." + id;
            double x = zone.getDouble("x");
            double y = zone.getDouble("y");
            double z = zone.getDouble("z");
            double hx = Math.abs(zone.getDouble("half-size-x", 1));
            double hy = Math.abs(zone.getDouble("half-size-y", 1));
            double hz = Math.abs(zone.getDouble("half-size-z", 1));
            boolean enabled = allEnabled && zone.getBoolean("enabled", true);
            zones.set(path + ".enabled", enabled);
            zones.set(path + ".destination", destinationFor(zone.getString("world-type", "OVERWORLD")));
            zones.set(path + ".mode", "INDIVIDUAL");
            zones.set(path + ".countdown", Math.max(0, zone.getInt("countdown-seconds", 5)));
            zones.set(path + ".cooldown", 0);
            zones.set(path + ".cancel-on-move", false);
            String permission = zone.getString("permission", "");
            if (permission != null && !permission.isBlank()) {
                zones.set(path + ".permission", permission);
            }
            zones.set(path + ".display.title", true);
            zones.set(path + ".display.actionbar", true);
            zones.set(path + ".display.bossbar", false);
            zones.set(path + ".area.world", zone.getString("world", "world"));
            zones.set(path + ".area.min", List.of(floor(x - hx), floor(y - hy), floor(z - hz)));
            zones.set(path + ".area.max", List.of(floor(x + hx), floor(y + hy), floor(z + hz)));
            notes.add("Cuboid zone '" + name + "' is now zone '" + id + "' in zones.yml"
                    + (enabled ? "." : " (disabled, as it was in 1.x)."));
        }
    }

    static String destinationFor(String worldType) {
        return switch (worldType == null ? "" : worldType.trim().toUpperCase(Locale.ROOT)) {
            case "NETHER" -> "nether";
            case "END", "THE_END" -> "end";
            default -> "overworld";
        };
    }

    static String sanitize(String name) {
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return id.isEmpty() ? "zone" : id;
    }

    private static String uniqueId(YamlConfiguration zones, String base) {
        String id = base;
        for (int i = 2; zones.contains("zones." + id); i++) {
            id = base + "_" + i;
        }
        return id;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
