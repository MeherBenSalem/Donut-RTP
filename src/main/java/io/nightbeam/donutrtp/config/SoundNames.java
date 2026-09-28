package io.nightbeam.donutrtp.config;

import java.util.List;
import java.util.Locale;

/**
 * Builds Bukkit/Paper sound lookup keys from config strings.
 */
final class SoundNames {

    private SoundNames() {
    }

    static List<String> keyCandidates(String trimmed) {
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains(":")) {
            return List.of(lower);
        }
        String dotted = lower.replace('_', '.');
        if (!dotted.equals(lower)) {
            return List.of("minecraft:" + dotted, "minecraft:" + lower);
        }
        return List.of("minecraft:" + lower);
    }

    static String enumName(String trimmed) {
        String enumName = trimmed.contains(":")
                ? trimmed.substring(trimmed.indexOf(':') + 1)
                : trimmed;
        return enumName.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }
}
