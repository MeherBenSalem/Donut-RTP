package io.nightbeam.donutrtp.config;

public record HeadSettings(String texture, String uuid, String player) {

    public boolean hasTexture() {
        return texture != null && !texture.isBlank();
    }

    public boolean hasUuid() {
        return uuid != null && !uuid.isBlank();
    }

    public boolean hasPlayer() {
        return player != null && !player.isBlank();
    }

    public boolean isPresent() {
        return hasTexture() || hasUuid() || hasPlayer();
    }
}
