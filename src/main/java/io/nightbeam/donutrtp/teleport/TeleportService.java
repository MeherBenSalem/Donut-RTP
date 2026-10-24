package io.nightbeam.donutrtp.teleport;

import io.nightbeam.donutrtp.api.RtpPreTeleportEvent;
import io.nightbeam.donutrtp.api.RtpSource;
import io.nightbeam.donutrtp.api.RtpTeleportEvent;
import io.nightbeam.donutrtp.config.Messages;
import io.nightbeam.donutrtp.config.Settings;
import io.nightbeam.donutrtp.destination.Destination;
import io.nightbeam.donutrtp.hook.Payments;
import io.nightbeam.donutrtp.platform.Platform;
import io.nightbeam.donutrtp.platform.Scheduler;
import io.nightbeam.donutrtp.platform.Text;
import io.nightbeam.donutrtp.search.LocationSearch;
import io.nightbeam.donutrtp.util.Durations;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** The /rtp flow: checks, warmup, search, payment, teleport and the safe landing. */
public final class TeleportService implements Listener {
    public static final String BYPASS_COOLDOWN = "donutrtp.cooldown.bypass";
    public static final String BYPASS_COST = "donutrtp.cost.bypass";
    public static final String BYPASS_WARMUP = "donutrtp.warmup.bypass";

    private final Scheduler scheduler;
    private final Supplier<Settings> settings;
    private final Supplier<Messages> messages;
    private final Supplier<Payments> payments;
    private final LocationSearch search;
    private final CooldownStore cooldowns;
    private final Logger log;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    /** Per player: {no fall damage until, no damage at all until}, in epoch millis. */
    private final Map<UUID, long[]> landing = new ConcurrentHashMap<>();
    private final AtomicBoolean warnedParticle = new AtomicBoolean();

    private static final class Pending {
        final Location origin;
        volatile boolean warming;
        volatile Scheduler.Task task;

        Pending(Location origin, boolean warming) {
            this.origin = origin;
            this.warming = warming;
        }
    }

    public TeleportService(Scheduler scheduler, Supplier<Settings> settings, Supplier<Messages> messages,
                           Supplier<Payments> payments, LocationSearch search, CooldownStore cooldowns, Logger log) {
        this.scheduler = scheduler;
        this.settings = settings;
        this.messages = messages;
        this.payments = payments;
        this.search = search;
        this.cooldowns = cooldowns;
        this.log = log;
    }

    public boolean busy(UUID player) {
        return pending.containsKey(player);
    }

    /** The cooldown key a destination uses: the shared one, or its own. */
    public String cooldownKey(Destination destination) {
        return settings.get().cooldowns().shared() ? CooldownStore.SHARED : destination.id();
    }

    public long cooldownLeft(Player player, Destination destination) {
        return cooldowns.remainingMillis(player.getUniqueId(), cooldownKey(destination));
    }

    /** A player (or an admin for them) asks to go to a destination. Must run on the player's thread. */
    public void request(Player player, Destination destination, RtpSource source) {
        Settings s = settings.get();
        Messages m = messages.get();
        UUID id = player.getUniqueId();
        boolean admin = source == RtpSource.ADMIN;
        if (pending.containsKey(id)) {
            m.send(player, "already-teleporting");
            return;
        }
        if (!admin && !destination.canUse(player)) {
            m.send(player, "destination-no-permission", "destination", destination.displayName());
            return;
        }
        if (destination.world() == null) {
            m.send(player, "world-not-found", "destination", destination.displayName());
            return;
        }
        if (!admin && !player.hasPermission(BYPASS_COOLDOWN)) {
            long left = cooldownLeft(player, destination);
            if (left > 0) {
                m.send(player, "cooldown", "time", Durations.format(Durations.ceilSeconds(left)));
                return;
            }
        }
        double cost = costFor(player, destination, source);
        if (cost > 0 && !payments.get().has(player, cost)) {
            m.send(player, "not-enough-money", "cost", payments.get().format(cost));
            return;
        }
        int warmup = admin || player.hasPermission(BYPASS_WARMUP) ? 0 : s.warmup().seconds();
        Pending entry = new Pending(player.getLocation(), warmup > 0);
        pending.put(id, entry);
        if (warmup > 0) {
            m.send(player, "countdown", "seconds", warmup);
            if (s.warmup().cancelOnMove()) {
                m.send(player, "countdown-warning");
            }
            countdown(player, destination, source, entry, warmup);
        } else {
            go(player, destination, source, entry);
        }
    }

    /** Stops a warmup or search for the player, if one is running. */
    public void cancel(Player player, String messageKey) {
        Pending entry = pending.remove(player.getUniqueId());
        if (entry == null) {
            return;
        }
        if (entry.task != null) {
            entry.task.cancel();
        }
        if (messageKey != null) {
            messages.get().send(player, messageKey);
        }
    }

    private void countdown(Player player, Destination destination, RtpSource source, Pending entry, int left) {
        if (pending.get(player.getUniqueId()) != entry) {
            return;
        }
        Settings.Warmup warmup = settings.get().warmup();
        Messages m = messages.get();
        if (warmup.actionbar()) {
            Text.actionBar(player, m.raw("countdown-actionbar", "seconds", left));
        }
        if (warmup.title()) {
            Text.title(player, m.raw("countdown-title", "seconds", left), m.raw("countdown-subtitle", "seconds", left),
                    0, 25, 5);
        }
        warmup.tickSound().play(player);
        entry.task = scheduler.entityLater(player, () -> {
            if (left <= 1) {
                entry.warming = false;
                go(player, destination, source, entry);
            } else {
                countdown(player, destination, source, entry, left - 1);
            }
        }, 20L);
    }

    private void go(Player player, Destination destination, RtpSource source, Pending entry) {
        if (pending.get(player.getUniqueId()) != entry) {
            return;
        }
        if (search.ready(destination.id()) == 0) {
            messages.get().send(player, "searching");
        }
        search.find(destination).whenComplete((spot, error) -> scheduler.entity(player, () -> {
            if (pending.get(player.getUniqueId()) != entry || !player.isOnline()) {
                return;
            }
            if (spot == null) {
                pending.remove(player.getUniqueId(), entry);
                messages.get().send(player, "no-safe-location");
                return;
            }
            deliver(player, destination, source, spot).whenComplete((ok, e) -> pending.remove(player.getUniqueId(), entry));
        }));
    }

    /**
     * Moves the player to a spot that was already found: the pre-teleport event, payment, teleport, landing
     * protection, effects, cooldown and the after event. Must run on the player's thread. Completes with whether the
     * player was moved.
     */
    public CompletableFuture<Boolean> deliver(Player player, Destination destination, RtpSource source, Location spot) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        Messages m = messages.get();
        RtpPreTeleportEvent pre = new RtpPreTeleportEvent(player, destination.id(), source, spot);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) {
            done.complete(false);
            return done;
        }
        Location from = player.getLocation();
        Location to = pre.getLocation();
        to.setYaw(from.getYaw());
        to.setPitch(from.getPitch());
        double cost = costFor(player, destination, source);
        if (cost > 0 && !payments.get().withdraw(player, cost)) {
            m.send(player, "not-enough-money", "cost", payments.get().format(cost));
            done.complete(false);
            return done;
        }
        Settings.Landing rules = settings.get().landing();
        // Protect from before the move until the timers start on landing.
        long far = System.currentTimeMillis() + 60_000L;
        landing.put(player.getUniqueId(), new long[]{far, rules.invulnerableSeconds() > 0 ? far : 0});
        teleport(player, to).whenComplete((moved, error) -> scheduler.entity(player, () -> {
            boolean ok = error == null && Boolean.TRUE.equals(moved);
            if (!ok) {
                landing.remove(player.getUniqueId());
                if (cost > 0) {
                    payments.get().refund(player, cost);
                }
                m.send(player, "no-safe-location");
                done.complete(false);
                return;
            }
            landed(player, destination, source, from, to, cost);
            done.complete(true);
        }));
        return done;
    }

    private void landed(Player player, Destination destination, RtpSource source, Location from, Location to, double cost) {
        Settings s = settings.get();
        Messages m = messages.get();
        long now = System.currentTimeMillis();
        landing.put(player.getUniqueId(), new long[]{
                now + s.landing().noFallDamageSeconds() * 1000L,
                now + s.landing().invulnerableSeconds() * 1000L});
        player.setFallDistance(0);
        s.landing().sound().play(player, to);
        particles(to, s.landing().particle());
        if (source != RtpSource.ADMIN && source != RtpSource.ZONE && !player.hasPermission(BYPASS_COOLDOWN)) {
            cooldowns.start(player.getUniqueId(), cooldownKey(destination),
                    s.cooldowns().secondsFor(player::hasPermission, destination.cooldown()));
        }
        if (cost > 0) {
            m.send(player, "charged", "cost", payments.get().format(cost));
        }
        m.send(player, "teleported", "x", to.getBlockX(), "y", to.getBlockY(), "z", to.getBlockZ(),
                "destination", destination.displayName(), "world", to.getWorld().getName());
        for (String command : s.landing().commands()) {
            String line = command.replace("{player}", player.getName())
                    .replace("{world}", to.getWorld().getName())
                    .replace("{x}", String.valueOf(to.getBlockX()))
                    .replace("{y}", String.valueOf(to.getBlockY()))
                    .replace("{z}", String.valueOf(to.getBlockZ()))
                    .replace("{destination}", destination.id());
            String trimmed = line.startsWith("/") ? line.substring(1) : line;
            scheduler.global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), trimmed));
        }
        Bukkit.getPluginManager().callEvent(new RtpTeleportEvent(player, destination.id(), source, from, to));
    }

    private CompletableFuture<Boolean> teleport(Player player, Location to) {
        if (Platform.asyncTeleport()) {
            return player.teleportAsync(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        return CompletableFuture.completedFuture(player.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN));
    }

    private void particles(Location at, String name) {
        if (name.isEmpty()) {
            return;
        }
        try {
            Particle particle = Particle.valueOf(name.toUpperCase(Locale.ROOT));
            at.getWorld().spawnParticle(particle, at.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
        } catch (Throwable e) {
            if (warnedParticle.compareAndSet(false, true)) {
                log.warning("landing.particle '" + name + "' cannot be shown (" + e.getClass().getSimpleName()
                        + "). Use a simple particle like PORTAL, or leave it empty.");
            }
        }
    }

    private double costFor(Player player, Destination destination, RtpSource source) {
        if (destination.cost() <= 0 || source == RtpSource.ADMIN || source == RtpSource.ZONE
                || !payments.get().available() || player.hasPermission(BYPASS_COST)) {
            return 0;
        }
        return destination.cost();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLandingDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || landing.isEmpty()) {
            return;
        }
        long[] until = landing.get(player.getUniqueId());
        if (until == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= until[0] && now >= until[1]) {
            landing.remove(player.getUniqueId(), until);
            return;
        }
        if (now < until[1] || (event.getCause() == EntityDamageEvent.DamageCause.FALL && now < until[0])) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWarmupDamage(EntityDamageEvent event) {
        if (pending.isEmpty() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Pending entry = pending.get(player.getUniqueId());
        if (entry != null && entry.warming && settings.get().warmup().cancelOnDamage()) {
            cancel(player, "cancelled-damage");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        movedDuringWarmup(event);
    }

    /** Being teleported away (by /spawn, another plugin, an admin) during the warmup counts as moving. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        movedDuringWarmup(event);
    }

    private void movedDuringWarmup(PlayerMoveEvent event) {
        if (pending.isEmpty()) {
            return;
        }
        Pending entry = pending.get(event.getPlayer().getUniqueId());
        if (entry == null || !entry.warming || !settings.get().warmup().cancelOnMove()) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        // Only walking off the block counts; jumping, falling and turning never cancel.
        if (to.getWorld() != entry.origin.getWorld()
                || to.getBlockX() != entry.origin.getBlockX() || to.getBlockZ() != entry.origin.getBlockZ()) {
            cancel(event.getPlayer(), "cancelled-move");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Pending entry = pending.remove(id);
        if (entry != null && entry.task != null) {
            entry.task.cancel();
        }
        landing.remove(id);
    }

    /** Cancels everything, for reloads and shutdown. */
    public void clear() {
        for (Pending entry : pending.values()) {
            if (entry.task != null) {
                entry.task.cancel();
            }
        }
        pending.clear();
    }
}
