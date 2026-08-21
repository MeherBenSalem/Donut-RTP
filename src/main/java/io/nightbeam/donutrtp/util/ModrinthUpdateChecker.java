package io.nightbeam.donutrtp.util;

import io.nightbeam.donutrtp.DonutRTPPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks Modrinth for a newer DonutRTP release on plugin startup.
 */
public final class ModrinthUpdateChecker {

    private static final String PROJECT_SLUG = "donut-rtp-and-rtp-zone";
    private static final String MODRINTH_URL = "https://modrinth.com/plugin/" + PROJECT_SLUG;
    private static final String VERSIONS_ENDPOINT =
            "https://api.modrinth.com/v2/project/" + PROJECT_SLUG
                    + "/version?version_type=release&limit=20";

    private ModrinthUpdateChecker() {}

    public static void checkAsync(DonutRTPPlugin plugin) {
        if (!plugin.getConfig().getBoolean("update-check.enabled", true)) {
            return;
        }

        String currentVersion = plugin.getDescription().getVersion();
        plugin.getFoliaCompat().runAsync(() -> {
            try {
                String latestVersion = fetchLatestVersion(plugin);
                if (latestVersion == null) {
                    return;
                }

                int comparison = compareVersions(latestVersion, currentVersion);
                plugin.getFoliaCompat().runLaterGlobal(() -> {
                    if (comparison > 0) {
                        plugin.setUpdateAvailable(latestVersion, MODRINTH_URL);
                        plugin.getLogger().warning(
                                "[DonutRTP] A new version is available: "
                                        + latestVersion + " (running " + currentVersion + "). "
                                        + "Download: " + MODRINTH_URL);
                    } else {
                        plugin.clearUpdateAvailable();
                        plugin.getLogger().info(
                                "[DonutRTP] Running the latest version (" + currentVersion + ").");
                    }
                }, 1L);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE,
                        "[DonutRTP] Could not check Modrinth for updates.", e);
            }
        });
    }

    private static String fetchLatestVersion(DonutRTPPlugin plugin) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(VERSIONS_ENDPOINT).toURL()
                .openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        connection.setRequestProperty("User-Agent",
                "DonutRTP/" + plugin.getDescription().getVersion()
                        + " (" + MODRINTH_URL + ")");

        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            plugin.getLogger().fine("[DonutRTP] Modrinth update check returned HTTP " + status + ".");
            return null;
        }

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }

        return pickNewestVersionNumber(body.toString());
    }

    static String pickNewestVersionNumber(String json) {
        List<VersionEntry> entries = parseVersionEntries(json);
        if (entries.isEmpty()) {
            return null;
        }
        entries.sort((a, b) -> b.datePublished.compareTo(a.datePublished));
        return entries.get(0).versionNumber;
    }

    static List<VersionEntry> parseVersionEntries(String json) {
        List<VersionEntry> entries = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return entries;
        }
        Pattern number = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");
        Pattern date = Pattern.compile("\"date_published\"\\s*:\\s*\"([^\"]+)\"");
        Matcher numMatcher = number.matcher(json);
        while (numMatcher.find()) {
            String object = enclosingObject(json, numMatcher.start());
            if (object == null) {
                continue;
            }
            Matcher versionInObject = number.matcher(object);
            Matcher dateInObject = date.matcher(object);
            if (versionInObject.find() && dateInObject.find()) {
                entries.add(new VersionEntry(versionInObject.group(1), dateInObject.group(1)));
            }
        }
        return entries;
    }

    static String enclosingObject(String json, int index) {
        int depth = 0;
        int start = -1;
        for (int i = index; i >= 0; i--) {
            char c = json.charAt(i);
            if (c == '}') {
                depth++;
            } else if (c == '{') {
                if (depth == 0) {
                    start = i;
                    break;
                }
                depth--;
            }
        }
        if (start < 0) {
            return null;
        }
        depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    static int compareVersions(String a, String b) {
        String[] left = normalizeVersion(a).split("\\.");
        String[] right = normalizeVersion(b).split("\\.");
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int leftPart = i < left.length ? parseVersionPart(left[i]) : 0;
            int rightPart = i < right.length ? parseVersionPart(right[i]) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    static String normalizeVersion(String version) {
        if (version == null) {
            return "0";
        }
        String trimmed = version.trim();
        int plus = trimmed.indexOf('+');
        if (plus >= 0) {
            trimmed = trimmed.substring(0, plus);
        }
        int hyphen = trimmed.indexOf('-');
        if (hyphen > 0) {
            trimmed = trimmed.substring(0, hyphen);
        }
        return trimmed;
    }

    private static int parseVersionPart(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        return Integer.parseInt(part.substring(0, end));
    }

    static final class VersionEntry {
        final String versionNumber;
        final String datePublished;

        VersionEntry(String versionNumber, String datePublished) {
            this.versionNumber = versionNumber;
            this.datePublished = datePublished == null ? "" : datePublished;
        }
    }
}
