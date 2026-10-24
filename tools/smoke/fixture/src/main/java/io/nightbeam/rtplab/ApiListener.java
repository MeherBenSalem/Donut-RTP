package io.nightbeam.rtplab;

import io.nightbeam.donutrtp.api.RtpPreTeleportEvent;
import io.nightbeam.donutrtp.api.RtpTeleportEvent;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Prints DonutRTP's API events so the runner can check the source and destination of every teleport. */
final class ApiListener implements Listener {
    private final RtpLabPlugin plugin;

    ApiListener(RtpLabPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPre(RtpPreTeleportEvent event) {
        plugin.report("event pre " + event.getPlayer().getName() + " " + event.getDestination() + " " + event.getSource());
    }

    @EventHandler
    public void onTeleport(RtpTeleportEvent event) {
        Location to = event.getTo();
        plugin.report(String.format(Locale.ROOT, "event teleport %s %s %s %s %d %d %d", event.getPlayer().getName(),
                event.getDestination(), event.getSource(), to.getWorld().getName(), to.getBlockX(), to.getBlockY(), to.getBlockZ()));
    }
}
