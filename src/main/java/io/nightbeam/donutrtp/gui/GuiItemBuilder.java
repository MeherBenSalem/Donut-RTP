package io.nightbeam.donutrtp.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.nightbeam.donutrtp.config.GuiItemSettings;
import io.nightbeam.donutrtp.config.HeadSettings;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class GuiItemBuilder {

    private static final String TEXTURE_URL_PREFIX = "http://textures.minecraft.net/texture/";

    private GuiItemBuilder() {
    }

    public static ItemStack build(GuiItemSettings settings, Material fallbackMaterial, Logger logger) {
        Material material = fallbackMaterial;
        ItemStack item;

        if (settings.head() != null && settings.head().isPresent()) {
            ItemStack headItem = buildHead(settings.head(), fallbackMaterial, logger);
            if (headItem != null) {
                item = headItem;
            } else {
                material = resolveMaterial(settings.material(), fallbackMaterial, logger);
                item = new ItemStack(material);
            }
        } else {
            material = resolveMaterial(settings.material(), fallbackMaterial, logger);
            item = new ItemStack(material);
        }

        applyMeta(item, settings.name(), settings.lore());
        return item;
    }

    private static Material resolveMaterial(Material configured, Material fallback, Logger logger) {
        if (configured == null || configured.isAir() || !configured.isItem()) {
            if (configured != null && configured != fallback) {
                logger.warning("Invalid GUI material '" + configured + "', using default " + fallback);
            }
            return fallback;
        }
        return configured;
    }

    private static ItemStack buildHead(HeadSettings head, Material fallback, Logger logger) {
        PlayerProfile profile = null;

        if (head.hasTexture()) {
            profile = profileFromTexture(head.texture().trim(), logger);
        } else if (head.hasUuid()) {
            profile = profileFromUuid(head.uuid().trim(), logger);
        } else if (head.hasPlayer()) {
            profile = profileFromPlayer(head.player().trim(), logger);
        }

        if (profile == null) {
            logger.warning("Invalid player head configuration, using default material " + fallback);
            return null;
        }

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) {
            logger.warning("Could not apply player head meta, using default material " + fallback);
            return null;
        }
        meta.setOwnerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    private static PlayerProfile profileFromTexture(String texture, Logger logger) {
        String value = texture;
        if (!texture.startsWith("eyJ")) {
            if (!texture.matches("(?i)[a-f0-9]{64}")) {
                logger.warning("Invalid head texture value '" + texture + "'");
                return null;
            }
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + TEXTURE_URL_PREFIX + texture + "\"}}}";
            value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }

        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
        profile.setProperty(new ProfileProperty("textures", value));
        return profile;
    }

    private static PlayerProfile profileFromUuid(String uuidString, Logger logger) {
        try {
            UUID uuid = UUID.fromString(uuidString);
            return Bukkit.createProfile(uuid);
        } catch (IllegalArgumentException ex) {
            logger.warning("Invalid head UUID '" + uuidString + "'");
            return null;
        }
    }

    private static PlayerProfile profileFromPlayer(String playerName, Logger logger) {
        if (playerName.isBlank()) {
            logger.warning("Invalid head player name");
            return null;
        }
        return Bukkit.createProfile(playerName);
    }

    private static void applyMeta(ItemStack item, String displayName, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
    }
}
