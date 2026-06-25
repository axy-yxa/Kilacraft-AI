package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /kila chat：进入/退出连续对话模式。
 */
public final class ChatCommand {

    private ChatCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args, ConfigManager configManager) {
        LanguageManager lm = plugin.getLanguageManager();
        if (!configManager.isEnableChatCommand()) {
            sender.sendMessage(lm.getFeatureChatModeDisabled());
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lm.getFeatureChatModePlayerOnly());
            return;
        }

        UUID playerId = player.getUniqueId();
        ConversationManager convManager = plugin.getConversationManager();
        boolean inChatMode = convManager.isInChatMode(playerId);
        convManager.setChatMode(playerId, !inChatMode);

        if (!inChatMode) {
            player.sendMessage(lm.getFeatureChatModeEnter());
            player.sendMessage(lm.getFeatureChatModeEnterSubtitle());
            PluginLoggerUtil.info("命令", lm.replacePlaceholders(lm.getLogChatModeEntered(), "player", player.getName()));
        } else {
            player.sendMessage(lm.getFeatureChatModeExit());
            PluginLoggerUtil.info("命令", lm.replacePlaceholders(lm.getLogChatModeExited(), "player", player.getName()));
        }
    }
}
