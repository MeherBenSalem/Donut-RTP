package io.nightbeam.donutrtp;

import io.nightbeam.donutrtp.command.AdminCommand;
import io.nightbeam.donutrtp.command.RtpCommand;
import io.nightbeam.donutrtp.config.ConfigLoader;
import io.nightbeam.donutrtp.config.Messages;
import io.nightbeam.donutrtp.config.Settings;
import io.nightbeam.donutrtp.gui.RtpMenu;
import io.nightbeam.donutrtp.hook.GriefPreventionHook;
import io.nightbeam.donutrtp.hook.HeadDatabaseHook;
import io.nightbeam.donutrtp.hook.Payments;
import io.nightbeam.donutrtp.hook.PlaceholderHook;
import io.nightbeam.donutrtp.hook.WorldGuardHook;
import io.nightbeam.donutrtp.platform.Platform;
import io.nightbeam.donutrtp.platform.Scheduler;
import io.nightbeam.donutrtp.search.LocationSearch;
import io.nightbeam.donutrtp.teleport.CooldownStore;
import io.nightbeam.donutrtp.teleport.TeleportService;
import io.nightbeam.donutrtp.util.UpdateChecker;
import io.nightbeam.donutrtp.zone.ZoneService;
import io.nightbeam.donutrtp.zone.ZoneStore;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class DonutRTPPlugin extends JavaPlugin implements Listener {
    private static final int BSTATS_ID = 33560;

    private volatile Settings settings;
    private volatile Messages messages;
    private volatile Payments payments = Payments.NONE;
    private volatile String newerVersion;
    private Scheduler scheduler;
    private WorldGuardHook worldGuard;
    private GriefPreventionHook griefPrevention;
    private CooldownStore cooldowns;
    private LocationSearch search;
    private TeleportService teleports;
    private ZoneStore zoneStore;
    private ZoneService zones;
    private RtpMenu menu;
    private Scheduler.Task saveTask;

    @Override
    public void onEnable() {
        scheduler = Scheduler.create(this);
        try {
            settings = ConfigLoader.load(this);
        } catch (Exception e) {
            getLogger().severe("config.yml has a mistake and was not loaded: " + e.getMessage());
            getLogger().severe("Using the built-in defaults until it is fixed and /rtpadmin reload is run.");
            settings = ConfigLoader.defaults(this);
        }
        messages = Messages.load(this);

        worldGuard = WorldGuardHook.create(getLogger());
        griefPrevention = GriefPreventionHook.create(getLogger());
        cooldowns = new CooldownStore(new File(getDataFolder(), "data/cooldowns.yml"), System::currentTimeMillis);
        cooldowns.load(getLogger());

        search = new LocationSearch(scheduler, this::settings, this::isProtected, getLogger());
        teleports = new TeleportService(scheduler, this::settings, this::messages, this::payments, search, cooldowns, getLogger());
        zoneStore = new ZoneStore(new File(getDataFolder(), "zones.yml"));
        zones = new ZoneService(scheduler, this::settings, this::messages, worldGuard, search, teleports, cooldowns, getLogger());
        menu = new RtpMenu(scheduler, this::settings, this::messages, this::payments, teleports,
                new HeadDatabaseHook(getLogger()), getLogger());

        Bukkit.getPluginManager().registerEvents(teleports, this);
        Bukkit.getPluginManager().registerEvents(zones, this);
        Bukkit.getPluginManager().registerEvents(menu, this);
        Bukkit.getPluginManager().registerEvents(this, this);

        AdminCommand admin = new AdminCommand(this);
        bind("rtp", new RtpCommand(this, admin));
        bind("rtpadmin", admin);

        try {
            zones.apply(zoneStore.load(getLogger()));
        } catch (Exception e) {
            getLogger().severe("zones.yml has a mistake and was not loaded: " + e.getMessage()
                    + " No zones run until it is fixed and /rtpadmin reload is run.");
        }
        search.start();
        saveTask = scheduler.globalTimer(() -> scheduler.async(() -> cooldowns.saveIfDirty(getLogger())), 600L, 600L);

        // Economy plugins register with Vault while enabling, so look it up once every plugin is enabled.
        scheduler.globalLater(() -> payments = Payments.create(getLogger()), 1L);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new PlaceholderHook(this).register();
                getLogger().info("Hooked into PlaceholderAPI: %donutrtp_...% placeholders are available.");
            } catch (Throwable e) {
                getLogger().warning("Could not register PlaceholderAPI placeholders: " + e);
            }
        }
        try {
            new Metrics(this, BSTATS_ID);
        } catch (Throwable e) {
            getLogger().log(Level.FINE, "bStats is unavailable", e);
        }
        checkForUpdate();
        getLogger().info("DonutRTP " + getDescription().getVersion() + " enabled on " + Platform.describe() + " with "
                + settings.destinations().size() + " destinations and " + zones.zones().size() + " zones.");
    }

    @Override
    public void onDisable() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (search != null) {
            search.stop();
        }
        if (zones != null) {
            zones.clear(true);
        }
        if (teleports != null) {
            teleports.clear();
        }
        if (cooldowns != null) {
            cooldowns.saveIfDirty(getLogger());
        }
    }

    /**
     * /rtpadmin reload: config, messages and zones. A file with a mistake is skipped and keeps its previous settings;
     * the problems are returned for the admin.
     */
    public List<String> reload() {
        List<String> problems = new ArrayList<>();
        try {
            settings = ConfigLoader.load(this);
        } catch (Exception e) {
            problems.add("config.yml: " + e.getMessage());
        }
        messages = Messages.load(this);
        try {
            zones.apply(zoneStore.load(getLogger()));
        } catch (Exception e) {
            problems.add("zones.yml: " + e.getMessage());
        }
        search.start();
        scheduler.global(() -> payments = Payments.create(getLogger()));
        for (String problem : problems) {
            getLogger().severe("Not reloaded, the previous settings stay: " + problem);
        }
        return problems;
    }

    private void bind(String name, Object executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "plugin.yml is missing /" + name);
        command.setExecutor((org.bukkit.command.CommandExecutor) executor);
        command.setTabCompleter((org.bukkit.command.TabCompleter) executor);
    }

    private boolean isProtected(Location location) {
        Settings.Search rules = settings.search();
        return (rules.avoidRegions() && worldGuard.isProtected(location))
                || (rules.avoidClaims() && griefPrevention.isClaimed(location));
    }

    private void checkForUpdate() {
        if (!settings.updateCheck()) {
            return;
        }
        String current = getDescription().getVersion();
        scheduler.async(() -> {
            try {
                String latest = UpdateChecker.latest("DonutRTP/" + current + " (" + UpdateChecker.PAGE + ")");
                if (latest != null && UpdateChecker.compare(latest, current) > 0) {
                    newerVersion = latest;
                    getLogger().warning("DonutRTP " + latest + " is available (you have " + current + "): " + UpdateChecker.PAGE);
                }
            } catch (Exception e) {
                getLogger().log(Level.FINE, "Update check failed", e);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String latest = newerVersion;
        if (latest != null && settings.notifyOpsOnJoin() && event.getPlayer().hasPermission("donutrtp.admin")) {
            messages.send(event.getPlayer(), "update-available", "version", latest, "url", UpdateChecker.PAGE);
        }
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Payments payments() {
        return payments;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    public WorldGuardHook worldGuard() {
        return worldGuard;
    }

    public GriefPreventionHook griefPrevention() {
        return griefPrevention;
    }

    public CooldownStore cooldowns() {
        return cooldowns;
    }

    public LocationSearch search() {
        return search;
    }

    public TeleportService teleports() {
        return teleports;
    }

    public ZoneStore zoneStore() {
        return zoneStore;
    }

    public ZoneService zones() {
        return zones;
    }

    public RtpMenu menu() {
        return menu;
    }
}
