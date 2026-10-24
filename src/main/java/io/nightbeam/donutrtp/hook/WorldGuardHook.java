package io.nightbeam.donutrtp.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** WorldGuard regions, or nothing when WorldGuard is not installed. WorldGuard types stay inside {@link Access}. */
public final class WorldGuardHook {
    private final Access access;

    private WorldGuardHook(Access access) {
        this.access = access;
    }

    public static WorldGuardHook create(Logger log) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return new WorldGuardHook(null);
        }
        try {
            Access access = new Access();
            access.probe();
            log.info("Hooked into WorldGuard: region zones and region avoidance are on.");
            return new WorldGuardHook(access);
        } catch (Throwable e) {
            log.warning("WorldGuard is installed but could not be hooked: " + e);
            return new WorldGuardHook(null);
        }
    }

    public boolean available() {
        return access != null;
    }

    public boolean regionExists(World world, String region) {
        return access != null && access.region(world, region) != null;
    }

    public boolean inRegion(Location location, String region) {
        return access != null && location.getWorld() != null && access.contains(location, region);
    }

    /** Inside any region except {@code __global__}. */
    public boolean isProtected(Location location) {
        return access != null && location.getWorld() != null && access.anyRegion(location);
    }

    public List<String> regionIds(World world) {
        return access == null || world == null ? List.of() : access.regionIds(world);
    }

    private static final class Access {
        void probe() {
            WorldGuard.getInstance().getPlatform().getRegionContainer();
        }

        RegionManager manager(World world) {
            return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        }

        ProtectedRegion region(World world, String id) {
            RegionManager manager = world == null ? null : manager(world);
            return manager == null || id == null || id.isBlank() ? null : manager.getRegion(id);
        }

        boolean contains(Location location, String id) {
            ProtectedRegion region = region(location.getWorld(), id);
            return region != null
                    && region.contains(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        }

        boolean anyRegion(Location location) {
            RegionManager manager = manager(location.getWorld());
            if (manager == null) {
                return false;
            }
            ApplicableRegionSet set = manager.getApplicableRegions(
                    BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            for (ProtectedRegion region : set) {
                if (!ProtectedRegion.GLOBAL_REGION.equalsIgnoreCase(region.getId())) {
                    return true;
                }
            }
            return false;
        }

        List<String> regionIds(World world) {
            RegionManager manager = manager(world);
            return manager == null ? List.of() : List.copyOf(manager.getRegions().keySet());
        }
    }
}
