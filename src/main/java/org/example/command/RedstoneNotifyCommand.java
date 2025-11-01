package org.example.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import org.example.module.RedstoneNotifierModule;

import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static org.example.RedstoneLampNotifier.PLUGIN_CONFIG;

public class RedstoneNotifyCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("lampNotify")
            .category(CommandCategory.MODULE)
            .description("""
                Toggles redstone lamp notification module settings.
                """)
            .usageLines(
                "on/off",
                "discord on/off"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("lampNotify")
            .then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.enabled = getToggle(c, "toggle");
                // make sure to sync so the module is actually toggled
                MODULE.get(RedstoneNotifierModule.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    // if no title is set, no embed response will be sent
                    // other properties like fields can be left unset without issues
                    .title("Lamp Notification " + toggleStrCaps(PLUGIN_CONFIG.enabled));
            }))
            .then(literal("discord").then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.discordNotifications = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Discord Notifications " + toggleStrCaps(PLUGIN_CONFIG.discordNotifications));
            })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
            .primaryColor()
            .addField("Enabled", toggleStr(PLUGIN_CONFIG.enabled))
            .addField("Discord Notification", PLUGIN_CONFIG.discordNotifications ? "On" : "Off");
    }
}
