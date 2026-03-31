package com.zm.kilacraftAI.listener;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.handler.AIRequestHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.util.AIRequestValidator;
import com.zm.kilacraftAI.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Deque;
import java.util.UUID;

/**
 * 聊天监听器
 *
 * <p>仅负责监听和处理聊天事件，对话状态管理由 ConversationManager 负责</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-24 17:22:06
 */
public class ChatListener implements Listener {

    private final KilacraftAI plugin;
    private final AIRequestValidator validator;
    private final LanguageManager languageManager;
    private final AIRequestHandler aiRequestHandler;

    public ChatListener(KilacraftAI plugin) {
        this.plugin = plugin;
        this.validator = new AIRequestValidator(plugin);
        this.languageManager = plugin.getLanguageManager();
        this.aiRequestHandler = new AIRequestHandler(plugin);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ConversationManager convManager = plugin.getConversationManager();
        UUID playerId = player.getUniqueId();

        // 检查是否处于连续对话模式
        if (convManager.isInChatMode(playerId)) {
            event.setCancelled(true);

            // 检查是否启用了连续对话模式
            if (!plugin.getConfigManager().isEnableChatCommand()) {
                player.sendMessage(languageManager.getFeatureChatModeDisabled());
                convManager.setChatMode(playerId, false);
                return;
            }

            // 检查世界限制
            if (!validator.canUseAIInWorld(player)) {
                player.sendMessage(languageManager.replacePlaceholders(languageManager.getWorldBannedHint(), "ai_name", MessageUtil.getAIName()));
                return;
            }

            handleAIRequest(player, playerId, event.getMessage());
            return;
        }

        // 检查关键词触发（普通聊天模式）
        if (plugin.getConfigManager().isEnableTrigger()) {
            String message = event.getMessage();
            for (String keyword : plugin.getConfigManager().getTriggerKeywords()) {
                if (message.contains(keyword)) {
                    // 移除关键词，获取实际消息内容
                    String actualMessage = removeKeyword(message, keyword).trim();
                    if (!actualMessage.isEmpty()) {
                        // 检查世界限制
                        if (!validator.canUseAIInWorld(player)) {
                            player.sendMessage(languageManager.replacePlaceholders(languageManager.getWorldBannedHint(), "ai_name", MessageUtil.getAIName()));
                            return;
                        }
                        handleAIRequest(player, playerId, actualMessage);
                    }
                    break;
                }
            }
        }
    }

    /**
     * 处理 AI 请求（带冷却、历史记录等）
     *
     * <p>所有请求统一使用 LLM 意图识别，不再使用关键词匹配</p>
     */
    private void handleAIRequest(Player player, UUID playerId, String message) {
        // 检查冷却时间
        if (!validator.isCooldownReady(playerId)) {
            long remainingSeconds = validator.getRemainingCooldownSeconds(playerId);
            if (remainingSeconds > 0) {
                player.sendMessage(languageManager.replacePlaceholders(languageManager.getCooldownWarning(), "seconds", String.valueOf(remainingSeconds)));
            }
            return;
        }

        // 发送"正在思考"消息
        MessageUtil.sendThinkingMessage(player);

        // 立即更新冷却时间
        validator.startCooldown(playerId);

        // 获取或创建历史记录
        ConversationManager convManager = plugin.getConversationManager();
        Deque<ConversationManager.Message> playerHistory = convManager.getOrCreateHistory(playerId);

        // 调试模式：打印历史记录信息
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 玩家 " + player.getName() + " 的历史记录数量：" + playerHistory.size());
        }

        // 使用统一的 AI 请求处理器
        boolean enableAgent = plugin.getConfigManager().isAgentEnabled() && plugin.getConfigManager().isAgentEnableChatListener();
        aiRequestHandler.handleAIRequest(player, message, playerHistory, enableAgent);
    }

    /**
     * 从消息中移除关键词
     */
    private String removeKeyword(String message, String keyword) {
        return message.replace(keyword, "").trim();
    }

    /**
     * 玩家退出游戏时自动关闭连续对话模式
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getConversationManager().onPlayerQuit(player);
    }
}
