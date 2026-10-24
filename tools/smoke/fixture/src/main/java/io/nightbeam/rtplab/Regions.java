package io.nightbeam.rtplab;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import org.bukkit.World;

/** WorldGuard regions for the tests. Only loaded when a region command runs. */
final class Regions {
    private Regions() {
    }

    static void create(World world, String id, int[] box) {
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (manager == null) {
            throw new IllegalStateException("WorldGuard has no regions for " + world.getName());
        }
        manager.addRegion(new ProtectedCuboidRegion(id, BlockVector3.at(box[0], box[1], box[2]), BlockVector3.at(box[3], box[4], box[5])));
    }
}
