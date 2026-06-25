package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.LanguageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /kila clear [玩家名]：清除对话历史（clear.self / clear.other 权限）。
 */
public final class ClearCommand {

    private ClearCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        String senderName = sender instanceof Player p ? p.getName() : "Console";

        if (args.length >= 2) {
            if (!PluginPermissionEnum.CLEAR_OTHER.hasPermission(sender)) {
                sender.sendMessage(lm.getPermissionClearOther());
                return;
            }

            String targetPlayerName = args[1];
            Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
            UUID targetPlayerId;

            if (targetPlayer != null) {
                targetPlayerId = targetPlayer.getUniqueId();
                targetPlayerName = targetPlayer.getName();
            } else {
                targetPlayerId = plugin.getServer().getOfflinePlayer(targetPlayerName).getUniqueId();
            }

            plugin.getConversationManager().clearAllHistory(targetPlayerId);
            sender.sendMessage(lm.replacePlaceholders(lm.getCommandClearOtherSuccess(), "player", targetPlayerName));
            PluginLoggerUtil.info("命令", lm.replacePlaceholders(lm.getLogClearOtherLogged(), "sender", senderName, "player", targetPlayerName));
            return;
        }

        if (!PluginPermissionEnum.CLEAR_SELF.hasPermission(sender)) {
            sender.sendMessage(lm.getPermissionClearSelf());
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lm.getCommandClearConsoleHint());
            return;
        }

        plugin.getConversationManager().clearAllHistory(player.getUniqueId());
        player.sendMessage(lm.getCommandClearSelfSuccess());
        PluginLoggerUtil.info("命令", lm.replacePlaceholders(lm.getLogClearSelfLogged(), "player", player.getName()));
    }
}
