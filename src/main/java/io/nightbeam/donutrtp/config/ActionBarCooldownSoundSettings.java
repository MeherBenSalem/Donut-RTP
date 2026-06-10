package io.nightbeam.donutrtp.config;

import org.bukkit.Sound;

public record ActionBarCooldownSoundSettings(boolean enabled, Sound sound, float volume, float pitch) {
}
