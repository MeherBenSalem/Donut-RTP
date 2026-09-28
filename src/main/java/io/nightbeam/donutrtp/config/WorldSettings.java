package io.nightbeam.donutrtp.config;

public record WorldSettings(String worldName, int radius, int minY) {
    public WorldSettings {
        radius = Math.max(1, radius);
    }
}
