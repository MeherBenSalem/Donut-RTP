package io.nightbeam.donutrtp.rtp;

import java.util.Locale;
import java.util.Optional;

public enum WorldType {
    OVERWORLD,
    NETHER,
    END;

    public static Optional<WorldType> fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
