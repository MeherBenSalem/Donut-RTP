package io.nightbeam.donutrtp.config;

import io.nightbeam.donutrtp.rtp.WorldType;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Validates and builds cuboid RTP zone definitions from config primitives.
 */
public final class RtpZoneParser {

    public static final double MIN_HALF_SIZE = 0.5D;
    public static final int MIN_COUNTDOWN_SECONDS = 1;

    private RtpZoneParser() {
    }

    public static Optional<RtpZoneSettings> parse(
            String id,
            boolean enabled,
            String world,
            String worldTypeRaw,
            double x,
            double y,
            double z,
            double halfSizeX,
            double halfSizeY,
            double halfSizeZ,
            int countdownSeconds,
            String permission,
            Consumer<String> warn
    ) {
        Consumer<String> logger = warn == null ? ignored -> {} : warn;
        if (id == null || id.isBlank()) {
            logger.accept("RTP zone is missing id, skipping");
            return Optional.empty();
        }
        if (world == null || world.isBlank()) {
            logger.accept("RTP zone '" + id + "' is missing world, skipping");
            return Optional.empty();
        }

        Optional<WorldType> worldType = WorldType.fromString(worldTypeRaw);
        if (worldType.isEmpty()) {
            logger.accept("Invalid world-type '" + worldTypeRaw + "' for RTP zone '" + id + "', skipping");
            return Optional.empty();
        }

        String trimmedPermission = permission == null || permission.isBlank() ? null : permission.trim();
        return Optional.of(new RtpZoneSettings(
                id,
                enabled,
                world.trim(),
                x,
                y,
                z,
                Math.max(MIN_HALF_SIZE, halfSizeX),
                Math.max(MIN_HALF_SIZE, halfSizeY),
                Math.max(MIN_HALF_SIZE, halfSizeZ),
                Math.max(MIN_COUNTDOWN_SECONDS, countdownSeconds),
                worldType.get(),
                trimmedPermission
        ));
    }
}
