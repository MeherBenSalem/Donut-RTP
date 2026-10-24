package io.nightbeam.donutrtp.gui;

import io.nightbeam.donutrtp.api.RtpSource;
import io.nightbeam.donutrtp.config.Messages;
import io.nightbeam.donutrtp.config.Settings;
import io.nightbeam.donutrtp.destination.Destination;
import io.nightbeam.donutrtp.hook.HeadDatabaseHook;
import io.nightbeam.donutrtp.hook.Payments;
import io.nightbeam.donutrtp.platform.Scheduler;
import io.nightbeam.donutrtp.platform.Text;
import io.nightbeam.donutrtp.teleport.TeleportService;
import io.nightbeam.donutrtp.util.Durations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** The /rtp menu. Items show the player's cooldown, cost and locked state, refreshed every second while open. */
public final class RtpMenu implements Listener {
    private final Scheduler scheduler;
    private final Supplier<Settings> settings;
    private final Supplier<Messages> messages;
    private final Supplier<Payments> payments;
    private final TeleportService teleports;
    private final MenuIcons icons;

    /** Marks our inventories; one per open menu. */
    private static final class Holder implements InventoryHolder {
        final Map<Integer, String> slots = new HashMap<>();
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public RtpMenu(Scheduler scheduler, Supplier<Settings> settings, Supplier<Messages> messages,
                   Supplier<Payments> payments, TeleportService teleports, HeadDatabaseHook headDatabase, Logger log) {
        this.scheduler = scheduler;
        this.settings = settings;
        this.messages = messages;
        this.payments = payments;
        this.teleports = teleports;
        this.icons = new MenuIcons(headDatabase, log);
    }

    public void open(Player player) {
        Settings.Menu menu = settings.get().menu();
        Holder holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, menu.rows() * 9, Text.color(menu.title()));
        render(player, holder);
        player.openInventory(holder.inventory);
        scheduler.entityLater(player, () -> refresh(player, holder), 20L);
    }

    private void refresh(Player player, Holder holder) {
        if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) {
            return;
        }
        render(player, holder);
        scheduler.entityLater(player, () -> refresh(player, holder), 20L);
    }

    private void render(Player player, Holder holder) {
        Settings s = settings.get();
        Inventory inventory = holder.inventory;
        holder.slots.clear();
        if (s.menu().fillEmptySlots()) {
            ItemStack filler = new ItemStack(icons.material(s.menu().filler(), Material.GRAY_STAINED_GLASS_PANE));
            ItemMeta meta = filler.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(" ");
                filler.setItemMeta(meta);
            }
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }
        for (Destination destination : s.destinations().values()) {
            int slot = destination.item().slot();
            if (destination.item().hidden() || slot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot, icon(player, destination, s));
            holder.slots.put(slot, destination.id());
        }
    }

    private ItemStack icon(Player player, Destination destination, Settings s) {
        ItemStack item = icons.base(destination.item());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(Text.color(destination.item().name()));
        List<String> lore = new ArrayList<>();
        for (String line : destination.item().lore()) {
            lore.add(Text.color(line));
        }
        if (!destination.canUse(player)) {
            addLine(lore, s.menu().lockedLore());
        } else {
            long left = player.hasPermission(TeleportService.BYPASS_COOLDOWN) ? 0 : teleports.cooldownLeft(player, destination);
            if (left > 0) {
                addLine(lore, Messages.format(s.menu().cooldownLore(), "time", Durations.format(Durations.ceilSeconds(left))));
            }
        }
        if (destination.cost() > 0 && payments.get().available() && !player.hasPermission(TeleportService.BYPASS_COST)) {
            addLine(lore, Messages.format(s.menu().costLore(), "cost", payments.get().format(destination.cost())));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private static void addLine(List<String> lore, String line) {
        if (line != null && !line.isEmpty()) {
            if (!lore.isEmpty()) {
                lore.add("");
            }
            lore.add(Text.color(line));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        String id = holder.slots.get(event.getRawSlot());
        Destination destination = id == null ? null : settings.get().destinations().get(id);
        if (destination == null) {
            return;
        }
        if (!destination.canUse(player)) {
            messages.get().send(player, "destination-no-permission", "destination", destination.displayName());
            return;
        }
        scheduler.entity(player, () -> {
            player.closeInventory();
            teleports.request(player, destination, RtpSource.MENU);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
