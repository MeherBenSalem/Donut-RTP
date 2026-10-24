package io.nightbeam.donutrtp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nightbeam.donutrtp.destination.Destination;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigTest {
    private static final Logger LOG = Logger.getLogger("test");

    static String resource(String name) throws Exception {
        try (InputStream in = ConfigTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void defaultConfigReads() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(resource("config.yml"));
        assertFalse(ConfigMigration.isLegacy(yaml));
        Settings settings = ConfigLoader.read(yaml, LOG);
        assertEquals(5, settings.warmup().seconds());
        assertEquals(300, settings.cooldowns().seconds());
        assertEquals(Map.of("donutrtp.cooldown.vip", 120, "donutrtp.cooldown.mvp", 60), settings.cooldowns().permissions());
        // Written back by the 1.x upgrade, the ranks stay flat (permission names with dots would nest).
        assertTrue(yaml.saveToString().contains("  ranks:\n    vip: 120\n"));
        assertEquals(List.of("overworld", "nether", "end"), List.copyOf(settings.destinations().keySet()));
        Destination overworld = settings.destinations().get("overworld");
        assertEquals("world", overworld.worldName());
        assertEquals(300, overworld.minRadius());
        assertEquals(5000, overworld.maxRadius());
        assertFalse(overworld.allowsBiome("minecraft:deep_ocean"));
        assertTrue(overworld.allowsBiome("minecraft:plains"));
        assertEquals(11, overworld.item().slot());
        assertEquals("Overworld", overworld.displayName());
        Destination end = settings.destinations().get("end");
        assertEquals(Destination.Shape.CIRCLE, end.shape());
        assertEquals("minecraft:entity.enderman.teleport", settings.landing().sound().key());
    }

    @Test
    void badValuesFallBackInsteadOfFailing() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("search.max-attempts", -4);
        yaml.set("menu.rows", 9);
        yaml.set("destinations.wild.world", "wild");
        yaml.set("destinations.wild.shape", "TRIANGLE");
        yaml.set("destinations.wild.min-radius", 500);
        yaml.set("destinations.wild.max-radius", 100);
        yaml.set("destinations.wild.min-y", 200);
        yaml.set("destinations.wild.max-y", 50);
        yaml.set("destinations.wild.biomes.allow", List.of("Plains", "minecraft:forest", "#minecraft:is_ocean"));
        yaml.set("destinations.wild.gui.slot", 80);
        Settings settings = ConfigLoader.read(yaml, LOG);
        assertEquals(1, settings.search().maxAttempts());
        assertEquals(6, settings.menu().rows());
        Destination wild = settings.destinations().get("wild");
        assertEquals(Destination.Shape.SQUARE, wild.shape());
        assertEquals(516, wild.maxRadius());
        assertEquals(50, wild.minY());
        assertEquals(200, wild.maxY());
        assertTrue(wild.allowsBiome("minecraft:plains"));
        assertTrue(wild.allowsBiome("minecraft:forest"));
        assertFalse(wild.allowsBiome("minecraft:desert"));
        assertTrue(wild.item().hidden());
    }

    @Test
    void migratesA1xConfig() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString(resource("legacy/config-1.5.2.yml"));
        assertTrue(ConfigMigration.isLegacy(old));
        ConfigMigration.Result result = ConfigMigration.migrate(old, resource("config.yml"));
        YamlConfiguration config = result.config();
        assertEquals(2, config.getInt("config-version"));
        assertEquals(3, config.getInt("warmup.seconds"));
        assertEquals(600, config.getInt("cooldown.seconds"));
        assertEquals(25, config.getInt("search.max-attempts"));
        assertEquals("BLOCK_NOTE_BLOCK_HAT", config.getString("warmup.tick-sound"));
        assertEquals("", config.getString("landing.sound"));
        assertFalse(config.getBoolean("menu.fill-empty-slots"));
        assertEquals("survival", config.getString("destinations.overworld.world"));
        assertEquals(8000, config.getInt("destinations.overworld.max-radius"));
        assertEquals(64, config.getInt("destinations.overworld.min-y"));
        assertEquals(100, config.getInt("destinations.nether.min-radius"));
        assertEquals(750, config.getInt("destinations.end.min-radius"), "min radius is capped at a quarter of 3000");
        assertEquals(12, config.getInt("destinations.overworld.gui.slot"));
        assertEquals("&aWild", config.getString("destinations.overworld.gui.name"));
        assertEquals("3b3125891576299764df12836f0b287cd85b9fbb529b2ff7bceb784d48cba",
                config.getString("destinations.nether.gui.head.texture"));
        assertFalse(config.getBoolean("update-check.enabled"));

        // The migrated config reads cleanly.
        Settings settings = ConfigLoader.read(config, LOG);
        assertEquals(3, settings.destinations().size());

        ConfigurationSection zones = result.zones().getConfigurationSection("zones");
        assertNotNull(zones);
        assertEquals(List.of("spawn_exit", "spawn_pad", "arena"), List.copyOf(zones.getKeys(false)));
        assertEquals("nether", zones.getString("spawn_exit.destination"));
        assertEquals("spawn_exit", zones.getString("spawn_exit.area.region"));
        assertEquals("INDIVIDUAL", zones.getString("spawn_exit.mode"));
        assertEquals(7, zones.getInt("spawn_exit.countdown"));
        assertEquals(120, zones.getInt("spawn_exit.cooldown"));
        assertTrue(zones.getBoolean("spawn_exit.enabled"));
        assertEquals(List.of(-3, 62, -3), zones.getIntegerList("spawn_pad.area.min"));
        assertEquals(List.of(3, 66, 3), zones.getIntegerList("spawn_pad.area.max"));
        assertEquals("end", zones.getString("spawn_pad.destination"));
        assertEquals("donutrtp.zone.pad", zones.getString("spawn_pad.permission"));
        assertFalse(zones.getBoolean("arena.enabled"), "disabled in 1.x stays disabled");

        Map<String, io.nightbeam.donutrtp.zone.Zone> read = io.nightbeam.donutrtp.zone.ZoneStoreAccess.read(zones);
        assertEquals(3, read.size());
    }
}
