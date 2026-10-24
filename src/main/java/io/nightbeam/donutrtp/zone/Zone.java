package io.nightbeam.donutrtp.zone;

/** An RTP zone from zones.yml. */
public record Zone(String id, boolean enabled, String destination, Mode mode, int countdown, int minPlayers,
                   int spread, int cooldown, boolean cancelOnMove, String permission, boolean showTitle,
                   boolean showActionbar, boolean showBossbar, ZoneArea area) {
    public enum Mode {
        /** One shared countdown; everyone inside when it ends lands together. */
        GROUP,
        /** Each player's own countdown starts when they walk in. */
        INDIVIDUAL
    }

    /** A new zone with the defaults /rtpadmin zone create uses. */
    public static Zone create(String id, String destination, ZoneArea area) {
        return new Zone(id, true, destination, Mode.GROUP, 10, 1, 3, 30, false, "", true, true, true, area);
    }

    public Builder edit() {
        return new Builder(this);
    }

    /** Copies a zone with some values changed. */
    public static final class Builder {
        private final String id;
        public boolean enabled;
        public String destination;
        public Mode mode;
        public int countdown;
        public int minPlayers;
        public int spread;
        public int cooldown;
        public boolean cancelOnMove;
        public String permission;
        public boolean showTitle;
        public boolean showActionbar;
        public boolean showBossbar;
        public ZoneArea area;

        private Builder(Zone zone) {
            id = zone.id;
            enabled = zone.enabled;
            destination = zone.destination;
            mode = zone.mode;
            countdown = zone.countdown;
            minPlayers = zone.minPlayers;
            spread = zone.spread;
            cooldown = zone.cooldown;
            cancelOnMove = zone.cancelOnMove;
            permission = zone.permission;
            showTitle = zone.showTitle;
            showActionbar = zone.showActionbar;
            showBossbar = zone.showBossbar;
            area = zone.area;
        }

        public Zone build() {
            return new Zone(id, enabled, destination, mode, countdown, minPlayers, spread, cooldown, cancelOnMove,
                    permission, showTitle, showActionbar, showBossbar, area);
        }
    }
}
