package io.nightbeam.donutrtp.zone;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reads and writes zones.yml. */
public final class ZoneStore {
    private final File file;
    /** Set while zones.yml cannot be read, so saving never overwrites the zones a typo is hiding. */
    private volatile boolean unreadable;

    public ZoneStore(File file) {
        this.file = file;
    }

    /** Reads zones.yml; throws when it is not valid YAML, and refuses to save over it until it reads again. */
    public Map<String, Zone> load(Logger log) throws InvalidConfigurationException, IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (InvalidConfigurationException | IOException e) {
                unreadable = true;
                throw e;
            }
        }
        unreadable = false;
        return read(yaml.getConfigurationSection("zones"), log);
    }

    static Map<String, Zone> read(ConfigurationSection zones, Logger log) {
        Map<String, Zone> out = new LinkedHashMap<>();
        if (zones == null) {
            return out;
        }
        for (String id : zones.getKeys(false)) {
            ConfigurationSection s = zones.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            Zone zone = zone(id.toLowerCase(Locale.ROOT), s, log);
            if (zone != null) {
                out.put(zone.id(), zone);
            }
        }
        return out;
    }

    static Zone zone(String id, ConfigurationSection s, Logger log) {
        ConfigurationSection area = s.getConfigurationSection("area");
        if (area == null) {
            log.warning("Zone '" + id + "' has no area. It is skipped.");
            return null;
        }
        String world = area.getString("world", "world");
        ZoneArea zoneArea;
        String region = area.getString("region", "");
        if (region != null && !region.isBlank()) {
            zoneArea = new ZoneArea.Region(world, region.trim());
        } else {
            List<Integer> min = area.getIntegerList("min");
            List<Integer> max = area.getIntegerList("max");
            if (min.size() != 3 || max.size() != 3) {
                log.warning("Zone '" + id + "': area needs a region, or min and max as [x, y, z]. It is skipped.");
                return null;
            }
            zoneArea = ZoneArea.Cuboid.of(world, min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2));
        }
        Zone.Mode mode;
        try {
            mode = Zone.Mode.valueOf(s.getString("mode", "GROUP").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Zone '" + id + "': mode must be GROUP or INDIVIDUAL. Using GROUP.");
            mode = Zone.Mode.GROUP;
        }
        return new Zone(id,
                s.getBoolean("enabled", true),
                s.getString("destination", "overworld").toLowerCase(Locale.ROOT),
                mode,
                Math.max(0, s.getInt("countdown", 10)),
                Math.max(1, s.getInt("min-players", 1)),
                Math.max(0, Math.min(64, s.getInt("spread", 3))),
                Math.max(0, s.getInt("cooldown", 30)),
                s.getBoolean("cancel-on-move", false),
                s.getString("permission", "").trim(),
                s.getBoolean("display.title", true),
                s.getBoolean("display.actionbar", true),
                s.getBoolean("display.bossbar", true),
                zoneArea);
    }

    /** Writes the zones, keeping the comments at the top of the file. */
    public void save(Map<String, Zone> zones) throws IOException {
        if (unreadable) {
            throw new IOException("zones.yml has a mistake; fix it and /rtpadmin reload before changing zones");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (InvalidConfigurationException e) {
                throw new IOException("zones.yml has a mistake: " + e.getMessage(), e);
            }
        }
        yaml.set("zones", null);
        ConfigurationSection root = yaml.createSection("zones");
        for (Zone zone : zones.values()) {
            write(root.createSection(zone.id()), zone);
        }
        yaml.save(file);
    }

    static void write(ConfigurationSection s, Zone zone) {
        s.set("enabled", zone.enabled());
        s.set("destination", zone.destination());
        s.set("mode", zone.mode().name());
        s.set("countdown", zone.countdown());
        s.set("min-players", zone.minPlayers());
        s.set("spread", zone.spread());
        s.set("cooldown", zone.cooldown());
        s.set("cancel-on-move", zone.cancelOnMove());
        if (!zone.permission().isEmpty()) {
            s.set("permission", zone.permission());
        }
        s.set("display.title", zone.showTitle());
        s.set("display.actionbar", zone.showActionbar());
        s.set("display.bossbar", zone.showBossbar());
        s.set("area.world", zone.area().world());
        if (zone.area() instanceof ZoneArea.Region region) {
            s.set("area.region", region.region());
        } else if (zone.area() instanceof ZoneArea.Cuboid box) {
            s.set("area.min", new ArrayList<>(List.of(box.minX(), box.minY(), box.minZ())));
            s.set("area.max", new ArrayList<>(List.of(box.maxX(), box.maxY(), box.maxZ())));
        }
    }
}
