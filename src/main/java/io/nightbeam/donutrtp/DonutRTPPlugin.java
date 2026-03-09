package io.nightbeam.donutrtp;

import io.nightbeam.donutrtp.command.RtpCommand;
import io.nightbeam.donutrtp.config.ConfigManager;
import io.nightbeam.donutrtp.gui.GuiManager;
import io.nightbeam.donutrtp.listener.PlayerMoveCancelListener;
import io.nightbeam.donutrtp.rtp.RtpManager;
import io.nightbeam.donutrtp.util.FoliaCompat;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DonutRTPPlugin extends JavaPlugin {

    private FoliaCompat foliaCompat;
    private ConfigManager configManager;
    private GuiManager guiManager;
    private RtpManager rtpManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.foliaCompat = new FoliaCompat(this);
        this.configManager = new ConfigManager(this);
        this.configManager.reload();

        this.rtpManager = new RtpManager(this, foliaCompat, configManager);
        this.guiManager = new GuiManager(configManager, rtpManager);

        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(new PlayerMoveCancelListener(rtpManager), this);

        PluginCommand rtp = getCommand("rtp");
        if (rtp != null) {
            RtpCommand command = new RtpCommand(configManager, guiManager);
            rtp.setExecutor(command);
            rtp.setTabCompleter(command);
        } else {
            getLogger().severe("Command 'rtp' is missing in plugin.yml");
        }

        getLogger().info("DonutRTP enabled. Folia mode: " + foliaCompat.isFolia());
    }

    @Override
    public void onDisable() {
        if (rtpManager != null) {
            rtpManager.shutdown();
        }
        if (foliaCompat != null) {
            foliaCompat.shutdown();
        }
    }
}
