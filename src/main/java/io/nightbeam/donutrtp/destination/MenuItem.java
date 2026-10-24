package io.nightbeam.donutrtp.destination;

import java.util.List;

/**
 * How a destination looks in the menu. {@code material} can be a material name or {@code hdb-<id>} for a
 * HeadDatabase head; a head texture, UUID or player name makes it a player head instead. A slot below 0 hides it.
 */
public record MenuItem(int slot, String material, String headTexture, String headUuid, String headPlayer,
                       String name, List<String> lore) {
    public boolean hidden() {
        return slot < 0;
    }

    public boolean hasHead() {
        return !headTexture.isEmpty() || !headUuid.isEmpty() || !headPlayer.isEmpty();
    }
}
