package org.example.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import org.example.module.RedstoneNotifierModule;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
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
                        "discord on/off",
                        "triggerDelay [delay in ticks]"
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
                .then(literal("discord")
                        .then(argument("toggle", toggle())
                                .executes(c -> {
                                    PLUGIN_CONFIG.discordNotifications = getToggle(c, "toggle");
                                    c.getSource().getEmbed()
                                            .title("Discord Notifications " + toggleStrCaps(PLUGIN_CONFIG.discordNotifications));
                                }))
                        .then(literal("role")
                                .then(literal("add")
                                        .then(argument("roleId", integer()).executes(c -> {
                                                    long roleId = getInteger(c, "roleId");
                                                    if (PLUGIN_CONFIG.rolesToPing.contains(roleId)) {
                                                        c.getSource().getEmbed()
                                                                .title("Role ID " + roleId + " is already in the notification list.");
                                                        return ERROR;
                                                    }
                                                    PLUGIN_CONFIG.rolesToPing.add(roleId);
                                                    c.getSource().getEmbed()
                                                            .title("Added Role ID " + roleId + " to the notification list.");
                                                    return OK;
                                                })
                                        )
                                )
                                .then(literal("remove")
                                        .then(argument("roleId", integer()).executes(c -> {
                                            long roleId = getInteger(c, "roleId");
                                            if (!PLUGIN_CONFIG.rolesToPing.contains(roleId)) {
                                                c.getSource().getEmbed()
                                                        .title("Role ID " + roleId + " is not in the notification list.");
                                                return ERROR;
                                            }
                                            PLUGIN_CONFIG.rolesToPing.remove(roleId);
                                            c.getSource().getEmbed()
                                                    .title("Removed Role ID " + roleId + " from the notification list.");
                                            return OK;
                                        }))
                                        .then(literal("list")
                                                .executes(c -> {
                                                    if (PLUGIN_CONFIG.rolesToPing.isEmpty()) {
                                                        c.getSource().getEmbed()
                                                                .title("No Role IDs in the notification list.");
                                                        return OK;
                                                    }
                                                    StringBuilder rolesList = new StringBuilder();
                                                    for (Long roleId : PLUGIN_CONFIG.rolesToPing) {
                                                        rolesList.append(roleId).append("\n");
                                                    }
                                                    c.getSource().getEmbed()
                                                            .title("Role IDs in the notification list:")
                                                            .description(rolesList.toString());
                                                    return OK;
                                                })
                                        )
                                )
                        )
                )
                .then(literal("triggerDelay").executes(c -> {
                    int ticks = PLUGIN_CONFIG.triggerDelay;
                    c.getSource().getEmbed()
                            .title("Current Trigger Delay: " + ticks + " ticks");
                }).then(argument("ticks", integer()).executes(c -> {
                    int ticks = getInteger(c, "ticks");
                    PLUGIN_CONFIG.triggerDelay = ticks;
                    c.getSource().getEmbed()
                            .title("Trigger Delay set to " + ticks + " ticks");
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
                .primaryColor()
                .addField("Enabled", toggleStr(PLUGIN_CONFIG.enabled))
                .addField("Discord Notification", PLUGIN_CONFIG.discordNotifications ? "On" : "Off")
                .addField("Trigger Delay", PLUGIN_CONFIG.triggerDelay + " ticks")
                .addField("Roles to Ping", PLUGIN_CONFIG.rolesToPing.isEmpty() ? "None" :
                        String.join(", ", PLUGIN_CONFIG.rolesToPing.stream().map(Object::toString).toList()));
    }
}
