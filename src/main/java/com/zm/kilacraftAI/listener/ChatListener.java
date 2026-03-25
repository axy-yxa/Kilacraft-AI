package com.zm.kilacraftAI.listener;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.handler.impl.PlayerResponseHandler;
import com.zm.kilacraftAI.util.AIRequestValidator;
import com.zm.kilacraftAI.util.MessageUtil;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天监听器
 *
 * @author Zm_Mmm
 * @since 2026-03-24 17:22:06
 */
public class ChatListener implements Listener {

    private final KilacraftAI plugin;
    private final Map<UUID, Boolean> chatMode;
    private final Map<UUID, Deque<Message>> history; // 历史对话记录
    private final AIRequestValidator validator;

    public ChatListener(KilacraftAI plugin) {
        this.plugin = plugin;
        this.chatMode = new HashMap<>();
        this.history = new ConcurrentHashMap<>();
        this.validator = new AIRequestValidator(plugin);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
            
        // 检查是否处于连续对话模式
        if (chatMode.getOrDefault(playerId, false)) {
            event.setCancelled(true);
                
            // 检查是否启用了连续对话模式
            if (!plugin.getConfigManager().isEnableChatCommand()) {
                player.sendMessage("§c连续对话模式已被禁用！请使用 /kilacraft <消息> 与 " + MessageUtil.getAIName() + " 对话。");
                chatMode.put(playerId, false);
                return;
            }
                            
            // 检查世界限制
            if (!validator.checkWorldLimitAndNotify(player, "连续对话模式")) {
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
                        if (!validator.checkWorldLimitAndNotify(player, "关键词触发")) {
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
     */
    private void handleAIRequest(Player player, UUID playerId, String message) {
        // 检查冷却时间
        if (!validator.checkCooldownAndNotify(player)) {
            return;
        }

        // 获取历史记录
        Deque<Message> playerHistory = validator.getOrCreateHistory(history, playerId);

        // 调试模式：打印当前历史记录
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 玩家 " + player.getName() + " 的历史记录数量：" + playerHistory.size());
        }

        // 发送消息到 AI（带历史记录）
        MessageUtil.sendThinkingMessage(player);

        // 立即更新冷却时间（在发送请求时）
        validator.startCooldown(playerId);

        // 创建玩家响应处理器
        AIResponseHandler handler = new PlayerResponseHandler(player, message, playerHistory);
        
        // 使用统一的 API 处理请求
        plugin.getDeepSeekAPI().processRequest(message, player.getName(), playerHistory, handler)
            .thenAccept(fullResponse -> {
                // 保存对话到历史记录
                validator.saveToHistory(playerHistory, message, fullResponse);
            }).exceptionally(throwable -> {
                player.sendMessage("§c发生错误：" + throwable.getMessage());
                return null;
            });
    }

    /**
     * 从消息中移除关键词
     */
    private String removeKeyword(String message, String keyword) {
        return message.replace(keyword, "").trim();
    }

    public void setChatMode(UUID playerId, boolean enabled) {
        chatMode.put(playerId, enabled);
    }

    public boolean isInChatMode(UUID playerId) {
        return chatMode.getOrDefault(playerId, false);
    }

    /**
     * 获取玩家的历史对话记录
     */
    public Deque<Message> getHistory(UUID playerId) {
        return history.get(playerId);
    }

    /**
     * 设置玩家的历史对话记录
     */
    public void setHistory(UUID playerId, Deque<Message> historyDeque) {
        history.put(playerId, historyDeque);
    }
    
    /**
     * 获取历史记录 Map（用于线程安全的操作）
     * @return 历史记录 Map
     */
    public Map<UUID, Deque<Message>> getHistoryMap() {
        return history;
    }

    /**
     * 清除玩家的历史对话记录
     */
    public void clearHistory(UUID playerId) {
        history.remove(playerId);
    }

    /**
     * 玩家退出游戏时自动关闭连续对话模式
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 如果玩家处于连续对话模式，自动退出
        if (chatMode.getOrDefault(playerId, false)) {
            chatMode.put(playerId, false);

            // 调试模式日志
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] 玩家 " + player.getName() + " 退出游戏，已自动关闭连续对话模式");
            }
        }
    }

    /**
     * 消息类
     */
    @Getter
    public static class Message {
        private final String role;
        private final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

    }
}
