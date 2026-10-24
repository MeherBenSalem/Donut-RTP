package io.nightbeam.donutrtp.gui;

import io.nightbeam.donutrtp.destination.MenuItem;
import io.nightbeam.donutrtp.hook.HeadDatabaseHook;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

/** Builds the base item for a menu entry: a material, a HeadDatabase head or a player head. */
final class MenuIcons {
    private static final Pattern URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final String TEXTURES = "https://textures.minecraft.net/texture/";

    private final HeadDatabaseHook headDatabase;
    private final Logger log;
    private final Map<String, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, Boolean> warned = new ConcurrentHashMap<>();

    MenuIcons(HeadDatabaseHook headDatabase, Logger log) {
        this.headDatabase = headDatabase;
        this.log = log;
    }

    ItemStack base(MenuItem item) {
        if (item.hasHead()) {
            ItemStack head = playerHead(item);
            if (head != null) {
                return head;
            }
        }
        if (HeadDatabaseHook.isHeadId(item.material())) {
            ItemStack head = headDatabase.head(item.material());
            if (head != null) {
                return head;
            }
            warnOnce(item.material(), "HeadDatabase head '" + item.material() + "' is not available; showing a compass.");
            return new ItemStack(Material.COMPASS);
        }
        return new ItemStack(material(item.material(), Material.COMPASS));
    }

    Material material(String name, Material fallback) {
        Material material = name == null ? null : Material.matchMaterial(name.trim());
        if (material == null || !material.isItem() || material.isAir()) {
            warnOnce(String.valueOf(name), "'" + name + "' is not an item; showing " + fallback + " instead.");
            return fallback;
        }
        return material;
    }

    private ItemStack playerHead(MenuItem item) {
        try {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta == null) {
                return null;
            }
            if (!item.headTexture().isEmpty()) {
                PlayerProfile profile = profiles.computeIfAbsent("t:" + item.headTexture(), key -> textured(item.headTexture()));
                if (profile == null) {
                    return null;
                }
                meta.setOwnerProfile(profile);
            } else if (!item.headUuid().isEmpty()) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(item.headUuid())));
            } else {
                PlayerProfile profile = profiles.computeIfAbsent("p:" + item.headPlayer().toLowerCase(), key -> named(item.headPlayer()));
                meta.setOwnerProfile(profile);
            }
            head.setItemMeta(meta);
            return head;
        } catch (Throwable e) {
            warnOnce(item.name(), "Could not build a player head for a menu item: " + e.getMessage());
            return null;
        }
    }

    /** A texture hash, a full texture URL, or the base64 value from a head website. */
    private PlayerProfile textured(String texture) {
        String url;
        if (texture.startsWith("http")) {
            url = texture;
        } else if (texture.matches("(?i)[0-9a-f]{40,80}")) {
            url = TEXTURES + texture;
        } else {
            try {
                String json = new String(Base64.getDecoder().decode(texture), StandardCharsets.UTF_8);
                Matcher matcher = URL.matcher(json);
                if (!matcher.find()) {
                    warnOnce(texture, "Head texture is not a hash, URL or base64 texture value.");
                    return null;
                }
                url = matcher.group(1);
            } catch (IllegalArgumentException e) {
                warnOnce(texture, "Head texture is not a hash, URL or base64 texture value.");
                return null;
            }
        }
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)), "DonutRTP");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create(url.replace("http://", "https://")).toURL());
            profile.setTextures(textures);
            return profile;
        } catch (Exception e) {
            warnOnce(texture, "Bad head texture URL " + url + ": " + e.getMessage());
            return null;
        }
    }

    /** A player's head by name; the skin is fetched in the background and shows from the next menu opening. */
    private PlayerProfile named(String name) {
        PlayerProfile profile = Bukkit.createPlayerProfile(null, name);
        profile.update().thenAccept(complete -> profiles.put("p:" + name.toLowerCase(), complete));
        return profile;
    }

    private void warnOnce(String key, String message) {
        if (warned.putIfAbsent(key, Boolean.TRUE) == null) {
            log.warning(message);
        }
    }
}
