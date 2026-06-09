package io.nightbeam.donutrtp.config;

import org.bukkit.Sound;

public record TeleportSoundSettings(boolean enabled, Sound sound, float volume, float pitch) {
}
