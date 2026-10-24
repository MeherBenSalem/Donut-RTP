package io.nightbeam.donutrtp.api;

/** What started a random teleport. */
public enum RtpSource {
    /** /rtp or /rtp &lt;destination&gt;. */
    COMMAND,
    /** A click in the /rtp menu. */
    MENU,
    /** An admin with /rtp player, or another plugin. */
    ADMIN,
    /** An RTP zone. */
    ZONE
}
