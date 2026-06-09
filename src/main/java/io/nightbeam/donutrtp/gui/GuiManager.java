package io.nightbeam.donutrtp.gui;

import io.nightbeam.donutrtp.config.ConfigManager;
import io.nightbeam.donutrtp.config.GuiItemSettings;
import io.nightbeam.donutrtp.config.Settings;
import io.nightbeam.donutrtp.rtp.RtpManager;
import io.nightbeam.donutrtp.rtp.WorldType;
import java.util.Map;
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
import org.bukkit.plugin.java.JavaPlugin;

public final class GuiManager implements Listener {

    private static final String TITLE = "ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ";

    private static final Map<WorldType, Material> DEFAULT_MATERIALS = Map.of(
            WorldType.OVERWORLD, Material.GRASS_BLOCK,
            WorldType.NETHER, Material.NETHERRACK,
            WorldType.END, Material.END_STONE
    );

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final RtpManager rtpManager;

    public GuiManager(JavaPlugin plugin, ConfigManager configManager, RtpManager rtpManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rtpManager = rtpManager;
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, TITLE);
        Settings settings = configManager.settings();

        if (settings.fillEmptySlots()) {
            ItemStack filler = createFillerItem();
            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(i, filler);
            }
        }

        for (WorldType worldType : WorldType.values()) {
            GuiItemSettings itemSettings = settings.guiItems().get(worldType);
            Material fallback = DEFAULT_MATERIALS.get(worldType);
            ItemStack item = GuiItemBuilder.build(itemSettings, fallback, plugin.getLogger());
            inv.setItem(itemSettings.slot(), item);
        }

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

        WorldType selected = worldTypeForSlot(event.getRawSlot());
        if (selected == null) {
            return;
        }

        player.closeInventory();
        rtpManager.startTeleport(player, selected);
    }

    private WorldType worldTypeForSlot(int slot) {
        Settings settings = configManager.settings();
        for (Map.Entry<WorldType, GuiItemSettings> entry : settings.guiItems().entrySet()) {
            if (entry.getValue().slot() == slot) {
                return entry.getKey();
            }
        }
        return null;
    }

    private ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
}
