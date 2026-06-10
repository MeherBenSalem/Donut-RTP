package io.nightbeam.donutrtp.config;

import java.util.List;
import org.bukkit.Material;

public record GuiItemSettings(
        int slot,
        String name,
        List<String> lore,
        Material material,
        HeadSettings head,
        String headDatabaseId
) {
    public boolean hasHeadDatabaseId() {
        return headDatabaseId != null && !headDatabaseId.isBlank();
    }
}
