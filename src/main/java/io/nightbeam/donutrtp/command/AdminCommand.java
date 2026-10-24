package io.nightbeam.donutrtp.command;

import io.nightbeam.donutrtp.DonutRTPPlugin;
import io.nightbeam.donutrtp.config.Messages;
import io.nightbeam.donutrtp.destination.Destination;
import io.nightbeam.donutrtp.platform.Platform;
import io.nightbeam.donutrtp.platform.Text;
import io.nightbeam.donutrtp.zone.Zone;
import io.nightbeam.donutrtp.zone.ZoneArea;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /rtpadmin: reload, info, the zone selection and zone management. */
public final class AdminCommand implements TabExecutor {
    private static final List<String> ZONE_KEYS = List.of("destination", "mode", "countdown", "min-players", "spread",
            "cooldown", "cancel-on-move", "permission", "enabled", "title", "actionbar", "bossbar", "region", "area");

    private final DonutRTPPlugin plugin;
    private final Map<UUID, Location[]> selections = new ConcurrentHashMap<>();

    public AdminCommand(DonutRTPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages m = plugin.messages();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("donutrtp.admin")) {
                    m.send(sender, "no-permission");
                    return true;
                }
                List<String> problems = plugin.reload();
                if (problems.isEmpty()) {
                    m.send(sender, "reloaded");
                } else {
                    for (String problem : problems) {
                        sender.sendMessage(Text.color("&cNot reloaded, the previous settings stay: &f" + problem));
                    }
                }
            }
            case "info" -> {
                if (!sender.hasPermission("donutrtp.admin")) {
                    m.send(sender, "no-permission");
                    return true;
                }
                info(sender);
            }
            case "pos1", "pos2" -> {
                if (!sender.hasPermission("donutrtp.zone.admin")) {
                    m.send(sender, "no-permission");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    m.send(sender, "players-only");
                    return true;
                }
                Location at = player.getLocation().getBlock().getLocation();
                Location[] selection = selections.computeIfAbsent(player.getUniqueId(), id -> new Location[2]);
                selection[sub.equals("pos1") ? 0 : 1] = at;
                m.send(player, "pos-set", "corner", sub.substring(3), "x", at.getBlockX(), "y", at.getBlockY(), "z", at.getBlockZ());
            }
            case "zone" -> {
                if (!sender.hasPermission("donutrtp.zone.admin")) {
                    m.send(sender, "no-permission");
                    return true;
                }
                zone(sender, RtpCommand.tail(args, 1));
            }
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        String l = "/" + label;
        sender.sendMessage(Text.color("&6&lDonutRTP &7" + plugin.getDescription().getVersion()));
        sender.sendMessage(Text.color("&e" + l + " reload &7- reload config.yml, messages.yml and zones.yml"));
        sender.sendMessage(Text.color("&e" + l + " info &7- platform, hooks, destinations and zones"));
        sender.sendMessage(Text.color("&e" + l + " pos1 &7/ &epos2 &7- mark the corners of a new zone where you stand"));
        sender.sendMessage(Text.color("&e" + l + " zone create <name> <destination> [region <id>]"));
        sender.sendMessage(Text.color("&e" + l + " zone set <name> <key> <value> &7- " + String.join(", ", ZONE_KEYS)));
        sender.sendMessage(Text.color("&e" + l + " zone delete|info <name> &7, &e" + l + " zone list"));
        sender.sendMessage(Text.color("&e/rtp player <player> [destination] &7- send a player"));
    }

    private void info(CommandSender sender) {
        sender.sendMessage(Text.color("&6&lDonutRTP &7" + plugin.getDescription().getVersion() + " &8- &f" + Platform.describe()));
        sender.sendMessage(Text.color("&7Hooks: WorldGuard " + yes(plugin.worldGuard().available())
                + "&7, GriefPrevention " + yes(plugin.griefPrevention().available())
                + "&7, Vault " + yes(plugin.payments().available())
                + "&7, PlaceholderAPI " + yes(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))));
        for (Destination d : plugin.settings().destinations().values()) {
            World world = d.world();
            sender.sendMessage(Text.color("&e" + d.id() + " &7" + d.worldName() + (world == null ? " &c(not loaded)" : "")
                    + " &7" + d.shape().name().toLowerCase(Locale.ROOT) + " " + d.minRadius() + "-" + d.maxRadius()
                    + " &7ready spots: &f" + plugin.search().ready(d.id())));
        }
        for (Zone zone : plugin.zones().zones().values()) {
            sender.sendMessage(Text.color("&dzone " + zone.id() + " &7" + zone.mode().name().toLowerCase(Locale.ROOT)
                    + " -> " + zone.destination() + " &7players: &f" + plugin.zones().players(zone.id())
                    + (zone.enabled() ? "" : " &c(disabled)")));
        }
    }

    private static String yes(boolean value) {
        return value ? "&ayes" : "&cno";
    }

    private void zone(CommandSender sender, String[] args) {
        Messages m = plugin.messages();
        String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        Map<String, Zone> zones = new LinkedHashMap<>(plugin.zones().zones());
        switch (action) {
            case "list" -> {
                if (zones.isEmpty()) {
                    sender.sendMessage(Text.color("&7No zones yet. Stand on two corners with &e/rtpadmin pos1 &7and &epos2&7, then &e/rtpadmin zone create <name> <destination>&7."));
                }
                for (Zone zone : zones.values()) {
                    sender.sendMessage(Text.color("&d" + zone.id() + " &7" + zone.mode().name().toLowerCase(Locale.ROOT)
                            + " -> &e" + zone.destination() + " &7in " + zone.area().world() + (zone.enabled() ? "" : " &c(disabled)")));
                }
            }
            case "create" -> create(sender, args, zones);
            case "delete", "remove" -> {
                Zone zone = args.length < 2 ? null : zones.remove(args[1].toLowerCase(Locale.ROOT));
                if (zone == null) {
                    m.send(sender, "zone-not-found", "zone", args.length < 2 ? "?" : args[1]);
                    return;
                }
                save(sender, zones);
                m.send(sender, "zone-deleted", "zone", zone.id());
            }
            case "info" -> {
                Zone zone = args.length < 2 ? null : zones.get(args[1].toLowerCase(Locale.ROOT));
                if (zone == null) {
                    m.send(sender, "zone-not-found", "zone", args.length < 2 ? "?" : args[1]);
                    return;
                }
                String area = zone.area() instanceof ZoneArea.Region region
                        ? "WorldGuard region " + region.region()
                        : ((ZoneArea.Cuboid) zone.area()).describe();
                sender.sendMessage(Text.color("&d&lZone " + zone.id() + (zone.enabled() ? "" : " &c(disabled)")));
                sender.sendMessage(Text.color("&7Area: &f" + zone.area().world() + " " + area));
                sender.sendMessage(Text.color("&7Destination: &f" + zone.destination() + " &7Mode: &f" + zone.mode()
                        + " &7Countdown: &f" + zone.countdown() + "s"));
                sender.sendMessage(Text.color("&7Min players: &f" + zone.minPlayers() + " &7Spread: &f" + zone.spread()
                        + " &7Cooldown: &f" + zone.cooldown() + "s &7Cancel on move: &f" + zone.cancelOnMove()));
                sender.sendMessage(Text.color("&7Show: title &f" + zone.showTitle() + " &7actionbar &f" + zone.showActionbar()
                        + " &7bossbar &f" + zone.showBossbar() + (zone.permission().isEmpty() ? "" : " &7Permission: &f" + zone.permission())));
                sender.sendMessage(Text.color("&7Players inside: &f" + plugin.zones().players(zone.id())));
            }
            case "set" -> set(sender, args, zones);
            default -> sender.sendMessage(Text.color("&cUsage: /rtpadmin zone <create|set|delete|info|list>"));
        }
    }

    private void create(CommandSender sender, String[] args, Map<String, Zone> zones) {
        Messages m = plugin.messages();
        if (args.length < 3) {
            sender.sendMessage(Text.color("&cUsage: /rtpadmin zone create <name> <destination> [region <id>]"));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]+")) {
            m.send(sender, "zone-invalid-value", "key", "name", "value", args[1]);
            return;
        }
        if (zones.containsKey(id)) {
            m.send(sender, "zone-exists", "zone", id);
            return;
        }
        String destination = args[2].toLowerCase(Locale.ROOT);
        if (!plugin.settings().destinations().containsKey(destination)) {
            m.send(sender, "unknown-destination", "destination", args[2]);
            return;
        }
        ZoneArea area;
        if (args.length >= 5 && args[3].equalsIgnoreCase("region")) {
            area = regionArea(sender, args[4]);
        } else {
            area = selectionArea(sender);
        }
        if (area == null) {
            return;
        }
        zones.put(id, Zone.create(id, destination, area));
        save(sender, zones);
        m.send(sender, "zone-created", "zone", id, "destination", destination);
    }

    private ZoneArea regionArea(CommandSender sender, String region) {
        Messages m = plugin.messages();
        if (!plugin.worldGuard().available()) {
            m.send(sender, "zone-worldguard-missing");
            return null;
        }
        World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
        if (!plugin.worldGuard().regionExists(world, region)) {
            m.send(sender, "zone-region-missing", "region", region, "world", world.getName());
            return null;
        }
        return new ZoneArea.Region(world.getName(), region);
    }

    private ZoneArea selectionArea(CommandSender sender) {
        Messages m = plugin.messages();
        Location[] selection = sender instanceof Player player ? selections.get(player.getUniqueId()) : null;
        if (selection == null || selection[0] == null || selection[1] == null) {
            m.send(sender, "selection-missing");
            return null;
        }
        if (selection[0].getWorld() != selection[1].getWorld()) {
            m.send(sender, "selection-other-world");
            return null;
        }
        return ZoneArea.Cuboid.of(selection[0].getWorld().getName(),
                selection[0].getBlockX(), selection[0].getBlockY(), selection[0].getBlockZ(),
                selection[1].getBlockX(), selection[1].getBlockY(), selection[1].getBlockZ());
    }

    private void set(CommandSender sender, String[] args, Map<String, Zone> zones) {
        Messages m = plugin.messages();
        if (args.length < 3 || (args.length < 4 && !args[2].equalsIgnoreCase("area"))) {
            sender.sendMessage(Text.color("&cUsage: /rtpadmin zone set <name> <key> <value>  &7Keys: " + String.join(", ", ZONE_KEYS)));
            return;
        }
        Zone zone = zones.get(args[1].toLowerCase(Locale.ROOT));
        if (zone == null) {
            m.send(sender, "zone-not-found", "zone", args[1]);
            return;
        }
        String key = args[2].toLowerCase(Locale.ROOT);
        String value = args.length >= 4 ? String.join(" ", RtpCommand.tail(args, 3)) : "selection";
        Zone updated = withValue(sender, zone, key, value);
        if (updated == null) {
            return;
        }
        zones.put(zone.id(), updated);
        save(sender, zones);
        m.send(sender, "zone-updated", "zone", zone.id(), "key", key, "value", value);
    }

    private Zone withValue(CommandSender sender, Zone zone, String key, String value) {
        Messages m = plugin.messages();
        Zone.Builder edit = zone.edit();
        try {
            switch (key) {
                case "destination" -> {
                    edit.destination = value.toLowerCase(Locale.ROOT);
                    if (!plugin.settings().destinations().containsKey(edit.destination)) {
                        m.send(sender, "unknown-destination", "destination", value);
                        return null;
                    }
                }
                case "mode" -> edit.mode = Zone.Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
                case "countdown" -> edit.countdown = nonNegative(value);
                case "min-players" -> edit.minPlayers = Math.max(1, nonNegative(value));
                case "spread" -> edit.spread = Math.min(64, nonNegative(value));
                case "cooldown" -> edit.cooldown = nonNegative(value);
                case "cancel-on-move" -> edit.cancelOnMove = bool(value);
                case "permission" -> edit.permission = value.equalsIgnoreCase("none") || value.equals("-") ? "" : value.trim();
                case "enabled" -> edit.enabled = bool(value);
                case "title" -> edit.showTitle = bool(value);
                case "actionbar" -> edit.showActionbar = bool(value);
                case "bossbar" -> edit.showBossbar = bool(value);
                case "region" -> edit.area = regionArea(sender, value);
                case "area" -> edit.area = selectionArea(sender);
                default -> {
                    m.send(sender, "zone-invalid-value", "key", key, "value", value);
                    return null;
                }
            }
        } catch (IllegalArgumentException e) {
            m.send(sender, "zone-invalid-value", "key", key, "value", value);
            return null;
        }
        // regionArea and selectionArea already told the sender what is wrong.
        return edit.area == null ? null : edit.build();
    }

    private static int nonNegative(String value) {
        int number = Integer.parseInt(value.trim());
        if (number < 0) {
            throw new IllegalArgumentException("negative");
        }
        return number;
    }

    private static boolean bool(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> throw new IllegalArgumentException("not a boolean");
        };
    }

    private void save(CommandSender sender, Map<String, Zone> zones) {
        try {
            plugin.zoneStore().save(zones);
        } catch (Exception e) {
            sender.sendMessage(Text.color("&cCould not save zones.yml: " + e.getMessage()));
        }
        plugin.zones().apply(zones);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("reload", "info", "pos1", "pos2", "zone"));
        } else if (args[0].equalsIgnoreCase("zone")) {
            if (args.length == 2) {
                options.addAll(List.of("create", "set", "delete", "info", "list"));
            } else if (args.length == 3 && !args[1].equalsIgnoreCase("create")) {
                options.addAll(plugin.zones().zones().keySet());
            } else if (args.length == 4 && args[1].equalsIgnoreCase("create")) {
                options.addAll(plugin.settings().destinations().keySet());
            } else if (args.length == 5 && args[1].equalsIgnoreCase("create")) {
                options.add("region");
            } else if (args.length == 6 && args[1].equalsIgnoreCase("create") && sender instanceof Player player) {
                options.addAll(plugin.worldGuard().regionIds(player.getWorld()));
            } else if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
                options.addAll(ZONE_KEYS);
            } else if (args.length == 5 && args[1].equalsIgnoreCase("set")) {
                switch (args[3].toLowerCase(Locale.ROOT)) {
                    case "destination" -> options.addAll(plugin.settings().destinations().keySet());
                    case "mode" -> options.addAll(List.of("GROUP", "INDIVIDUAL"));
                    case "enabled", "cancel-on-move", "title", "actionbar", "bossbar" -> options.addAll(List.of("true", "false"));
                    case "region" -> {
                        if (sender instanceof Player player) {
                            options.addAll(plugin.worldGuard().regionIds(player.getWorld()));
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        if (!sender.hasPermission("donutrtp.admin") && !sender.hasPermission("donutrtp.zone.admin")) {
            return List.of();
        }
        return RtpCommand.filter(options, args[args.length - 1]);
    }
}
