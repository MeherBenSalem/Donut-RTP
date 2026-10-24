package io.nightbeam.donutrtp.zone;

import io.nightbeam.donutrtp.api.RtpSource;
import io.nightbeam.donutrtp.config.Messages;
import io.nightbeam.donutrtp.config.Settings;
import io.nightbeam.donutrtp.destination.Destination;
import io.nightbeam.donutrtp.hook.WorldGuardHook;
import io.nightbeam.donutrtp.platform.Platform;
import io.nightbeam.donutrtp.platform.Scheduler;
import io.nightbeam.donutrtp.platform.Text;
import io.nightbeam.donutrtp.search.LocationSearch;
import io.nightbeam.donutrtp.teleport.CooldownStore;
import io.nightbeam.donutrtp.teleport.TeleportService;
import io.nightbeam.donutrtp.util.Durations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Runs the RTP zones. Membership changes on the player's own thread (move and teleport events); one global timer
 * counts down every second and starts the teleports.
 */
public final class ZoneService implements Listener {
    public static final String USE_PERMISSION = "donutrtp.zone.use";

    private final Scheduler scheduler;
    private final Supplier<Settings> settings;
    private final Supplier<Messages> messages;
    private final WorldGuardHook worldGuard;
    private final LocationSearch search;
    private final TeleportService teleports;
    private final CooldownStore cooldowns;
    private final Logger log;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    /** The zone each player stands in (members, players on cooldown and halted players). */
    private final Map<UUID, String> memberOf = new ConcurrentHashMap<>();
    private final Set<String> warnedDestination = ConcurrentHashMap.newKeySet();
    private volatile Map<String, Zone> zones = Map.of();
    private Scheduler.Task ticker;

    /** Live state of one zone. */
    private static final class State {
        /** Players the zone will teleport. */
        final Set<UUID> members = ConcurrentHashMap.newKeySet();
        /** INDIVIDUAL: seconds left per player. */
        final Map<UUID, Integer> personal = new ConcurrentHashMap<>();
        /** INDIVIDUAL: where each countdown started, for cancel-on-move. */
        final Map<UUID, Location> anchors = new ConcurrentHashMap<>();
        /** Inside but on cooldown; admitted when it ends. */
        final Set<UUID> cooling = ConcurrentHashMap.newKeySet();
        /** Inside but stopped (moved, or no spot found) until they walk out and back in. */
        final Set<UUID> halted = ConcurrentHashMap.newKeySet();
        /** Being teleported by this zone, so leaving is not reported as a cancel. */
        final Set<UUID> departing = ConcurrentHashMap.newKeySet();
        final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
        /** GROUP: seconds left, -1 when not counting. */
        volatile int groupLeft = -1;
        /** GROUP: the number players saw last, for placeholders. */
        volatile int shown = -1;
        volatile boolean firing;
    }

    public ZoneService(Scheduler scheduler, Supplier<Settings> settings, Supplier<Messages> messages,
                       WorldGuardHook worldGuard, LocationSearch search, TeleportService teleports,
                       CooldownStore cooldowns, Logger log) {
        this.scheduler = scheduler;
        this.settings = settings;
        this.messages = messages;
        this.worldGuard = worldGuard;
        this.search = search;
        this.teleports = teleports;
        this.cooldowns = cooldowns;
        this.log = log;
    }

    public Map<String, Zone> zones() {
        return zones;
    }

    /** Replaces the zones (load, reload, admin edits) and starts the timer. */
    public void apply(Map<String, Zone> newZones) {
        clear(false);
        zones = Map.copyOf(newZones);
        warnedDestination.clear();
        for (Zone zone : zones.values()) {
            states.put(zone.id(), new State());
            if (zone.area() instanceof ZoneArea.Region region) {
                if (!worldGuard.available()) {
                    log.warning("Zone '" + zone.id() + "' uses WorldGuard region '" + region.region()
                            + "' but WorldGuard is not installed. It does nothing.");
                } else if (Bukkit.getWorld(region.world()) != null && !worldGuard.regionExists(Bukkit.getWorld(region.world()), region.region())) {
                    log.warning("Zone '" + zone.id() + "': WorldGuard region '" + region.region() + "' was not found in "
                            + region.world() + ".");
                }
            }
        }
        if (ticker == null && !zones.isEmpty()) {
            ticker = scheduler.globalTimer(this::tick, 20L, 20L);
        }
    }

    /** Stops every countdown and removes the boss bars. */
    public void clear(boolean shuttingDown) {
        for (State state : states.values()) {
            for (Map.Entry<UUID, BossBar> bar : state.bars.entrySet()) {
                Player player = Bukkit.getPlayer(bar.getKey());
                if (shuttingDown || player == null) {
                    bar.getValue().removeAll();
                } else {
                    scheduler.entity(player, () -> bar.getValue().removePlayer(player));
                }
            }
        }
        states.clear();
        memberOf.clear();
        if (shuttingDown && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    public String zoneOf(UUID player) {
        return memberOf.get(player);
    }

    /** GROUP countdown players see, or -1. */
    public int countdown(String zone) {
        State state = states.get(zone);
        return state == null ? -1 : state.shown;
    }

    public int players(String zone) {
        State state = states.get(zone);
        return state == null ? 0 : state.members.size();
    }

    public Zone zoneAt(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        String world = location.getWorld().getName();
        for (Zone zone : zones.values()) {
            if (!zone.enabled() || !zone.area().world().equals(world)) {
                continue;
            }
            if (zone.area() instanceof ZoneArea.Cuboid box) {
                if (box.contains(world, location.getX(), location.getY(), location.getZ())) {
                    return zone;
                }
            } else if (zone.area() instanceof ZoneArea.Region region && worldGuard.inRegion(location, region.region())) {
                return zone;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (zones.isEmpty() || to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ() && from.getWorld() == to.getWorld())) {
            return;
        }
        update(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!zones.isEmpty() && event.getTo() != null) {
            update(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String zone = memberOf.get(event.getPlayer().getUniqueId());
        if (zone != null) {
            leave(event.getPlayer(), zone, false);
        }
    }

    private void update(Player player, Location to) {
        UUID id = player.getUniqueId();
        String current = memberOf.get(id);
        Zone inside = zoneAt(to);
        if (current != null && (inside == null || !inside.id().equals(current))) {
            leave(player, current, true);
            current = null;
        }
        if (inside == null) {
            return;
        }
        if (current == null) {
            enter(player, inside);
        } else {
            moveWithin(player, inside, to);
        }
    }

    private void enter(Player player, Zone zone) {
        if (!player.hasPermission(USE_PERMISSION) || (!zone.permission().isEmpty() && !player.hasPermission(zone.permission()))) {
            return;
        }
        State state = states.get(zone.id());
        if (state == null) {
            return;
        }
        UUID id = player.getUniqueId();
        memberOf.put(id, zone.id());
        long left = zoneCooldownLeft(player, zone);
        if (left > 0) {
            state.cooling.add(id);
            messages.get().send(player, "zone-cooldown", "time", Durations.format(Durations.ceilSeconds(left)));
            return;
        }
        admit(player, zone, state);
    }

    private void admit(Player player, Zone zone, State state) {
        UUID id = player.getUniqueId();
        state.cooling.remove(id);
        state.members.add(id);
        messages.get().send(player, "zone-entered");
        if (zone.mode() == Zone.Mode.INDIVIDUAL) {
            state.anchors.put(id, player.getLocation());
            if (zone.countdown() <= 0) {
                fireIndividual(player, zone, state);
            } else {
                state.personal.put(id, zone.countdown());
            }
        }
    }

    private void moveWithin(Player player, Zone zone, Location to) {
        State state = states.get(zone.id());
        UUID id = player.getUniqueId();
        if (state == null || zone.mode() != Zone.Mode.INDIVIDUAL || !zone.cancelOnMove() || !state.personal.containsKey(id)) {
            return;
        }
        Location anchor = state.anchors.get(id);
        if (anchor != null && (anchor.getBlockX() != to.getBlockX() || anchor.getBlockZ() != to.getBlockZ())) {
            state.personal.remove(id);
            state.members.remove(id);
            state.halted.add(id);
            hideBar(player, state);
            messages.get().send(player, "cancelled-move");
        }
    }

    private void leave(Player player, String zoneId, boolean notify) {
        UUID id = player.getUniqueId();
        memberOf.remove(id);
        State state = states.get(zoneId);
        if (state == null) {
            return;
        }
        Zone zone = zones.get(zoneId);
        boolean counting = state.members.remove(id)
                && (state.personal.remove(id) != null || (zone != null && zone.mode() == Zone.Mode.GROUP && state.groupLeft >= 0));
        state.personal.remove(id);
        state.anchors.remove(id);
        state.cooling.remove(id);
        state.halted.remove(id);
        boolean departing = state.departing.remove(id);
        hideBar(player, state);
        if (notify && counting && !departing) {
            messages.get().send(player, "zone-cancelled");
            if (zone != null && zone.showTitle()) {
                Text.title(player, "", "", 0, 1, 0);
            }
        }
    }

    /**
     * Who is in which zone, from where players are rather than from events: some Folia builds fire no event when a
     * plugin teleports a player, so events alone would miss players put onto a pad or sent away from one.
     */
    private void scan() {
        Set<String> worlds = new java.util.HashSet<>();
        for (Zone zone : zones.values()) {
            if (zone.enabled()) {
                worlds.add(zone.area().world());
            }
        }
        if (worlds.isEmpty() && memberOf.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Platform.folia()) {
                scheduler.entity(player, () -> recheck(player, worlds));
            } else {
                recheck(player, worlds);
            }
        }
    }

    private void recheck(Player player, Set<String> worlds) {
        if (!player.isOnline()) {
            return;
        }
        Location at = player.getLocation();
        if (memberOf.containsKey(player.getUniqueId()) || (at.getWorld() != null && worlds.contains(at.getWorld().getName()))) {
            update(player, at);
        }
    }

    private void tick() {
        scan();
        for (Zone zone : zones.values()) {
            State state = states.get(zone.id());
            if (state == null || !zone.enabled()) {
                continue;
            }
            for (UUID id : state.cooling) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) {
                    state.cooling.remove(id);
                } else if (zoneCooldownLeft(player, zone) <= 0) {
                    scheduler.entity(player, () -> {
                        if (zone.id().equals(memberOf.get(id)) && state.cooling.contains(id)) {
                            admit(player, zone, state);
                        }
                    });
                }
            }
            if (zone.mode() == Zone.Mode.GROUP) {
                tickGroup(zone, state);
            } else {
                tickIndividual(zone, state);
            }
        }
    }

    private void tickGroup(Zone zone, State state) {
        if (state.firing) {
            return;
        }
        List<Player> players = new ArrayList<>();
        for (UUID id : state.members) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                state.members.remove(id);
            } else {
                players.add(player);
            }
        }
        if (players.isEmpty()) {
            state.groupLeft = -1;
            state.shown = -1;
            return;
        }
        if (players.size() < zone.minPlayers()) {
            state.groupLeft = -1;
            state.shown = -1;
            int needed = zone.minPlayers() - players.size();
            for (Player player : players) {
                show(player, zone, state, -1, players.size(), needed);
            }
            return;
        }
        if (state.groupLeft < 0) {
            state.groupLeft = zone.countdown();
        }
        int left = state.groupLeft;
        if (left <= 0) {
            fireGroup(zone, state, players);
            return;
        }
        state.shown = left;
        for (Player player : players) {
            show(player, zone, state, left, players.size(), 0);
        }
        state.groupLeft = left - 1;
    }

    private void tickIndividual(Zone zone, State state) {
        for (Map.Entry<UUID, Integer> entry : state.personal.entrySet()) {
            UUID id = entry.getKey();
            int left = entry.getValue();
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                state.personal.remove(id);
                continue;
            }
            if (left <= 0) {
                if (state.personal.remove(id, left)) {
                    scheduler.entity(player, () -> fireIndividual(player, zone, state));
                }
                continue;
            }
            show(player, zone, state, left, state.members.size(), 0);
            state.personal.computeIfPresent(id, (key, value) -> value - 1);
        }
    }

    /** Countdown display on the player's thread. {@code seconds} -1 means waiting for {@code needed} more players. */
    private void show(Player player, Zone zone, State state, int seconds, int count, int needed) {
        scheduler.entity(player, () -> {
            if (!zone.id().equals(memberOf.get(player.getUniqueId())) || !state.members.contains(player.getUniqueId())) {
                return;
            }
            Messages m = messages.get();
            String line = seconds < 0
                    ? m.raw("zone-waiting", "needed", needed, "players", count)
                    : m.raw("zone-countdown-actionbar", "seconds", seconds, "players", count);
            if (zone.showActionbar()) {
                Text.actionBar(player, line);
            }
            if (zone.showTitle() && seconds >= 0) {
                Text.title(player, m.raw("zone-countdown-title", "seconds", seconds),
                        m.raw("zone-countdown-subtitle", "seconds", seconds, "players", count), 0, 25, 5);
            }
            if (zone.showBossbar()) {
                String title = seconds < 0 ? line : m.raw("zone-bossbar", "seconds", seconds, "players", count);
                double progress = seconds < 0 || zone.countdown() <= 0 ? 0 : Math.min(1.0, seconds / (double) zone.countdown());
                BossBar bar = state.bars.get(player.getUniqueId());
                if (bar == null) {
                    bar = Bukkit.createBossBar(title, BarColor.YELLOW, BarStyle.SOLID);
                    bar.setProgress(progress);
                    bar.addPlayer(player);
                    state.bars.put(player.getUniqueId(), bar);
                } else {
                    bar.setTitle(title);
                    bar.setProgress(progress);
                }
            }
        });
    }

    private void hideBar(Player player, State state) {
        BossBar bar = state.bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    private void fireGroup(Zone zone, State state, List<Player> players) {
        Destination destination = destination(zone);
        state.groupLeft = -1;
        state.shown = -1;
        if (destination == null) {
            return;
        }
        state.firing = true;
        for (Player player : players) {
            scheduler.entity(player, () -> {
                hideBar(player, state);
                if (zone.showActionbar()) {
                    Text.actionBar(player, messages.get().raw("searching"));
                }
            });
        }
        search.find(destination).whenComplete((center, error) -> {
            if (center == null) {
                state.firing = false;
                for (Player player : players) {
                    scheduler.entity(player, () -> messages.get().send(player, "no-safe-location"));
                }
                return;
            }
            search.spread(center, players.size(), zone.spread(), destination).whenComplete((spots, spreadError) -> {
                int count = players.size();
                // The zone keeps "teleporting" until everyone has moved, so nobody left behind for a moment sees
                // "waiting for players". The timer covers players whose task never runs (they logged out).
                AtomicInteger left = new AtomicInteger(count);
                Scheduler.Task fallback = scheduler.globalLater(() -> state.firing = false, 200L);
                Runnable oneDone = () -> {
                    if (left.decrementAndGet() == 0) {
                        fallback.cancel();
                        state.firing = false;
                    }
                };
                for (int i = 0; i < count; i++) {
                    Player player = players.get(i);
                    Location spot = spots == null ? center : spots.get(i);
                    scheduler.entity(player, () -> send(player, zone, state, destination, spot, count).whenComplete((ok, e) -> oneDone.run()));
                }
            });
        });
    }

    private void fireIndividual(Player player, Zone zone, State state) {
        Destination destination = destination(zone);
        if (destination == null) {
            return;
        }
        hideBar(player, state);
        if (zone.showActionbar()) {
            Text.actionBar(player, messages.get().raw("searching"));
        }
        search.find(destination).whenComplete((spot, error) -> scheduler.entity(player, () -> {
            if (!zone.id().equals(memberOf.get(player.getUniqueId()))) {
                return;
            }
            if (spot == null) {
                state.members.remove(player.getUniqueId());
                state.halted.add(player.getUniqueId());
                messages.get().send(player, "no-safe-location");
                return;
            }
            send(player, zone, state, destination, spot, 1);
        }));
    }

    /** Teleports one zone member. Completes when the teleport is over, or at once when they already left. */
    private CompletableFuture<Boolean> send(Player player, Zone zone, State state, Destination destination, Location spot, int count) {
        UUID id = player.getUniqueId();
        if (!player.isOnline() || !zone.id().equals(memberOf.get(id)) || !state.members.contains(id)) {
            return CompletableFuture.completedFuture(false);
        }
        state.departing.add(id);
        return teleports.deliver(player, destination, RtpSource.ZONE, spot).whenComplete((moved, error) -> {
            if (Boolean.TRUE.equals(moved)) {
                // Not left to the teleport event, which some Folia builds do not fire.
                leave(player, zone.id(), false);
                if (!player.hasPermission(TeleportService.BYPASS_COOLDOWN)) {
                    cooldowns.start(id, CooldownStore.zoneKey(zone.id()), zone.cooldown());
                }
                if (count > 1) {
                    messages.get().send(player, "zone-teleported-group", "count", count);
                }
            } else {
                state.departing.remove(id);
            }
        });
    }

    private Destination destination(Zone zone) {
        Destination destination = settings.get().destinations().get(zone.destination());
        if (destination == null && warnedDestination.add(zone.id())) {
            log.warning("Zone '" + zone.id() + "' sends players to destination '" + zone.destination()
                    + "', which is not in config.yml.");
        }
        return destination;
    }

    private long zoneCooldownLeft(Player player, Zone zone) {
        if (zone.cooldown() <= 0 || player.hasPermission(TeleportService.BYPASS_COOLDOWN)) {
            return 0;
        }
        return cooldowns.remainingMillis(player.getUniqueId(), CooldownStore.zoneKey(zone.id()));
    }
}
