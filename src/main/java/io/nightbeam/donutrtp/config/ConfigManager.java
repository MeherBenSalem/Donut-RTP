package io.nightbeam.donutrtp.config;



import io.nightbeam.donutrtp.rtp.WorldType;

import java.io.File;

import java.util.ArrayList;
import java.util.EnumMap;

import java.util.List;

import java.util.Map;

import java.util.Objects;

import org.bukkit.ChatColor;

import org.bukkit.Material;

import org.bukkit.NamespacedKey;

import org.bukkit.Registry;

import org.bukkit.Sound;

import org.bukkit.configuration.ConfigurationSection;

import org.bukkit.configuration.file.FileConfiguration;

import org.bukkit.configuration.file.YamlConfiguration;

import org.bukkit.plugin.java.JavaPlugin;



public final class ConfigManager {



    private static final Sound DEFAULT_TELEPORT_SOUND = Sound.ENTITY_ENDERMAN_TELEPORT;

    private static final Sound DEFAULT_ACTIONBAR_COOLDOWN_SOUND = Sound.BLOCK_NOTE_BLOCK_HAT;



    private final JavaPlugin plugin;

    private FileConfiguration messagesConfig;



    private volatile Settings cachedSettings;



    public ConfigManager(JavaPlugin plugin) {

        this.plugin = plugin;

    }



    public void reload() {

        plugin.reloadConfig();

        ensureMissingDefaults();

        plugin.reloadConfig();



        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);



        FileConfiguration config = plugin.getConfig();

        int warmup = Math.max(0, config.getInt("warmup-seconds", 5));

        int cooldown = Math.max(0, config.getInt("cooldown-seconds", 300));

        int maxAttempts = Math.max(1, config.getInt("max-attempts", 60));

        boolean instantTeleport = config.getBoolean("instant-teleport", false);

        boolean fillEmpty = config.getBoolean("gui.fill-empty-slots", true);



        Map<WorldType, GuiItemSettings> guiItems = new EnumMap<>(WorldType.class);

        guiItems.put(WorldType.OVERWORLD, readGuiItemSettings(config, "gui.items.overworld", 11, Material.GRASS_BLOCK,

                "&aOverworld RTP",

                List.of("&7Click to randomly teleport", "&7in the Overworld")));

        guiItems.put(WorldType.NETHER, readGuiItemSettings(config, "gui.items.nether", 13, Material.NETHERRACK,

                "&cNether RTP",

                List.of("&7Click to randomly teleport", "&7in the Nether")));

        guiItems.put(WorldType.END, readGuiItemSettings(config, "gui.items.end", 15, Material.END_STONE,

                "&dEnd RTP",

                List.of("&7Click to randomly teleport", "&7in The End")));



        TeleportSoundSettings teleportSound = readTeleportSoundSettings(config);

        ActionBarCooldownSoundSettings actionBarCooldownSound = readActionBarCooldownSoundSettings(config);



        Map<WorldType, WorldSettings> worlds = new EnumMap<>(WorldType.class);

        worlds.put(WorldType.OVERWORLD, readWorldSettings(config, "worlds.overworld", "world", 5000, 60));

        worlds.put(WorldType.NETHER, readWorldSettings(config, "worlds.nether", "world_nether", 4000, 40));

        worlds.put(WorldType.END, readWorldSettings(config, "worlds.end", "world_the_end", 3000, 50));

        boolean rtpZonesEnabled = config.getBoolean("rtp-zones.enabled", false);
        List<RtpZoneSettings> rtpZones = readRtpZones(config);

        this.cachedSettings = new Settings(
                warmup,
                cooldown,
                maxAttempts,
                instantTeleport,
                fillEmpty,
                guiItems,
                teleportSound,
                actionBarCooldownSound,
                worlds,
                rtpZonesEnabled,
                rtpZones
        );

    }



    public Settings settings() {

        return Objects.requireNonNull(cachedSettings, "Settings not loaded yet");

    }



    public String message(String key) {

        return prefix() + plainMessage(key);

    }



    public String plainMessage(String key) {

        return color(messagesConfig.getString(key, ""));

    }



    private String prefix() {

        return color(messagesConfig.getString("prefix", "&6&lRTP &8» "));

    }



    public List<String> messageList(String key) {

        return messagesConfig.getStringList(key).stream().map(this::color).toList();

    }



    public String color(String input) {

        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);

    }



    private void ensureMissingDefaults() {

        FileConfiguration config = plugin.getConfig();

        boolean changed = false;



        if (!config.isSet("instant-teleport")) {

            config.set("instant-teleport", false);

            changed = true;

        }

        if (!config.isSet("actionbar-cooldown.enabled")) {

            config.set("actionbar-cooldown.enabled", false);

            changed = true;

        }

        if (!config.isSet("actionbar-cooldown.sound")) {

            config.set("actionbar-cooldown.sound", "BLOCK_NOTE_BLOCK_HAT");

            changed = true;

        }

        if (!config.isSet("actionbar-cooldown.volume")) {

            config.set("actionbar-cooldown.volume", 1.0);

            changed = true;

        }

        if (!config.isSet("actionbar-cooldown.pitch")) {

            config.set("actionbar-cooldown.pitch", 1.0);

            changed = true;

        }

        if (!config.isSet("rtp-zones.enabled")) {

            config.set("rtp-zones.enabled", false);

            changed = true;

        }

        if (changed) {

            plugin.saveConfig();

        }

    }



    private GuiItemSettings readGuiItemSettings(

            FileConfiguration config,

            String path,

            int defaultSlot,

            Material defaultMaterial,

            String defaultName,

            List<String> defaultLore

    ) {

        int slot = config.getInt(path + ".slot", defaultSlot);

        if (slot < 0 || slot > 26) {

            plugin.getLogger().warning("Invalid GUI slot " + slot + " at " + path + ", using default " + defaultSlot);

            slot = defaultSlot;

        }



        String name = color(config.getString(path + ".name", defaultName));

        List<String> lore = config.getStringList(path + ".lore");

        if (lore.isEmpty()) {

            lore = defaultLore;

        }

        lore = lore.stream().map(this::color).toList();



        String rawMaterial = config.getString(path + ".material");

        String headDatabaseId = parseHeadDatabaseId(rawMaterial, path + ".material");

        Material material = headDatabaseId != null

                ? defaultMaterial

                : parseMaterial(rawMaterial, defaultMaterial, path + ".material");

        HeadSettings head = readHeadSettings(config.getConfigurationSection(path + ".head"));



        return new GuiItemSettings(slot, name, lore, material, head, headDatabaseId);

    }



    private String parseHeadDatabaseId(String raw, String path) {

        if (raw == null || raw.isBlank()) {

            return null;

        }

        String trimmed = raw.trim();

        if (!trimmed.regionMatches(true, 0, "hdb-", 0, 4) || trimmed.length() <= 4) {

            return null;

        }

        String id = trimmed.substring(4).trim();

        if (id.isEmpty()) {

            plugin.getLogger().warning("Invalid HeadDatabase material at " + path + ", missing ID after hdb- prefix");

            return null;

        }

        return id;

    }



    private HeadSettings readHeadSettings(ConfigurationSection section) {

        if (section == null) {

            return new HeadSettings(null, null, null);

        }

        return new HeadSettings(

                section.getString("texture"),

                section.getString("uuid"),

                section.getString("player")

        );

    }



    private Material parseMaterial(String raw, Material fallback, String path) {

        if (raw == null || raw.isBlank()) {

            return fallback;

        }

        Material material = Material.matchMaterial(raw.trim());

        if (material == null || material.isAir() || !material.isItem()) {

            plugin.getLogger().warning("Invalid material '" + raw + "' at " + path + ", using default " + fallback);

            return fallback;

        }

        return material;

    }



    private TeleportSoundSettings readTeleportSoundSettings(FileConfiguration config) {

        boolean enabled = config.getBoolean("teleport-sound.enabled", true);

        String rawSound = config.getString("teleport-sound.sound", "ENTITY_ENDERMAN_TELEPORT");

        Sound sound = resolveSound(rawSound, DEFAULT_TELEPORT_SOUND, "teleport");

        float volume = clamp(config.getDouble("teleport-sound.volume", 1.0), 0.0, 2.0);

        float pitch = clamp(config.getDouble("teleport-sound.pitch", 1.0), 0.0, 2.0);

        return new TeleportSoundSettings(enabled, sound, volume, pitch);

    }



    private ActionBarCooldownSoundSettings readActionBarCooldownSoundSettings(FileConfiguration config) {

        boolean enabled = config.getBoolean("actionbar-cooldown.enabled", false);

        String rawSound = config.getString("actionbar-cooldown.sound", "BLOCK_NOTE_BLOCK_HAT");

        Sound sound = resolveSound(rawSound, DEFAULT_ACTIONBAR_COOLDOWN_SOUND, "actionbar-cooldown");

        float volume = clamp(config.getDouble("actionbar-cooldown.volume", 1.0), 0.0, 2.0);

        float pitch = clamp(config.getDouble("actionbar-cooldown.pitch", 1.0), 0.0, 2.0);

        return new ActionBarCooldownSoundSettings(enabled, sound, volume, pitch);

    }



    private Sound resolveSound(String raw, Sound fallback, String label) {

        if (raw == null || raw.isBlank()) {

            plugin.getLogger().warning(label + " sound is empty, using default " + fallback);

            return fallback;

        }



        String trimmed = raw.trim();

        try {

            return Sound.valueOf(trimmed.toUpperCase());

        } catch (IllegalArgumentException ignored) {

            // Try registry lookup below.

        }



        NamespacedKey key = NamespacedKey.fromString(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed);

        if (key != null) {

            Sound registrySound = Registry.SOUNDS.get(key);

            if (registrySound != null) {

                return registrySound;

            }

        }



        plugin.getLogger().warning("Invalid " + label + " sound '" + raw + "', using default " + fallback);

        return fallback;

    }



    private float clamp(double value, double min, double max) {

        return (float) Math.max(min, Math.min(max, value));

    }



    private WorldSettings readWorldSettings(FileConfiguration config, String path, String defaultWorld, int defaultRadius, int defaultMinY) {

        String worldName = config.getString(path + ".world-name", defaultWorld);

        int radius = Math.max(1, config.getInt(path + ".radius", defaultRadius));

        int minY = config.getInt(path + ".min-y", defaultMinY);

        return new WorldSettings(worldName, radius, minY);

    }

    private List<RtpZoneSettings> readRtpZones(FileConfiguration config) {

        ConfigurationSection section = config.getConfigurationSection("rtp-zones.zones");

        if (section == null) {

            return List.of();

        }

        List<RtpZoneSettings> zones = new ArrayList<>();

        for (String id : section.getKeys(false)) {

            String path = "rtp-zones.zones." + id;

            boolean enabled = config.getBoolean(path + ".enabled", true);

            String world = config.getString(path + ".world");

            if (world == null || world.isBlank()) {

                plugin.getLogger().warning("RTP zone '" + id + "' is missing world, skipping");

                continue;

            }

            String worldTypeRaw = config.getString(path + ".world-type", "OVERWORLD");

            WorldType worldType;

            try {

                worldType = WorldType.valueOf(worldTypeRaw.trim().toUpperCase());

            } catch (IllegalArgumentException ex) {

                plugin.getLogger().warning("Invalid world-type '" + worldTypeRaw + "' for RTP zone '" + id + "', skipping");

                continue;

            }

            int countdown = Math.max(1, config.getInt(path + ".countdown-seconds", 10));

            double halfSizeX = Math.max(0.5, config.getDouble(path + ".half-size-x", 1.0));

            double halfSizeY = Math.max(0.5, config.getDouble(path + ".half-size-y", 1.0));

            double halfSizeZ = Math.max(0.5, config.getDouble(path + ".half-size-z", 1.0));

            zones.add(new RtpZoneSettings(

                    id,

                    enabled,

                    world.trim(),

                    config.getDouble(path + ".x"),

                    config.getDouble(path + ".y"),

                    config.getDouble(path + ".z"),

                    halfSizeX,

                    halfSizeY,

                    halfSizeZ,

                    countdown,

                    worldType,

                    config.getString(path + ".permission")

            ));

        }

        return List.copyOf(zones);

    }

}


