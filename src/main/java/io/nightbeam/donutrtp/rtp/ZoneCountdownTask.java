package io.nightbeam.donutrtp.rtp;

import io.nightbeam.donutrtp.config.ActionBarCooldownSoundSettings;
import io.nightbeam.donutrtp.config.ConfigManager;
import io.nightbeam.donutrtp.config.RtpZoneSettings;
import io.nightbeam.donutrtp.util.FoliaCompat;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

public final class ZoneCountdownTask {

    private final FoliaCompat foliaCompat;
    private final ConfigManager configManager;
    private final RtpManager rtpManager;
    private final ActionBarCooldownSoundSettings countdownSound;
    private final RtpZoneSettings zone;
    private final Player player;
    private final Runnable onCancelled;
    private final Runnable onFinished;

    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicInteger secondsLeft;

    public ZoneCountdownTask(
            FoliaCompat foliaCompat,
            ConfigManager configManager,
            RtpManager rtpManager,
            ActionBarCooldownSoundSettings countdownSound,
            RtpZoneSettings zone,
            Player player,
            Runnable onCancelled,
            Runnable onFinished
    ) {
        this.foliaCompat = foliaCompat;
        this.configManager = configManager;
        this.rtpManager = rtpManager;
        this.countdownSound = countdownSound;
        this.zone = zone;
        this.player = player;
        this.onCancelled = onCancelled;
        this.onFinished = onFinished;
        this.secondsLeft = new AtomicInteger(zone.countdownSeconds());
    }

    public RtpZoneSettings zone() {
        return zone;
    }

    public void start() {
        tick();
    }

    public void cancel(boolean sendCallback) {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        clearTitle();
        onFinished.run();
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
        if (!zone.contains(player.getLocation())) {
            cancel(true);
            return;
        }

        int left = secondsLeft.getAndDecrement();
        if (left <= 0) {
            if (active.compareAndSet(true, false)) {
                clearTitle();
                onFinished.run();
                rtpManager.teleportRandom(player, zone.worldType());
            }
            return;
        }

        showCountdownTitle(left);
        playCountdownSound();
        foliaCompat.runLaterForEntity(player, this::tick, 20L);
    }

    private void showCountdownTitle(int seconds) {
        String titleText = configManager.plainMessage("zone-countdown-title")
                .replace("%seconds%", String.valueOf(seconds));
        String subtitleText = configManager.plainMessage("zone-countdown-subtitle");
        Component title = LegacyComponentSerializer.legacySection().deserialize(titleText);
        Component subtitle = LegacyComponentSerializer.legacySection().deserialize(subtitleText);
        Title.Times times = Title.Times.times(
                Duration.ZERO,
                Duration.ofMillis(1100),
                Duration.ofMillis(200)
        );
        player.showTitle(Title.title(title, subtitle, times));
    }

    private void playCountdownSound() {
        if (!countdownSound.enabled()) {
            return;
        }
        player.playSound(
                player.getLocation(),
                countdownSound.sound(),
                countdownSound.volume(),
                countdownSound.pitch()
        );
    }

    private void clearTitle() {
        if (player.isOnline()) {
            player.clearTitle();
        }
    }
}
