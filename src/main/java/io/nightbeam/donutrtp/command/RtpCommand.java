package io.nightbeam.donutrtp.command;

import io.nightbeam.donutrtp.config.ConfigManager;
import io.nightbeam.donutrtp.gui.GuiManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class RtpCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;
    private final GuiManager guiManager;

    public RtpCommand(ConfigManager configManager, GuiManager guiManager) {
        this.configManager = configManager;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("donutrtp.admin")) {
                sender.sendMessage(configManager.message("no-permission"));
                return true;
            }
            configManager.reload();
            sender.sendMessage(configManager.message("reloaded"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("donutrtp.use")) {
            player.sendMessage(configManager.message("no-permission"));
            return true;
        }

        guiManager.openMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            if ("reload".startsWith(input) && sender.hasPermission("donutrtp.admin")) {
                options.add("reload");
            }
            return options;
        }
        return Collections.emptyList();
    }
}
