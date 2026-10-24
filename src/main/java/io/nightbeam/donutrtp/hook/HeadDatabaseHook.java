package io.nightbeam.donutrtp.hook;

import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** HeadDatabase heads for menu icons ({@code material: "hdb-1234"}). Its API is only touched when it is installed. */
public final class HeadDatabaseHook {
    private final Logger log;

    public HeadDatabaseHook(Logger log) {
        this.log = log;
    }

    public static boolean isHeadId(String material) {
        return material != null && material.regionMatches(true, 0, "hdb-", 0, 4);
    }

    /** The head, or null when HeadDatabase is missing or does not know the id. */
    public ItemStack head(String material) {
        String id = material.substring(4).trim();
        if (id.isEmpty() || !Bukkit.getPluginManager().isPluginEnabled("HeadDatabase")) {
            return null;
        }
        try {
            ItemStack head = Access.head(id);
            return head == null ? null : head.clone();
        } catch (Throwable e) {
            log.fine("HeadDatabase has no head " + id + ": " + e);
            return null;
        }
    }

    private static final class Access {
        static ItemStack head(String id) {
            return new me.arcaniax.hdb.api.HeadDatabaseAPI().getItemHead(id);
        }
    }
}
