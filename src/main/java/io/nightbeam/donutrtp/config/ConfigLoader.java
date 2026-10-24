package io.nightbeam.donutrtp.config;

import io.nightbeam.donutrtp.destination.Destination;
import io.nightbeam.donutrtp.destination.MenuItem;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Writes the default files, upgrades 1.x files, and reads config.yml into {@link Settings}. */
public final class ConfigLoader {
    public static final int CONFIG_VERSION = 2;
    /** A rank under cooldown.ranks is granted by this permission plus the rank's name. */
    public static final String RANK_PERMISSION = "donutrtp.cooldown.";

    private ConfigLoader() {
    }

    /**
     * Reads config.yml, writing the defaults first and upgrading a 1.x file. Throws when the file is not valid YAML, so
     * a typo never silently turns into an empty config.
     */
    public static Settings load(JavaPlugin plugin) throws InvalidConfigurationException, IOException {
        Logger log = plugin.getLogger();
        File folder = plugin.getDataFolder();
        File configFile = new File(folder, "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        YamlConfiguration config = parse(configFile);
        if (ConfigMigration.isLegacy(config)) {
            config = upgrade(plugin, config);
        }
        if (!new File(folder, "zones.yml").exists()) {
            plugin.saveResource("zones.yml", false);
        }
        if (!new File(folder, "messages.yml").exists()) {
            plugin.saveResource("messages.yml", false);
        }
        return read(config, log);
    }

    /** The built-in defaults, for when config.yml cannot be read. */
    public static Settings defaults(JavaPlugin plugin) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(resourceText(plugin, "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the default config: " + e.getMessage());
        }
        return read(config, plugin.getLogger());
    }

    /** Loads a YAML file, failing on syntax errors instead of returning an empty configuration. */
    public static YamlConfiguration parse(File file) throws InvalidConfigurationException, IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return yaml;
    }

    /** Backs up the 1.x files, then writes 2.0 versions with the same settings. */
    private static YamlConfiguration upgrade(JavaPlugin plugin, YamlConfiguration old) {
        Logger log = plugin.getLogger();
        File folder = plugin.getDataFolder();
        try {
            String defaults = resourceText(plugin, "config.yml");
            ConfigMigration.Result result = ConfigMigration.migrate(old, defaults);
            backup(new File(folder, "config.yml"), new File(folder, "config-1.x.yml"));
            result.config().save(new File(folder, "config.yml"));

            File zonesFile = new File(folder, "zones.yml");
            ConfigurationSection migrated = result.zones().getConfigurationSection("zones");
            if (migrated != null && !migrated.getKeys(false).isEmpty()) {
                YamlConfiguration zones = new YamlConfiguration();
                zones.loadFromString(zonesFile.exists()
                        ? Files.readString(zonesFile.toPath(), StandardCharsets.UTF_8)
                        : resourceText(plugin, "zones.yml"));
                for (String id : migrated.getKeys(false)) {
                    if (!zones.contains("zones." + id)) {
                        zones.set("zones." + id, migrated.getConfigurationSection(id));
                    }
                }
                zones.save(zonesFile);
            }

            File messagesFile = new File(folder, "messages.yml");
            if (messagesFile.exists()) {
                YamlConfiguration oldMessages = YamlConfiguration.loadConfiguration(messagesFile);
                backup(messagesFile, new File(folder, "messages-1.x.yml"));
                YamlConfiguration messages = new YamlConfiguration();
                messages.loadFromString(resourceText(plugin, "messages.yml"));
                if (oldMessages.isString("prefix")) {
                    messages.set("prefix", oldMessages.getString("prefix"));
                }
                messages.save(messagesFile);
            }

            log.info("Upgraded your DonutRTP 1.x config to 2.0. The old files are kept as config-1.x.yml and messages-1.x.yml.");
            for (String note : result.notes()) {
                log.info("  " + note);
            }
            return YamlConfiguration.loadConfiguration(new File(folder, "config.yml"));
        } catch (Exception e) {
            log.severe("Could not upgrade the 1.x config: " + e.getMessage() + ". Using the 2.0 defaults for now.");
            YamlConfiguration fallback = new YamlConfiguration();
            try {
                fallback.loadFromString(resourceText(plugin, "config.yml"));
            } catch (Exception ignored) {
                // An empty config still loads with the built-in defaults below.
            }
            return fallback;
        }
    }

    private static void backup(File from, File to) throws IOException {
        if (!to.exists()) {
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String resourceText(JavaPlugin plugin, String name) throws IOException {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                throw new IOException(name + " is missing from the plugin jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Reads a 2.0 config. Bad values are logged and replaced with defaults, so a typo never stops the plugin. */
    public static Settings read(ConfigurationSection config, Logger log) {
        Settings.Warmup warmup = new Settings.Warmup(
                Math.max(0, config.getInt("warmup.seconds", 5)),
                config.getBoolean("warmup.cancel-on-move", true),
                config.getBoolean("warmup.cancel-on-damage", true),
                config.getBoolean("warmup.actionbar", true),
                config.getBoolean("warmup.title", false),
                sound(config.getString("warmup.tick-sound", ""), 1.0, 1.0, log));

        // cooldown.ranks: {vip: 120} means the permission donutrtp.cooldown.vip gives 120 seconds.
        Map<String, Integer> tiers = new LinkedHashMap<>();
        ConfigurationSection ranks = config.getConfigurationSection("cooldown.ranks");
        if (ranks != null) {
            for (String rank : ranks.getKeys(false)) {
                tiers.put(RANK_PERMISSION + rank.toLowerCase(Locale.ROOT), Math.max(0, ranks.getInt(rank)));
            }
        }
        Settings.Cooldowns cooldowns = new Settings.Cooldowns(
                Math.max(0, config.getInt("cooldown.seconds", 300)),
                config.getBoolean("cooldown.shared", true),
                Collections.unmodifiableMap(tiers));

        Settings.Search search = new Settings.Search(
                clamp(config.getInt("search.max-attempts", 40), 1, 500),
                clamp(config.getInt("search.pool-size", 3), 0, 20),
                clamp(config.getInt("search.refill-seconds", 4), 1, 300),
                config.getBoolean("search.avoid-regions", true),
                config.getBoolean("search.avoid-claims", true),
                clamp(config.getInt("search.border-margin", 8), 0, 1000));

        Settings.Landing landing = new Settings.Landing(
                Math.max(0, config.getInt("landing.no-fall-damage-seconds", 5)),
                Math.max(0, config.getInt("landing.invulnerable-seconds", 3)),
                sound(config.getString("landing.sound", "ENTITY_ENDERMAN_TELEPORT"),
                        config.getDouble("landing.volume", 1.0), config.getDouble("landing.pitch", 1.0), log),
                config.getString("landing.particle", "PORTAL").trim(),
                List.copyOf(config.getStringList("landing.commands")));

        Settings.Menu menu = new Settings.Menu(
                config.getString("menu.title", "&8ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ"),
                clamp(config.getInt("menu.rows", 3), 1, 6),
                config.getBoolean("menu.fill-empty-slots", true),
                config.getString("menu.filler", "GRAY_STAINED_GLASS_PANE"),
                config.getString("menu.cooldown-lore", "&cAvailable in {time}"),
                config.getString("menu.locked-lore", "&cYou cannot use this destination."),
                config.getString("menu.cost-lore", "&7Cost: &e{cost}"));

        Map<String, Destination> destinations = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("destinations");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection destination = section.getConfigurationSection(id);
                if (destination != null) {
                    destinations.put(id.toLowerCase(Locale.ROOT), destination(id.toLowerCase(Locale.ROOT), destination, menu.rows(), log));
                }
            }
        }
        if (destinations.isEmpty()) {
            log.warning("config.yml has no destinations, so /rtp has nowhere to send players.");
        }

        return new Settings(warmup, cooldowns, search, landing, menu, Collections.unmodifiableMap(destinations),
                config.getBoolean("update-check.enabled", true),
                config.getBoolean("update-check.notify-ops-on-join", true));
    }

    static Destination destination(String id, ConfigurationSection s, int menuRows, Logger log) {
        String world = s.getString("world", id);
        Destination.Shape shape;
        try {
            shape = Destination.Shape.valueOf(s.getString("shape", "SQUARE").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Destination '" + id + "': shape must be SQUARE or CIRCLE. Using SQUARE.");
            shape = Destination.Shape.SQUARE;
        }
        boolean spawn = "spawn".equalsIgnoreCase(s.getString("center", ""));
        int centerX = spawn ? 0 : s.getInt("center.x", 0);
        int centerZ = spawn ? 0 : s.getInt("center.z", 0);
        int minRadius = Math.max(0, s.getInt("min-radius", 0));
        int maxRadius = s.getInt("max-radius", 5000);
        if (maxRadius < minRadius + 16) {
            log.warning("Destination '" + id + "': max-radius must be at least 16 more than min-radius. Using "
                    + (minRadius + 16) + ".");
            maxRadius = minRadius + 16;
        }
        int minY = s.getInt("min-y", -64);
        int maxY = s.getInt("max-y", 320);
        if (maxY < minY) {
            log.warning("Destination '" + id + "': max-y is below min-y. Swapping them.");
            int swap = minY;
            minY = maxY;
            maxY = swap;
        }
        double cost = Math.max(0, s.getDouble("cost", 0));
        int cooldown = s.getInt("cooldown", -1);

        ConfigurationSection gui = s.getConfigurationSection("gui");
        MenuItem item;
        if (gui == null) {
            item = new MenuItem(-1, "COMPASS", "", "", "", "&e" + id, List.of());
        } else {
            int slot = gui.getInt("slot", -1);
            if (slot >= menuRows * 9) {
                log.warning("Destination '" + id + "': slot " + slot + " is outside a " + menuRows + "-row menu. It is hidden.");
                slot = -1;
            }
            item = new MenuItem(slot,
                    gui.getString("material", "COMPASS"),
                    gui.getString("head.texture", "").trim(),
                    gui.getString("head.uuid", "").trim(),
                    gui.getString("head.player", "").trim(),
                    gui.getString("name", "&e" + id),
                    List.copyOf(gui.getStringList("lore")));
        }
        return new Destination(id, world, shape, spawn, centerX, centerZ, minRadius, maxRadius, minY, maxY,
                biomes(id, s.getStringList("biomes.allow"), log), biomes(id, s.getStringList("biomes.deny"), log),
                s.getString("permission", "").trim(), cooldown, cost, item);
    }

    private static Set<String> biomes(String destination, List<String> names, Logger log) {
        Set<String> out = new LinkedHashSet<>();
        for (String name : names) {
            String key = name.trim().toLowerCase(Locale.ROOT);
            if (key.startsWith("#")) {
                log.warning("Destination '" + destination + "': biome tags like " + name
                        + " are not supported; list the biomes themselves (minecraft:ocean, ...). It is ignored.");
                continue;
            }
            if (!key.isEmpty()) {
                out.add(key.contains(":") ? key : "minecraft:" + key);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    private static SoundSpec sound(String name, double volume, double pitch, Logger log) {
        SoundSpec spec = SoundSpec.of(name, volume, pitch);
        if (spec.enabled() && !SoundSpec.known(spec.key())) {
            log.warning("Unknown sound '" + name + "' (" + spec.key() + "). Players will hear nothing for it.");
        }
        return spec;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
