package io.nightbeam.donutrtp.hook;

import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Money for destinations with a cost. Vault's types stay in {@link VaultPayments}, loaded only with Vault. */
public interface Payments {
    Payments NONE = new Payments() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean has(Player player, double amount) {
            return true;
        }

        @Override
        public boolean withdraw(Player player, double amount) {
            return true;
        }

        @Override
        public void refund(Player player, double amount) {
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }
    };

    boolean available();

    boolean has(Player player, double amount);

    boolean withdraw(Player player, double amount);

    void refund(Player player, double amount);

    String format(double amount);

    static Payments create(Logger log) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return NONE;
        }
        try {
            Payments payments = VaultPayments.create();
            if (payments == null) {
                log.info("Vault is installed but no economy plugin is registered; destination costs are ignored.");
                return NONE;
            }
            log.info("Hooked into Vault: destinations can cost money.");
            return payments;
        } catch (Throwable e) {
            log.warning("Vault is installed but could not be hooked: " + e);
            return NONE;
        }
    }
}
