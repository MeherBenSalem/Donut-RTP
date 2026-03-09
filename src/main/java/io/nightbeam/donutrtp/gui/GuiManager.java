package io.nightbeam.donutrtp.gui;

import io.nightbeam.donutrtp.config.ConfigManager;
import io.nightbeam.donutrtp.config.Settings;
import io.nightbeam.donutrtp.rtp.RtpManager;
import io.nightbeam.donutrtp.rtp.WorldType;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GuiManager implements Listener {

    private static final String TITLE = "ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ";

    private final ConfigManager configManager;
    private final RtpManager rtpManager;

    public GuiManager(ConfigManager configManager, RtpManager rtpManager) {
        this.configManager = configManager;
        this.rtpManager = rtpManager;
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, TITLE);
        Settings settings = configManager.settings();

        if (settings.fillEmptySlots()) {
            ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(i, filler);
            }
        }

        inv.setItem(11, createItem(Material.GRASS_BLOCK, "§aOverworld RTP", List.of(
                "§7Click to randomly teleport",
                "§7in the Overworld"
        )));
        inv.setItem(13, createItem(Material.NETHERRACK, "§cNether RTP", List.of(
                "§7Click to randomly teleport",
                "§7in the Nether"
        )));
        inv.setItem(15, createItem(Material.END_STONE, "§dEnd RTP", List.of(
                "§7Click to randomly teleport",
                "§7in The End"
        )));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getType() != InventoryType.CHEST) {
            return;
        }
        if (!TITLE.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        int slot = event.getRawSlot();
        WorldType selected = switch (slot) {
            case 11 -> WorldType.OVERWORLD;
            case 13 -> WorldType.NETHER;
            case 15 -> WorldType.END;
            default -> null;
        };

        if (selected == null) {
            return;
        }

        player.closeInventory();
        rtpManager.startTeleport(player, selected);
    }

    private ItemStack createItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
}
