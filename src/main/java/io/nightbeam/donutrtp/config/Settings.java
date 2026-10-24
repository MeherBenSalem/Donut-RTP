package io.nightbeam.donutrtp.config;

import io.nightbeam.donutrtp.destination.Destination;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Everything in config.yml, read once per load. */
public record Settings(Warmup warmup, Cooldowns cooldowns, Search search, Landing landing, Menu menu,
                       Map<String, Destination> destinations, boolean updateCheck, boolean notifyOpsOnJoin) {

    public record Warmup(int seconds, boolean cancelOnMove, boolean cancelOnDamage, boolean actionbar, boolean title,
                         SoundSpec tickSound) {
    }

    public record Cooldowns(int seconds, boolean shared, Map<String, Integer> permissions) {
        /**
         * The cooldown for a player: the destination's own value when it has one ({@code >= 0}), else the global one,
         * then lowered to the smallest permission tier the player has.
         */
        public int secondsFor(Predicate<String> hasPermission, int destinationCooldown) {
            int base = destinationCooldown >= 0 ? destinationCooldown : seconds;
            int result = base;
            for (Map.Entry<String, Integer> tier : permissions.entrySet()) {
                if (tier.getValue() < result && hasPermission.test(tier.getKey())) {
                    result = tier.getValue();
                }
            }
            return Math.max(0, result);
        }
    }

    public record Search(int maxAttempts, int poolSize, int refillSeconds, boolean avoidRegions, boolean avoidClaims,
                         int borderMargin) {
    }

    public record Landing(int noFallDamageSeconds, int invulnerableSeconds, SoundSpec sound, String particle,
                          List<String> commands) {
    }

    public record Menu(String title, int rows, boolean fillEmptySlots, String filler, String cooldownLore,
                       String lockedLore, String costLore) {
    }
}
