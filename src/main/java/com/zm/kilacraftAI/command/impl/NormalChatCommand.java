package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.ConversationSourceEnum;
import com.zm.kilacraftAI.common.util.AIRequestValidatorUtil;
import com.zm.kilacraftAI.common.util.MessageUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.db.service.ConversationPersistenceService;
import com.zm.kilacraftAI.handler.AIRequestHandler;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.UUID;

/**
 * /kila <消息>：AI 对话入口（无子命令时的默认行为）。
 *
 * @author Zm_Mmm
 * @since 2026-06-25
 */
public final class NormalChatCommand {

    private NormalChatCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getCommandRunPlayerOnly());
            return;
        }
        handlePlayer(plugin, player, args);
    }

    private static void handlePlayer(KilacraftAI plugin, Player player, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        AIRequestValidatorUtil validator = new AIRequestValidatorUtil(plugin);

        if (!validator.canUseAIInWorld(player)) {
            player.sendMessage(lm.replacePlaceholders(lm.getWorldBannedHint(), "ai_name", MessageUtil.getAIName()));
            return;
        }

        UUID playerId = player.getUniqueId();
        if (!validator.isCooldownReady(playerId)) {
            long remainingSeconds = validator.getRemainingCooldownSeconds(playerId);
            if (remainingSeconds > 0) {
                player.sendMessage(lm.replacePlaceholders(lm.getCooldownWarning(), "seconds", String.valueOf(remainingSeconds)));
            }
            return;
        }

        String message = String.join(" ", args);
        MessageUtil.sendThinkingMessage(player);
        validator.startCooldown(playerId);

        ConversationPersistenceService persistenceService = plugin.getPersistenceService();
        boolean enableAgent = plugin.getConfigManager().isAgentEnabled() && plugin.getConfigManager().isAgentEnableCommand();
        AIRequestHandler aiHandler = new AIRequestHandler(plugin);

        if (persistenceService != null) {
            ConversationManager convManager = plugin.getConversationManager();
            Deque<ConversationManager.Message> playerHistory = convManager.getOrCreateHistory(playerId);
            persistenceService.loadHistoryIfNeeded(playerId, "", loadedHistory -> {
                ConversationPersistenceService.mergeLoadedHistory(loadedHistory, playerHistory);
                PluginLoggerUtil.debug("命令", "玩家 {} 的历史记录数量：{}", player.getName(), playerHistory.size());
                aiHandler.handleAIRequest(player, message, playerHistory, enableAgent, false, ConversationSourceEnum.COMMAND);
            }, ConversationSourceEnum.COMMAND, ConversationSourceEnum.CHAT);
        } else {
            Deque<ConversationManager.Message> playerHistory = plugin.getConversationManager().getOrCreateHistory(playerId);
            PluginLoggerUtil.debug("命令", "玩家 {} 的历史记录数量：{}", player.getName(), playerHistory.size());
            aiHandler.handleAIRequest(player, message, playerHistory, enableAgent, false, ConversationSourceEnum.COMMAND);
        }
    }
}
