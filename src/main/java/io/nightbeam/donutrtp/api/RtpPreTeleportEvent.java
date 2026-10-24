package io.nightbeam.donutrtp.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Called when a safe spot was found, right before the player is moved. Cancel it to stop the teleport (nothing is
 * charged and no cooldown starts) or change where the player lands.
 */
public final class RtpPreTeleportEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String destination;
    private final RtpSource source;
    private Location location;
    private boolean cancelled;

    public RtpPreTeleportEvent(Player player, String destination, RtpSource source, Location location) {
        super(player);
        this.destination = destination;
        this.source = source;
        this.location = location;
    }

    /** The destination id from config.yml. */
    public String getDestination() {
        return destination;
    }

    public RtpSource getSource() {
        return source;
    }

    public Location getLocation() {
        return location.clone();
    }

    public void setLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("location needs a world");
        }
        this.location = location.clone();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
