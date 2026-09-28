package io.nightbeam.donutrtp.config;

import io.nightbeam.donutrtp.rtp.WorldType;
import org.bukkit.Location;

public record RtpZoneSettings(
        String id,
        boolean enabled,
        String worldName,
        double centerX,
        double centerY,
        double centerZ,
        double halfSizeX,
        double halfSizeY,
        double halfSizeZ,
        int countdownSeconds,
        WorldType worldType,
        String permission
) {

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return contains(location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
    }

    public boolean contains(String world, double x, double y, double z) {
        if (world == null || !world.equals(worldName)) {
            return false;
        }
        return Math.abs(x - centerX) <= halfSizeX
                && Math.abs(y - centerY) <= halfSizeY
                && Math.abs(z - centerZ) <= halfSizeZ;
    }

    public boolean hasPermission() {
        return permission != null && !permission.isBlank();
    }
}
