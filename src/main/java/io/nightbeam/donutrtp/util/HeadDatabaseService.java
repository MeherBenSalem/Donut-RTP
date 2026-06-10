package io.nightbeam.donutrtp.util;

import java.util.Optional;
import java.util.logging.Logger;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class HeadDatabaseService {

    private final Logger logger;
    private HeadDatabaseAPI api;

    public HeadDatabaseService(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
    }

    public Optional<ItemStack> getHead(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        String headId = stripPrefix(id.trim());
        if (headId.isBlank()) {
            logger.warning("Invalid HeadDatabase ID '" + id + "'");
            return Optional.empty();
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("HeadDatabase")) {
            logger.warning("HeadDatabase is not installed; cannot resolve head '" + id + "'");
            return Optional.empty();
        }

        HeadDatabaseAPI headApi = api();
        try {
            ItemStack head = headApi.getItemHead(headId);
            if (head == null) {
                logger.warning("HeadDatabase returned no head for ID '" + headId + "'");
                return Optional.empty();
            }
            return Optional.of(head.clone());
        } catch (NullPointerException ex) {
            logger.warning("HeadDatabase could not find head with ID '" + headId + "'");
            return Optional.empty();
        }
    }

    private HeadDatabaseAPI api() {
        if (api == null) {
            api = new HeadDatabaseAPI();
        }
        return api;
    }

    private static String stripPrefix(String id) {
        if (id.regionMatches(true, 0, "hdb-", 0, 4)) {
            return id.substring(4);
        }
        return id;
    }
}
