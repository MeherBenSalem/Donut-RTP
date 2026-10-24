package io.nightbeam.donutrtp.platform;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Coloured text for chat, action bars and titles that works on Bukkit, Spigot and Paper: {@code &} codes and
 * {@code &#RRGGBB} hex. Action bars use Adventure when the server has it and the Bungee chat API otherwise.
 */
public final class Text {
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    private Text() {
    }

    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            StringBuilder hex = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) {
                hex.append('§').append(Character.toLowerCase(c));
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(hex.toString()));
        }
        matcher.appendTail(out);
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    /** Sends a coloured line; an empty line sends nothing, so messages can be switched off in messages.yml. */
    public static void send(CommandSender to, String colored) {
        if (colored != null && !colored.isEmpty()) {
            to.sendMessage(colored);
        }
    }

    public static void actionBar(Player player, String colored) {
        if (colored == null) {
            return;
        }
        if (Platform.adventure()) {
            AdventureText.actionBar(player, colored);
        } else {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colored));
        }
    }

    @SuppressWarnings("deprecation")
    public static void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(title == null ? "" : title, subtitle == null ? "" : subtitle, fadeIn, stay, fadeOut);
    }

    /** Only loaded when Adventure exists, so Spigot never resolves these classes. */
    private static final class AdventureText {
        static void actionBar(Player player, String colored) {
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize(colored));
        }
    }
}
