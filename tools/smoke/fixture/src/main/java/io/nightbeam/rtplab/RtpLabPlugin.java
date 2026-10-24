package io.nightbeam.rtplab;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Console helpers for the DonutRTP smoke matrix. Every command prints one {@code [RTPLAB] <command> ...} line (or
 * {@code [RTPLAB] error ...}) that the runner waits for. Uses the region-aware schedulers, which Paper has too.
 */
public final class RtpLabPlugin extends JavaPlugin {
    private final LabEconomy economy = new LabEconomy();

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null || Bukkit.getPluginManager().getPlugin("VaultUnlocked") != null) {
            Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class, economy, this, ServicePriority.Highest);
        }
        // DonutRTP enables after this plugin; listen to its API once it is there.
        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> {
            if (Bukkit.getPluginManager().isPluginEnabled("DonutRTP")) {
                Bukkit.getPluginManager().registerEvents(new ApiListener(this), this);
                report("api listening");
            }
        }, 1L);
    }

    void report(String line) {
        getLogger().info("[RTPLAB] " + line);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "money" -> {
                    Player player = player(args[1]);
                    economy.set(player.getUniqueId(), Double.parseDouble(args[2]));
                    report("money " + player.getName() + " " + economy.balance(player.getUniqueId()));
                }
                case "balance" -> {
                    Player player = player(args[1]);
                    report("balance " + player.getName() + " " + economy.balance(player.getUniqueId()));
                }
                case "pos" -> {
                    Player player = player(args[1]);
                    player.getScheduler().run(this, task -> report(position(player)), () -> report("error pos retired"));
                }
                case "tp" -> {
                    Player player = player(args[1]);
                    World world = world(args[2]);
                    Location to = new Location(world, Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5]));
                    player.getScheduler().run(this, task -> player.teleportAsync(to)
                            .thenAccept(ok -> report("tp " + player.getName() + " " + ok)), () -> report("error tp retired"));
                }
                case "damage" -> {
                    Player player = player(args[1]);
                    double amount = Double.parseDouble(args[2]);
                    player.getScheduler().run(this, task -> {
                        player.damage(amount);
                        report("damage " + player.getName() + " health=" + player.getHealth());
                    }, () -> report("error damage retired"));
                }
                case "fall" -> {
                    Player player = player(args[1]);
                    player.getScheduler().run(this, task -> {
                        @SuppressWarnings("deprecation")
                        EntityDamageEvent event = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, 6.0);
                        Bukkit.getPluginManager().callEvent(event);
                        report("fall " + player.getName() + " cancelled=" + event.isCancelled());
                    }, () -> report("error fall retired"));
                }
                case "perm" -> {
                    Player player = player(args[1]);
                    boolean value = Boolean.parseBoolean(args[3]);
                    player.getScheduler().run(this, task -> {
                        player.addAttachment(this, args[2], value);
                        report("perm " + player.getName() + " " + args[2] + "=" + player.hasPermission(args[2]));
                    }, () -> report("error perm retired"));
                }
                case "set" -> {
                    File file = new File(getDataFolder().getParentFile(), "DonutRTP/config.yml");
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                    String value = String.join(" ", List.of(args).subList(2, args.length));
                    yaml.set(args[1], parse(value));
                    yaml.save(file);
                    report("set " + args[1] + "=" + yaml.get(args[1]));
                }
                case "yaml" -> {
                    File file = new File(getDataFolder().getParentFile(), "DonutRTP/" + args[1]);
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                    report("yaml " + args[1] + " " + args[2] + "=" + yaml.get(args[2]));
                }
                case "file" -> {
                    File file = new File(getDataFolder().getParentFile(), "DonutRTP/" + args[1]);
                    report("file " + args[1] + " exists=" + file.exists());
                }
                case "region" -> {
                    World world = world(args[1]);
                    Regions.create(world, args[2], ints(args, 3, 6));
                    report("region " + args[2] + " created");
                }
                case "claim" -> {
                    World world = world(args[1]);
                    report("claim " + claim(world, ints(args, 2, 4)));
                }
                case "sudo" -> {
                    // Runs a command as the player, on their own thread.
                    Player player = player(args[1]);
                    String line = String.join(" ", List.of(args).subList(2, args.length));
                    player.getScheduler().run(this, task -> {
                        player.performCommand(line.startsWith("/") ? line.substring(1) : line);
                        report("sudo " + player.getName() + " " + line);
                    }, () -> report("error sudo retired"));
                }
                case "platform" -> {
                    // A 21x21 stone floor with open air above, for bots that need to walk.
                    World world = world(args[1]);
                    int[] at = ints(args, 2, 3);
                    Bukkit.getRegionScheduler().run(this, world, at[0] >> 4, at[2] >> 4, task -> {
                        for (int dx = -10; dx <= 10; dx++) {
                            for (int dz = -10; dz <= 10; dz++) {
                                world.getBlockAt(at[0] + dx, at[1] - 1, at[2] + dz).setType(org.bukkit.Material.STONE);
                                for (int dy = 0; dy < 4; dy++) {
                                    world.getBlockAt(at[0] + dx, at[1] + dy, at[2] + dz).setType(org.bukkit.Material.AIR);
                                }
                            }
                        }
                        report("platform " + at[0] + " " + at[1] + " " + at[2]);
                    });
                }
                case "surface" -> {
                    World world = world(args[1]);
                    int x = Integer.parseInt(args[2]);
                    int z = Integer.parseInt(args[3]);
                    Bukkit.getRegionScheduler().run(this, world, x >> 4, z >> 4,
                            task -> report("surface " + x + " " + z + " " + world.getHighestBlockYAt(x, z)));
                }
                default -> report("error unknown " + sub);
            }
        } catch (Exception | LinkageError e) {
            report("error " + sub + " " + e);
        }
        return true;
    }

    private static Player player(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            throw new IllegalArgumentException("no player " + name);
        }
        return player;
    }

    private static World world(String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            throw new IllegalArgumentException("no world " + name);
        }
        return world;
    }

    private static int[] ints(String[] args, int from, int count) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = Integer.parseInt(args[from + i]);
        }
        return out;
    }

    private static Object parse(String value) {
        if (value.equals("true") || value.equals("false")) {
            return Boolean.parseBoolean(value);
        }
        if (value.equals("[]")) {
            return new ArrayList<>();
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // not an int
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            // not a number
        }
        return value.replaceAll("^\"|\"$", "");
    }

    /** Where the player is and what they stand on: floor, feet and head blocks, and the biome. */
    private static String position(Player player) {
        Location at = player.getLocation();
        World world = at.getWorld();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int floorY = (int) Math.floor(at.getY()) - 1;
        Block floor = world.getBlockAt(x, floorY, z);
        Block feet = world.getBlockAt(x, floorY + 1, z);
        Block head = world.getBlockAt(x, floorY + 2, z);
        Object biome = world.getBiome(x, floorY + 1, z);
        String biomeKey = biome instanceof Keyed keyed ? keyed.getKey().toString() : String.valueOf(biome);
        return String.format(Locale.ROOT, "pos %s %s %.2f %.2f %.2f floor=%s/%s feet=%s/%s/%s head=%s/%s/%s biome=%s",
                player.getName(), world.getName(), at.getX(), at.getY(), at.getZ(),
                floor.getType(), floor.getType().isSolid(),
                feet.getType(), feet.isPassable(), feet.isLiquid(),
                head.getType(), head.isPassable(), head.isLiquid(), biomeKey);
    }

    /** A GriefPrevention admin claim through reflection, so the fixture builds without GriefPrevention. */
    private String claim(World world, int[] box) throws Exception {
        Plugin gp = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (gp == null) {
            throw new IllegalStateException("GriefPrevention is not installed");
        }
        Object store = gp.getClass().getField("dataStore").get(gp);
        for (Method method : store.getClass().getMethods()) {
            if (!method.getName().equals("createClaim") || method.getParameterCount() < 11) {
                continue;
            }
            Object[] values = new Object[method.getParameterCount()];
            values[0] = world;
            values[1] = box[0];
            values[2] = box[2];
            values[3] = world.getMinHeight();
            values[4] = world.getMaxHeight();
            values[5] = box[1];
            values[6] = box[3];
            for (int i = 11; i < values.length; i++) {
                values[i] = method.getParameterTypes()[i] == boolean.class ? Boolean.FALSE : null;
            }
            Object result = method.invoke(store, values);
            return "created=" + result.getClass().getField("succeeded").get(result);
        }
        throw new IllegalStateException("no createClaim method");
    }
}
