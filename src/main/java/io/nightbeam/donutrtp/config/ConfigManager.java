package io.nightbeam.donutrtp.config;

import io.nightbeam.donutrtp.rtp.WorldType;
import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration messagesConfig;

    private volatile Settings cachedSettings;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        FileConfiguration config = plugin.getConfig();
        int warmup = Math.max(0, config.getInt("warmup-seconds", 5));
        int cooldown = Math.max(0, config.getInt("cooldown-seconds", 30));
        int maxAttempts = Math.max(1, config.getInt("max-attempts", 30));
        boolean fillEmpty = config.getBoolean("gui.fill-empty-slots", true);

        Map<WorldType, WorldSettings> worlds = new EnumMap<>(WorldType.class);
        worlds.put(WorldType.OVERWORLD, readWorldSettings(config, "worlds.overworld", "world", 5000, 60));
        worlds.put(WorldType.NETHER, readWorldSettings(config, "worlds.nether", "world_nether", 4000, 40));
        worlds.put(WorldType.END, readWorldSettings(config, "worlds.end", "world_the_end", 3000, 50));

        this.cachedSettings = new Settings(warmup, cooldown, maxAttempts, fillEmpty, worlds);
    }

    public Settings settings() {
        return Objects.requireNonNull(cachedSettings, "Settings not loaded yet");
    }

    public String message(String key) {
        String prefix = color(messagesConfig.getString("prefix", "&6&lRTP &8» "));
        String value = color(messagesConfig.getString(key, ""));
        return prefix + value;
    }

    public List<String> messageList(String key) {
        return messagesConfig.getStringList(key).stream().map(this::color).toList();
    }

    public String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private WorldSettings readWorldSettings(FileConfiguration config, String path, String defaultWorld, int defaultRadius, int defaultMinY) {
        String worldName = config.getString(path + ".world-name", defaultWorld);
        int radius = Math.max(1, config.getInt(path + ".radius", defaultRadius));
        int minY = config.getInt(path + ".min-y", defaultMinY);
        return new WorldSettings(worldName, radius, minY);
    }
}
