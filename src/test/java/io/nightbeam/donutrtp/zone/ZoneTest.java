package io.nightbeam.donutrtp.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ZoneTest {
    @Test
    void cuboidOrdersCornersAndIncludesBothEnds() {
        ZoneArea.Cuboid box = ZoneArea.Cuboid.of("world", 3, 66, 3, -3, 64, -3);
        assertEquals(-3, box.minX());
        assertEquals(66, box.maxY());
        assertTrue(box.contains("world", -3.0, 64.0, -3.0));
        assertTrue(box.contains("world", 3.99, 64.0, 3.99));
        assertFalse(box.contains("world", 4.0, 64.0, 0.0));
        assertFalse(box.contains("world", -3.01, 64.0, 0.0));
        assertFalse(box.contains("world_nether", 0.0, 64.0, 0.0));
    }

    @Test
    void jumpingAtTheTopStillCounts() {
        ZoneArea.Cuboid box = ZoneArea.Cuboid.of("world", 0, 64, 0, 2, 64, 2);
        assertTrue(box.contains("world", 1.5, 65.25, 1.5));
        assertFalse(box.contains("world", 1.5, 66.0, 1.5));
        assertFalse(box.contains("world", 1.5, 63.9, 1.5));
    }

    @Test
    void zonesSurviveASaveAndLoad() {
        Zone pad = Zone.create("pad", "overworld", ZoneArea.Cuboid.of("world", -2, 64, -2, 2, 66, 2));
        Zone.Builder edit = pad.edit();
        edit.mode = Zone.Mode.INDIVIDUAL;
        edit.countdown = 5;
        edit.permission = "rtp.pad";
        Zone changed = edit.build();
        Zone region = Zone.create("arena", "nether", new ZoneArea.Region("world", "arena_exit"));

        YamlConfiguration yaml = new YamlConfiguration();
        ZoneStore.write(yaml.createSection("zones.pad"), changed);
        ZoneStore.write(yaml.createSection("zones.arena"), region);
        Map<String, Zone> read = ZoneStore.read(yaml.getConfigurationSection("zones"), Logger.getLogger("test"));
        assertEquals(changed, read.get("pad"));
        assertEquals(region, read.get("arena"));
    }

    @Test
    void aBrokenFileIsNeverOverwritten(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.io.File file = dir.resolve("zones.yml").toFile();
        String broken = "zones:\n  pad:\n    destination: [overworld\n";
        java.nio.file.Files.writeString(file.toPath(), broken);
        ZoneStore store = new ZoneStore(file);
        org.junit.jupiter.api.Assertions.assertThrows(org.bukkit.configuration.InvalidConfigurationException.class,
                () -> store.load(Logger.getLogger("test")));
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () -> store.save(Map.of()));
        assertEquals(broken, java.nio.file.Files.readString(file.toPath()));

        java.nio.file.Files.writeString(file.toPath(), "zones: {}\n");
        assertTrue(store.load(Logger.getLogger("test")).isEmpty());
        store.save(Map.of("pad", Zone.create("pad", "overworld", ZoneArea.Cuboid.of("world", 0, 64, 0, 2, 65, 2))));
        assertEquals(1, store.load(Logger.getLogger("test")).size());
    }

    @Test
    void zonesWithoutAnAreaAreSkipped() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("zones.broken.destination", "overworld");
        yaml.set("zones.half.area.world", "world");
        yaml.set("zones.half.area.min", java.util.List.of(1, 2));
        Map<String, Zone> read = ZoneStore.read(yaml.getConfigurationSection("zones"), Logger.getLogger("test"));
        assertTrue(read.isEmpty());
    }
}
