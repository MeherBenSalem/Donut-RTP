package io.nightbeam.donutrtp.config;

import io.nightbeam.donutrtp.platform.Text;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * messages.yml with the jar's copy as defaults. Placeholders are written {@code {name}} (1.x's {@code %name%} still
 * works). An empty message is never sent.
 */
public final class Messages {
    private final Map<String, String> templates;
    private final String prefix;

    private Messages(Map<String, String> templates) {
        this.templates = templates;
        this.prefix = Text.color(templates.getOrDefault("prefix", ""));
    }

    public static Messages load(Plugin plugin) {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the default messages: " + e.getMessage());
        }
        File file = new File(plugin.getDataFolder(), "messages.yml");
        YamlConfiguration custom = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        Map<String, String> templates = new HashMap<>();
        for (String key : defaults.getKeys(false)) {
            templates.put(key, defaults.getString(key, ""));
        }
        for (String key : custom.getKeys(false)) {
            if (custom.isString(key)) {
                templates.put(key, custom.getString(key, ""));
            }
        }
        return new Messages(templates);
    }

    public static Messages of(Map<String, String> templates) {
        return new Messages(new HashMap<>(templates));
    }

    /** Coloured text without the prefix, for action bars, titles and boss bars. */
    public String raw(String key, Object... placeholders) {
        return Text.color(format(templates.getOrDefault(key, ""), placeholders));
    }

    /** Coloured chat line with the prefix, or "" when the message is turned off. */
    public String chat(String key, Object... placeholders) {
        String body = raw(key, placeholders);
        return body.isEmpty() ? "" : prefix + body;
    }

    public void send(CommandSender to, String key, Object... placeholders) {
        Text.send(to, chat(key, placeholders));
    }

    /** Replaces {@code {name}} and {@code %name%}; placeholders come in name, value pairs. */
    public static String format(String template, Object... placeholders) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String out = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String name = String.valueOf(placeholders[i]);
            String value = String.valueOf(placeholders[i + 1]);
            out = out.replace("{" + name + "}", value).replace("%" + name + "%", value);
        }
        return out;
    }
}
