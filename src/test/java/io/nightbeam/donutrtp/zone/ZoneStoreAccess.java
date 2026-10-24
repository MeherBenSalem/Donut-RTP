package io.nightbeam.donutrtp.zone;

import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/** Lets tests in other packages read zones. */
public final class ZoneStoreAccess {
    private ZoneStoreAccess() {
    }

    public static Map<String, Zone> read(ConfigurationSection zones) {
        return ZoneStore.read(zones, Logger.getLogger("test"));
    }
}
