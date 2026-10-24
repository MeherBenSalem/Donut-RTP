package io.nightbeam.donutrtp.util;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Asks Modrinth for the newest release. Network work only; the plugin decides threads and messages. */
public final class UpdateChecker {
    public static final String PAGE = "https://modrinth.com/plugin/donut-rtp-and-rtp-zone";
    private static final String API = "https://api.modrinth.com/v2/project/donut-rtp-and-rtp-zone/version?version_type=release";
    private static final Pattern VERSION = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");

    private UpdateChecker() {
    }

    /** The highest release version on Modrinth, or null. Blocks; call it off the main thread. */
    public static String latest(String userAgent) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(API).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", userAgent);
        if (connection.getResponseCode() != 200) {
            return null;
        }
        try (InputStream in = connection.getInputStream()) {
            return highestVersion(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static String highestVersion(String json) {
        Matcher matcher = VERSION.matcher(json == null ? "" : json);
        String highest = null;
        while (matcher.find()) {
            if (highest == null || compare(matcher.group(1), highest) > 0) {
                highest = matcher.group(1);
            }
        }
        return highest;
    }

    /** Compares dotted versions by number: 2.0.10 is newer than 2.0.9; suffixes like -beta are ignored. */
    public static int compare(String a, String b) {
        String[] left = clean(a).split("\\.");
        String[] right = clean(b).split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length ? number(left[i]) : 0;
            int r = i < right.length ? number(right[i]) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static String clean(String version) {
        String v = version == null ? "0" : version.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        int cut = v.indexOf('-');
        if (cut < 0) {
            cut = v.indexOf('+');
        }
        return cut > 0 ? v.substring(0, cut) : v;
    }

    private static int number(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Integer.parseInt(part.substring(0, end));
    }
}
