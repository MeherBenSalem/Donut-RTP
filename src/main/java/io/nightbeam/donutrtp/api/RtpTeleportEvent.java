package io.nightbeam.donutrtp.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Called after a player was randomly teleported. */
public final class RtpTeleportEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String destination;
    private final RtpSource source;
    private final Location from;
    private final Location to;

    public RtpTeleportEvent(Player player, String destination, RtpSource source, Location from, Location to) {
        super(player);
        this.destination = destination;
        this.source = source;
        this.from = from;
        this.to = to;
    }

    public String getDestination() {
        return destination;
    }

    public RtpSource getSource() {
        return source;
    }

    public Location getFrom() {
        return from.clone();
    }

    public Location getTo() {
        return to.clone();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
