package io.nightbeam.donutrtp.command;

import io.nightbeam.donutrtp.DonutRTPPlugin;
import io.nightbeam.donutrtp.api.RtpSource;
import io.nightbeam.donutrtp.config.Messages;
import io.nightbeam.donutrtp.destination.Destination;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /rtp, /rtp &lt;destination&gt; and /rtp player &lt;player&gt; [destination]. */
public final class RtpCommand implements TabExecutor {
    private final DonutRTPPlugin plugin;
    private final AdminCommand admin;

    public RtpCommand(DonutRTPPlugin plugin, AdminCommand admin) {
        this.plugin = plugin;
        this.admin = admin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages m = plugin.messages();
        if (args.length > 0) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("player")) {
                sendOther(sender, args);
                return true;
            }
            // 1.x commands still work: /rtp reload and /rtp zone ...
            if ((first.equals("reload") || first.equals("zone")) && plugin.settings().destinations().get(first) == null) {
                return admin.onCommand(sender, command, label, args);
            }
        }
        if (!(sender instanceof Player player)) {
            m.send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("donutrtp.use")) {
            m.send(player, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.menu().open(player);
            return true;
        }
        Destination destination = plugin.settings().destinations().get(args[0].toLowerCase(Locale.ROOT));
        if (destination == null) {
            m.send(player, "unknown-destination", "destination", args[0]);
            return true;
        }
        plugin.teleports().request(player, destination, RtpSource.COMMAND);
        return true;
    }

    private void sendOther(CommandSender sender, String[] args) {
        Messages m = plugin.messages();
        if (!sender.hasPermission("donutrtp.admin")) {
            m.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /rtp player <player> [destination]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            m.send(sender, "player-not-found", "player", args[1]);
            return;
        }
        Destination destination;
        if (args.length >= 3) {
            destination = plugin.settings().destinations().get(args[2].toLowerCase(Locale.ROOT));
            if (destination == null) {
                m.send(sender, "unknown-destination", "destination", args[2]);
                return;
            }
        } else {
            destination = defaultFor(target);
            if (destination == null) {
                m.send(sender, "unknown-destination", "destination", "-");
                return;
            }
        }
        m.send(sender, "sent-player", "player", target.getName(), "destination", destination.displayName());
        plugin.scheduler().entity(target, () -> plugin.teleports().request(target, destination, RtpSource.ADMIN));
    }

    /** The destination in the player's current world, else the first one. */
    private Destination defaultFor(Player player) {
        String world = player.getWorld().getName();
        Destination first = null;
        for (Destination destination : plugin.settings().destinations().values()) {
            if (destination.worldName().equals(world)) {
                return destination;
            }
            if (first == null) {
                first = destination;
            }
        }
        return first;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (Destination destination : plugin.settings().destinations().values()) {
                if (destination.canUse(sender)) {
                    options.add(destination.id());
                }
            }
            if (sender.hasPermission("donutrtp.admin")) {
                options.add("player");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("player") && sender.hasPermission("donutrtp.admin")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("player") && sender.hasPermission("donutrtp.admin")) {
            options.addAll(plugin.settings().destinations().keySet());
        }
        return filter(options, args[args.length - 1]);
    }

    static List<String> filter(List<String> options, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    static String[] tail(String[] args, int from) {
        return Arrays.copyOfRange(args, Math.min(from, args.length), args.length);
    }
}
