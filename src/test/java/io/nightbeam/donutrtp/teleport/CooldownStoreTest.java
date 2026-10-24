package io.nightbeam.donutrtp.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nightbeam.donutrtp.config.Settings;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CooldownStoreTest {
    private static final Logger LOG = Logger.getLogger("test");

    @TempDir
    Path dir;

    @Test
    void countsDownAndSurvivesARestart() {
        AtomicLong now = new AtomicLong(1_000_000);
        File file = dir.resolve("data/cooldowns.yml").toFile();
        CooldownStore store = new CooldownStore(file, now::get);
        UUID player = UUID.randomUUID();
        store.start(player, CooldownStore.SHARED, 300);
        store.start(player, CooldownStore.zoneKey("spawn.pad"), 30);
        now.addAndGet(100_000);
        assertEquals(200_000, store.remainingMillis(player, CooldownStore.SHARED));
        store.saveIfDirty(LOG);
        assertTrue(file.exists());

        CooldownStore reloaded = new CooldownStore(file, now::get);
        reloaded.load(LOG);
        assertEquals(200_000, reloaded.remainingMillis(player, CooldownStore.SHARED));
        // Zone 'spawn.pad' expired at +30s, so it is gone.
        assertEquals(0, reloaded.remainingMillis(player, CooldownStore.zoneKey("spawn.pad")));
    }

    @Test
    void keysWithDotsAndColonsRoundTrip() {
        AtomicLong now = new AtomicLong(0);
        File file = dir.resolve("cooldowns.yml").toFile();
        CooldownStore store = new CooldownStore(file, now::get);
        UUID player = UUID.randomUUID();
        store.start(player, CooldownStore.zoneKey("a.b:c"), 60);
        store.saveIfDirty(LOG);
        CooldownStore reloaded = new CooldownStore(file, now::get);
        reloaded.load(LOG);
        assertEquals(60_000, reloaded.remainingMillis(player, CooldownStore.zoneKey("a.b:c")));
    }

    @Test
    void lowestPermissionTierWins() {
        Map<String, Integer> tiers = new LinkedHashMap<>();
        tiers.put("vip", 120);
        tiers.put("mvp", 60);
        Settings.Cooldowns cooldowns = new Settings.Cooldowns(300, true, tiers);
        assertEquals(300, cooldowns.secondsFor(p -> false, -1));
        assertEquals(120, cooldowns.secondsFor(Set.of("vip")::contains, -1));
        assertEquals(60, cooldowns.secondsFor(Set.of("vip", "mvp")::contains, -1));
        // A destination's own cooldown replaces the global one; tiers still lower it.
        assertEquals(30, cooldowns.secondsFor(p -> false, 30));
        assertEquals(30, cooldowns.secondsFor(Set.of("vip")::contains, 30));
        assertEquals(0, cooldowns.secondsFor(Set.of("mvp")::contains, 0));
    }
}
