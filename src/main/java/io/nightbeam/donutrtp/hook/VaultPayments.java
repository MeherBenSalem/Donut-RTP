package io.nightbeam.donutrtp.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Only loaded when Vault is installed. */
final class VaultPayments implements Payments {
    private final Economy economy;

    private VaultPayments(Economy economy) {
        this.economy = economy;
    }

    static Payments create() {
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : new VaultPayments(provider.getProvider());
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean has(Player player, double amount) {
        return economy.has(player, amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public void refund(Player player, double amount) {
        economy.depositPlayer(player, amount);
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }
}
