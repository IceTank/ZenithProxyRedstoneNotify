package org.example;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.example.command.RedstoneNotifyCommand;
import org.example.module.RedstoneNotifierModule;

@Plugin(
    id = "redstone-lamp-notifier",
    version = BuildConstants.VERSION,
    description = "Notifies when a redstone lamp with sign text is activated",
    url = "https://github.com/rfresh2/ZenithProxyExamplePlugin",
    authors = {"IceTank"},
    mcVersions = {"1.21.4"} // to indicate any MC version: @Plugin(mcVersions = "*")
                            // if you touch packet classes, you almost certainly need to pin to a single mc version
)
public class RedstoneLampNotifier implements ZenithProxyPlugin {
    // public static for simple access from modules and commands
    // or alternatively, you could pass these around in constructors
    public static RedstoneNotifierConfig PLUGIN_CONFIG;
    public static ComponentLogger LOG;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        LOG.info("Redstone Lamp Notifier Plugin loading...");
        // initialize any configurations before modules or commands might need to read them
        PLUGIN_CONFIG = pluginAPI.registerConfig("redstone-lamp-notification", RedstoneNotifierConfig.class);
        pluginAPI.registerCommand(new RedstoneNotifyCommand());
        pluginAPI.registerModule(new RedstoneNotifierModule());
        LOG.info("Redstone Lamp Notifier Plugin loaded.");
    }
}
