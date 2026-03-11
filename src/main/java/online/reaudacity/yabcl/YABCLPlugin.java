package online.reaudacity.yabcl;

import online.reaudacity.yabcl.log.YabclLogger;
import org.bukkit.plugin.java.JavaPlugin;

public final class YABCLPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        YabclLogger.info("Successfully loaded YABCL!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        YabclLogger.info("Disabled YABCL!");
    }
}
