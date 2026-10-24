package io.nightbeam.donutrtp.util;

/** Short, readable durations for messages: {@code 45s}, {@code 4m 30s}, {@code 1h 5m}. */
public final class Durations {
    private Durations() {
    }

    public static String format(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        }
        if (minutes > 0) {
            return secs > 0 ? minutes + "m " + secs + "s" : minutes + "m";
        }
        return secs + "s";
    }

    /** Seconds from a whole number of milliseconds, rounded up so "0s left" is never shown while waiting. */
    public static long ceilSeconds(long millis) {
        return millis <= 0 ? 0 : (millis + 999) / 1000;
    }
}
