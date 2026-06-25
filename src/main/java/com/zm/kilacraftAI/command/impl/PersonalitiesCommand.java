package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.LanguageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * /kila personalities reload：人格配置管理（personalities 权限）。
 */
public final class PersonalitiesCommand {

    private PersonalitiesCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        if (!PluginPermissionEnum.PERSONALITIES.hasPermission(sender)) {
            sender.sendMessage(lm.getPermissionPersonalities());
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(lm.getHelpPersonalities());
            return;
        }

        String subCommand = args[1].toLowerCase(Locale.ROOT);
        if ("reload".equals(subCommand)) {
            handleReload(plugin, sender, lm);
            return;
        } else {
            sender.sendMessage(lm.getCommandUnknownSubcommand() + subCommand);
            sender.sendMessage(lm.getCommandAvailableSubcommands());
            return;
        }
    }

    private static void handleReload(KilacraftAI plugin, CommandSender sender, LanguageManager lm) {
        String senderName = sender instanceof Player p ? p.getName() : "Console";
        try {
            plugin.getPersonalitiesConfigManager().reload();
            sender.sendMessage(lm.getCommandPersonalitiesReloadSuccess());
            sender.sendMessage(lm.replacePlaceholders(lm.getCommandPersonalitiesLoaded(), "count", String.valueOf(plugin.getPersonalitiesConfigManager().getAllPersonalities().size())));
            PluginLoggerUtil.info("命令", lm.replacePlaceholders(lm.getLogPersonalitiesReloaded(), "sender", senderName));
        } catch (Exception e) {
            sender.sendMessage(lm.getCommandPersonalitiesReloadFailure() + e.getMessage());
            PluginLoggerUtil.error("命令", lm.getLogAiRequestError() + e.getMessage(), e);
        }
    }
}
