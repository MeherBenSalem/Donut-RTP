package io.nightbeam.donutrtp.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/** GriefPrevention claims, read through reflection so no GriefPrevention jar is needed to build or run. */
public final class GriefPreventionHook {
    private final Object dataStore;
    private final Method getClaimAt;
    private final int extraBooleans;

    private GriefPreventionHook(Object dataStore, Method getClaimAt, int extraBooleans) {
        this.dataStore = dataStore;
        this.getClaimAt = getClaimAt;
        this.extraBooleans = extraBooleans;
    }

    public static GriefPreventionHook create(Logger log) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (plugin == null || !plugin.isEnabled()) {
            return new GriefPreventionHook(null, null, 0);
        }
        try {
            Field storeField = plugin.getClass().getField("dataStore");
            Object store = storeField.get(plugin);
            for (Method method : store.getClass().getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (!method.getName().equals("getClaimAt") || params.length < 3 || params[0] != Location.class) {
                    continue;
                }
                // getClaimAt(Location, boolean ignoreHeight, Claim cached) or (Location, boolean, boolean, Claim).
                boolean shape = params[params.length - 1].getSimpleName().equals("Claim");
                for (int i = 1; i < params.length - 1; i++) {
                    shape &= params[i] == boolean.class;
                }
                if (shape) {
                    log.info("Hooked into GriefPrevention: random teleports avoid claims.");
                    return new GriefPreventionHook(store, method, params.length - 2);
                }
            }
            log.warning("GriefPrevention is installed but its claim lookup was not found; claims are not avoided.");
        } catch (Throwable e) {
            log.warning("GriefPrevention is installed but could not be hooked: " + e);
        }
        return new GriefPreventionHook(null, null, 0);
    }

    public boolean available() {
        return getClaimAt != null;
    }

    public boolean isClaimed(Location location) {
        if (getClaimAt == null) {
            return false;
        }
        Object[] args = new Object[extraBooleans + 2];
        args[0] = location;
        for (int i = 1; i <= extraBooleans; i++) {
            args[i] = i == 1; // ignoreHeight = true, then false for any newer flag
        }
        args[args.length - 1] = null;
        try {
            return getClaimAt.invoke(dataStore, args) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
