package io.nightbeam.donutrtp.rtp;

import io.nightbeam.donutrtp.config.ConfigManager;
import io.nightbeam.donutrtp.util.FoliaCompat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;

public final class WarmupTask {

    private final FoliaCompat foliaCompat;
    private final ConfigManager configManager;
    private final Player player;
    private final Runnable onComplete;
    private final Runnable onCancelled;

    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicInteger secondsLeft;
    private final int initialSeconds;

    public WarmupTask(FoliaCompat foliaCompat, ConfigManager configManager, Player player,
                      int warmupSeconds, Runnable onComplete, Runnable onCancelled) {
        this.foliaCompat = foliaCompat;
        this.configManager = configManager;
        this.player = player;
        this.onComplete = onComplete;
        this.onCancelled = onCancelled;
        this.secondsLeft = new AtomicInteger(warmupSeconds);
        this.initialSeconds = warmupSeconds;
    }

    public void start() {
        tick();
    }

    public void cancel(boolean sendCallback) {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        if (sendCallback) {
            onCancelled.run();
        }
    }

    public boolean isActive() {
        return active.get();
    }

    private void tick() {
        if (!active.get()) {
            return;
        }
        if (!player.isOnline()) {
            cancel(false);
            return;
        }

        int left = secondsLeft.getAndDecrement();
        if (left <= 0) {
            if (active.compareAndSet(true, false)) {
                onComplete.run();
            }
            return;
        }

        player.sendMessage(configManager.message("countdown").replace("%seconds%", String.valueOf(left)));
        if (left == initialSeconds) {
            player.sendMessage(configManager.message("countdown-warning"));
        }

        foliaCompat.runLaterForEntity(player, this::tick, 20L);
    }
}
