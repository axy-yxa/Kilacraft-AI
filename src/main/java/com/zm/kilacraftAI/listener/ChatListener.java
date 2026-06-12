package com.zm.kilacraftAI.listener;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.AIRequestValidatorUtil;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.db.service.ConversationPersistenceService;
import com.zm.kilacraftAI.common.enums.ConversationSourceEnum;
import com.zm.kilacraftAI.handler.AIRequestHandler;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.common.util.MessageUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
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
 * @since 2026-03-24
 */
public class ChatListener implements Listener {

    private final KilacraftAI plugin;
    private final AIRequestValidatorUtil validator;
    private final LanguageManager languageManager;
    private final AIRequestHandler aiRequestHandler;

    public ChatListener(KilacraftAI plugin) {
        this.plugin = plugin;
        this.validator = new AIRequestValidatorUtil(plugin);
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

            handleAIRequest(player, playerId, event.getMessage(), false);
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
                        // 公屏回复：仅关键词触发时根据配置决定是否广播
                        handleAIRequest(player, playerId, actualMessage, plugin.getConfigManager().isPublicReply());
                    }
                    break;
                }
            }
        }
    }

    /**
     * 处理 AI 请求
     *
     * @param publicReply 是否将AI回复广播给所有在线玩家
     */
    private void handleAIRequest(Player player, UUID playerId, String message, boolean publicReply) {
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

        Deque<ConversationManager.Message> h = plugin.getConversationManager().getHistory(playerId);
        PluginLoggerUtil.debug("聊天监听", "玩家 {} 的历史记录数量：{}", player.getName(), h != null ? h.size() : 0);

        // 使用统一的 AI 请求处理器
        boolean enableAgent = plugin.getConfigManager().isAgentEnabled() && plugin.getConfigManager().isAgentEnableChatListener();

        // 异步加载历史记录（Lazy Loading）
        ConversationPersistenceService persistenceService = plugin.getPersistenceService();
        if (persistenceService != null) {
            // 获取或创建内存历史（如果已有则跳过DB加载）
            ConversationManager convManager = plugin.getConversationManager();
            Deque<ConversationManager.Message> playerHistory = convManager.getOrCreateHistory(playerId);

            persistenceService.loadHistoryIfNeeded(playerId, "", loadedHistory -> {
                // 合并 DB 历史到内存：DB 历史在前，内存中的问候（如有）在后
                ConversationPersistenceService.mergeLoadedHistory(loadedHistory, playerHistory);
                aiRequestHandler.handleAIRequest(player, message, playerHistory, enableAgent, publicReply, ConversationSourceEnum.CHAT);
            }, ConversationSourceEnum.CHAT, ConversationSourceEnum.COMMAND);
        } else {
            // 无持久化服务，使用原有同步逻辑
            ConversationManager convManager = plugin.getConversationManager();
            Deque<ConversationManager.Message> playerHistory = convManager.getOrCreateHistory(playerId);
            aiRequestHandler.handleAIRequest(player, message, playerHistory, enableAgent, publicReply, ConversationSourceEnum.CHAT);
        }
    }

    /**
     * 从消息中移除关键词
     */
    private String removeKeyword(String message, String keyword) {
        return message.replace(keyword, "").trim();
    }

    /**
     * 玩家退出游戏时自动关闭连续对话模式并清理流式状态
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getConversationManager().onPlayerQuit(player);

        // 清理玩家的流式生成状态
        if (plugin.getStreamOutputManager() != null) {
            plugin.getStreamOutputManager().cancelGeneration(player);
        }

        // 清理玩家的 Scoreboard / BossBar（防止离线后定时器仍在运行导致内存泄漏）
        if (plugin.getResponsePipeline() != null && plugin.getResponsePipeline().getDispatcher() != null) {
            plugin.getResponsePipeline().getDispatcher().getScoreboardManager().removeSidebar(player.getUniqueId());
            plugin.getResponsePipeline().getDispatcher().getBossBarManager().removeBossBar(player.getUniqueId());
        }
    }
}
