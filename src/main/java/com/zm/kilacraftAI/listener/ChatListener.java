package com.zm.kilacraftAI.listener;

import com.zm.kilacraftAI.KilacraftAI;
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
    private final Map<UUID, Long> cooldowns;
    private final Map<UUID, Deque<Message>> history; // 历史对话记录

    public ChatListener(KilacraftAI plugin) {
        this.plugin = plugin;
        this.chatMode = new HashMap<>();
        this.cooldowns = new HashMap<>();
        this.history = new ConcurrentHashMap<>();
    }

    /**
     * 检查玩家是否可以在当前世界使用 AI
     */
    private boolean canUseAIInWorld(Player player) {
        String worldName = player.getWorld().getName();
        
        // 获取配置
        List<String> allowedWorlds = plugin.getConfigManager().getAllowedWorlds();
        List<String> bannedWorlds = plugin.getConfigManager().getBannedWorlds();
        
        // 优先检查禁止列表（如果在禁止列表中，直接返回 false）
        if (!bannedWorlds.isEmpty() && bannedWorlds.contains(worldName)) {
            return false;
        }
        
        // 再检查允许列表（如果为空表示所有世界都允许）
        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(worldName)) {
            return false;
        }
        
        return true;
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
                player.sendMessage("§c连续对话模式已被禁用！请使用 /kilacraft <消息> 与 Kilacraft-AI 对话。");
                chatMode.put(playerId, false);
                return;
            }
                            
            // 检查世界限制
            if (!canUseAIInWorld(player)) {
                // 调试模式：打印玩家信息
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("[DEBUG] [世界限制] 玩家 " + player.getName() + " 在禁止的世界 " + player.getWorld().getName() + " 尝试使用 Kilacraft-AI（连续对话模式）");
                }
                player.sendMessage("§c当前世界禁止使用 Kilacraft-AI！");
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
                        if (!canUseAIInWorld(player)) {
                            // 调试模式：打印玩家信息
                            if (plugin.getConfigManager().isDebugMode()) {
                                plugin.getLogger().warning("[DEBUG] [世界限制] 玩家 " + player.getName() + " 在禁止的世界 " + player.getWorld().getName() + " 尝试使用 Kilacraft-AI（关键词触发）");
                            }
                            player.sendMessage("§c当前世界禁止使用 Kilacraft-AI！");
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
        int cooldownSeconds = plugin.getConfigManager().getCooldownSeconds();
        long currentTime = System.currentTimeMillis();

        if (cooldownSeconds > 0 && cooldowns.containsKey(playerId)) {
            Long lastUsed = cooldowns.get(playerId);
            long timeLeft = (lastUsed + (cooldownSeconds * 1000L)) - currentTime;
            if (timeLeft > 0) {
                player.sendMessage("§c请等待 " + (timeLeft / 1000) + " 秒后再试！");
                return;
            }
        }

        // 获取历史记录
        Deque<Message> playerHistory = history.computeIfAbsent(playerId, k -> new ArrayDeque<>());

        // 调试模式：打印当前历史记录
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 玩家 " + player.getName() + " 的历史记录数量：" + playerHistory.size());
        }

        // 发送消息到 AI（带历史记录）
        player.sendMessage("§7[Kilacraft-AI] §f正在思考中...");

        // 立即更新冷却时间（在发送请求时）
        cooldowns.put(playerId, currentTime);

        plugin.getDeepSeekAPI().sendMessage(message, player.getName(), playerHistory).thenAccept(response -> {
            // 调试模式日志
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] 收到 AI 响应，长度：" + response.length());
            }

            player.sendMessage("§b[Kilacraft-AI] §f" + response);

            // 保存对话到历史记录
            int maxHistory = plugin.getConfigManager().getMaxHistory();
            if (maxHistory > 0) {
                // 添加用户消息
                playerHistory.add(new Message("user", message));
                // 添加 AI 回复
                playerHistory.add(new Message("assistant", response));

                // 保持历史记录不超过限制（每轮对话算 2 条）
                while (playerHistory.size() > maxHistory * 2) {
                    Message removed = playerHistory.removeFirst();
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("[DEBUG] 移除最早的历史记录：" + removed.getContent().substring(0, Math.min(20, removed.getContent().length())) + "...");
                    }
                }

                // 调试模式：打印保存后的历史记录
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] 已保存新对话，当前历史记录数量：" + playerHistory.size());
                }
            }
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