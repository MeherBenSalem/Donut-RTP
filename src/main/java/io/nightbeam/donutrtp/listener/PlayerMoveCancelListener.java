package io.nightbeam.donutrtp.listener;

import io.nightbeam.donutrtp.rtp.RtpManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class PlayerMoveCancelListener implements Listener {

    private final RtpManager rtpManager;

    public PlayerMoveCancelListener(RtpManager rtpManager) {
        this.rtpManager = rtpManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        rtpManager.cancelWarmupIfMoving(event.getPlayer());
    }
}
