package io.nightbeam.donutrtp.hook;

import io.nightbeam.donutrtp.DonutRTPPlugin;
import io.nightbeam.donutrtp.destination.Destination;
import io.nightbeam.donutrtp.teleport.CooldownStore;
import io.nightbeam.donutrtp.util.Durations;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI placeholders. Only loaded when PlaceholderAPI is installed.
 * <ul>
 *     <li>{@code %donutrtp_cooldown%}, {@code %donutrtp_cooldown_seconds%}: the shared /rtp cooldown, or the longest
 *     one when each destination has its own</li>
 *     <li>{@code %donutrtp_cooldown_<destination>%}, {@code %donutrtp_cooldown_seconds_<destination>%}</li>
 *     <li>{@code %donutrtp_zone%}: the zone the player stands in</li>
 *     <li>{@code %donutrtp_zone_<name>_countdown%}, {@code %donutrtp_zone_<name>_players%}</li>
 * </ul>
 */
public final class PlaceholderHook extends PlaceholderExpansion {
    private final DonutRTPPlugin plugin;

    public PlaceholderHook(DonutRTPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "donutrtp";
    }

    @Override
    public String getAuthor() {
        return "NightBeam";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        if (key.startsWith("zone_")) {
            return zone(key.substring(5));
        }
        if (player == null) {
            return "";
        }
        if (key.equals("zone")) {
            String zone = plugin.zones().zoneOf(player.getUniqueId());
            return zone == null ? "" : zone;
        }
        if (key.equals("cooldown") || key.equals("cooldown_seconds")) {
            return cooldown(longest(player), key.endsWith("_seconds"));
        }
        if (key.startsWith("cooldown_seconds_")) {
            return destinationCooldown(player, key.substring("cooldown_seconds_".length()), true);
        }
        if (key.startsWith("cooldown_")) {
            return destinationCooldown(player, key.substring("cooldown_".length()), false);
        }
        return null;
    }

    private String zone(String rest) {
        if (rest.endsWith("_countdown")) {
            int seconds = plugin.zones().countdown(rest.substring(0, rest.length() - "_countdown".length()));
            return seconds < 0 ? "" : String.valueOf(seconds);
        }
        if (rest.endsWith("_players")) {
            return String.valueOf(plugin.zones().players(rest.substring(0, rest.length() - "_players".length())));
        }
        return null;
    }

    private long longest(OfflinePlayer player) {
        CooldownStore store = plugin.cooldowns();
        long longest = store.remainingMillis(player.getUniqueId(), CooldownStore.SHARED);
        if (!plugin.settings().cooldowns().shared()) {
            for (String id : plugin.settings().destinations().keySet()) {
                longest = Math.max(longest, store.remainingMillis(player.getUniqueId(), id));
            }
        }
        return longest;
    }

    private String destinationCooldown(OfflinePlayer player, String id, boolean seconds) {
        Destination destination = plugin.settings().destinations().get(id);
        if (destination == null) {
            return null;
        }
        String key = plugin.settings().cooldowns().shared() ? CooldownStore.SHARED : destination.id();
        return cooldown(plugin.cooldowns().remainingMillis(player.getUniqueId(), key), seconds);
    }

    private String cooldown(long millis, boolean seconds) {
        long left = Durations.ceilSeconds(millis);
        if (seconds) {
            return String.valueOf(left);
        }
        return left <= 0 ? plugin.messages().raw("placeholder-ready") : Durations.format(left);
    }
}
