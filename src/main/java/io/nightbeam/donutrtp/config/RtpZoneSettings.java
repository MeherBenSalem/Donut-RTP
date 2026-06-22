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
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }
        return Math.abs(location.getX() - centerX) <= halfSizeX
                && Math.abs(location.getY() - centerY) <= halfSizeY
                && Math.abs(location.getZ() - centerZ) <= halfSizeZ;
    }

    public boolean hasPermission() {
        return permission != null && !permission.isBlank();
    }
}
