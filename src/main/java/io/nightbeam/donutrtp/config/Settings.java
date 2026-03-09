package io.nightbeam.donutrtp.config;

import io.nightbeam.donutrtp.rtp.WorldType;
import java.util.Map;

public record Settings(
        int warmupSeconds,
        int cooldownSeconds,
        int maxAttempts,
        boolean fillEmptySlots,
        Map<WorldType, WorldSettings> worlds
) {
}
