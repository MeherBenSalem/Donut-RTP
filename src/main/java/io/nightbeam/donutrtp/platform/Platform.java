package io.nightbeam.donutrtp.platform;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * What the running server can do. The plugin is compiled against the Paper 1.20.1 API, but Paper-only methods are
 * only called when these flags say they exist, so the same jar runs on Bukkit and Spigot.
 */
public final class Platform {
    private static final boolean FOLIA = hasClass("io.papermc.paper.threadedregions.RegionizedServer");
    private static final boolean ASYNC_CHUNKS = hasMethod(World.class, "getChunkAtAsync", int.class, int.class, boolean.class);
    private static final boolean ASYNC_TELEPORT = hasMethod(Entity.class, "teleportAsync", Location.class);
    private static final boolean ADVENTURE = hasClass("net.kyori.adventure.text.Component")
            && hasClass("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer")
            && hasMethod(org.bukkit.entity.Player.class, "sendActionBar", classOrNull("net.kyori.adventure.text.Component"));

    private Platform() {
    }

    /** Folia: regionised threads, no main thread. */
    public static boolean folia() {
        return FOLIA;
    }

    /** Paper's async chunk loading ({@code World#getChunkAtAsync}). */
    public static boolean asyncChunks() {
        return ASYNC_CHUNKS;
    }

    /** Paper's {@code Entity#teleportAsync}. */
    public static boolean asyncTeleport() {
        return ASYNC_TELEPORT;
    }

    /** Adventure components on players (Paper and forks). */
    public static boolean adventure() {
        return ADVENTURE;
    }

    public static String describe() {
        return (FOLIA ? "Folia" : ASYNC_CHUNKS ? "Paper" : "Bukkit/Spigot")
                + " (async chunks: " + ASYNC_CHUNKS + ", async teleport: " + ASYNC_TELEPORT + ")";
    }

    private static boolean hasClass(String name) {
        return classOrNull(name) != null;
    }

    private static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name, false, Platform.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static boolean hasMethod(Class<?> owner, String name, Class<?>... parameters) {
        for (Class<?> parameter : parameters) {
            if (parameter == null) {
                return false;
            }
        }
        try {
            owner.getMethod(name, parameters);
            return true;
        } catch (NoSuchMethodException | LinkageError e) {
            return false;
        }
    }
}
