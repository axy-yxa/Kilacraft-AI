package com.zm.kilacraftAI.manager;

import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话管理器
 *
 * <p>统一管理玩家的对话状态、历史记录和插件命令历史</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-26
 */
public class ConversationManager {

    /**
     * 玩家连续对话模式状态
     */
    private final Map<UUID, Boolean> chatMode = new HashMap<>();

    /**
     * 玩家历史对话记录（普通命令和聊天模式）
     */
    @Getter
    private final Map<UUID, Deque<Message>> history = new ConcurrentHashMap<>();

    /**
     * 插件命令的历史记录（key: UUID_人格）
     */
    @Getter
    private final Map<String, Deque<Message>> pluginCommandHistory = new ConcurrentHashMap<>();

    /**
     * AI 最新回复缓存（key: UUID_人格，value: AI 回复内容）
     * <p>
     * 特性：
     * - 只保存每个"玩家 UUID_人格"的最新一条 AI 回复
     * - 对话结束后保存，新的自动覆盖旧的
     * - 被读取后自动清除
     * </p>
     */
    @Getter
    private final Map<String, String> latestAIResponses = new ConcurrentHashMap<>();

    /**
     * 消息记录类
     */
    @Getter
    public static class Message {
        public final String role;
        public final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

    }

    /**
     * 设置玩家的连续对话模式状态
     *
     * @param playerId 玩家 UUID
     * @param enabled  是否启用
     */
    public void setChatMode(UUID playerId, boolean enabled) {
        chatMode.put(playerId, enabled);
    }

    /**
     * 检查玩家是否处于连续对话模式
     *
     * @param playerId 玩家 UUID
     * @return 是否处于连续对话模式
     */
    public boolean isInChatMode(UUID playerId) {
        return chatMode.getOrDefault(playerId, false);
    }

    /**
     * 获取或创建玩家的历史记录
     *
     * @param playerId 玩家 UUID
     * @return 历史记录队列
     */
    public Deque<Message> getOrCreateHistory(UUID playerId) {
        return history.computeIfAbsent(playerId, k -> new ArrayDeque<>());
    }

    /**
     * 获取玩家的历史记录
     *
     * @param playerId 玩家 UUID
     * @return 历史记录队列，如果不存在则返回 null
     */
    public Deque<Message> getHistory(UUID playerId) {
        return history.get(playerId);
    }

    /**
     * 设置玩家的历史记录
     *
     * @param playerId     玩家 UUID
     * @param historyDeque 历史记录队列
     */
    public void setHistory(UUID playerId, Deque<Message> historyDeque) {
        history.put(playerId, historyDeque);
    }

    /**
     * 清除玩家的普通对话历史记录
     *
     * @param playerId 玩家 UUID
     */
    public void clearHistory(UUID playerId) {
        history.remove(playerId);
    }

    /**
     * 获取或创建插件命令的历史记录
     *
     * @param historyKey 历史记录 key（格式：UUID_人格）
     * @return 历史记录队列
     */
    public Deque<Message> getOrCreatePluginHistory(String historyKey) {
        return pluginCommandHistory.computeIfAbsent(historyKey, k -> new ArrayDeque<>());
    }

    /**
     * 清除指定玩家的插件命令历史记录
     *
     * @param playerId 玩家 UUID
     */
    public void clearPluginHistory(UUID playerId) {
        pluginCommandHistory.entrySet().removeIf(entry -> entry.getKey().startsWith(playerId.toString()));
    }

    /**
     * 清除指定玩家的所有历史记录（包括普通和插件命令）
     *
     * @param playerId 玩家 UUID
     */
    public void clearAllHistory(UUID playerId) {
        clearHistory(playerId);
        clearPluginHistory(playerId);
    }
    
    /**
     * 获取指定玩家的插件命令历史中的最新 AI 回复
     * 
     * @param playerId 玩家 UUID
     * @param personality 人偶类型
     * @return 最新的 AI 回复消息，如果不存在则返回 null
     */
    public String getLatestAIResponse(UUID playerId, String personality) {
        String key = generatePluginHistoryKey(playerId, personality);
        Deque<Message> history = pluginCommandHistory.get(key);
        
        if (history == null || history.isEmpty()) {
            return null;
        }
        
        // 获取最后一条 assistant 角色的消息
        Message lastMessage = null;
        for (Message msg : history) {
            if ("assistant".equals(msg.getRole())) {
                lastMessage = msg;
            }
        }
        
        return lastMessage != null ? lastMessage.getContent() : null;
    }

    /**
     * 保存 AI 回复到最新回复缓存
     * <p>
     * 在对话结束时调用，保存 AI 的最新回复
     * </p>
     * 
     * @param playerId 玩家 UUID
     * @param personality 人偶类型
     * @param response AI 回复内容
     */
    public void saveLatestAIResponse(UUID playerId, String personality, String response) {
        String key = generatePluginHistoryKey(playerId, personality);
        latestAIResponses.put(key, response);
    }

    /**
     * 获取并清除 AI 最新回复
     * <p>
     * 用于自定义占位符解析，获取后会清除该回复
     * </p>
     * 
     * @param playerId 玩家 UUID
     * @param personality 人偶类型
     * @return AI 回复内容，如果不存在则返回 null
     */
    public String pollLatestAIResponse(UUID playerId, String personality) {
        String key = generatePluginHistoryKey(playerId, personality);
        // 先获取值，然后删除
        return latestAIResponses.remove(key);
    }
    
    /**
     * 生成插件命令历史记录键
     * 
     * @param playerId 玩家 UUID
     * @param personality 人偶类型
     * @return 历史记录键（格式：UUID_人格）
     */
    private String generatePluginHistoryKey(UUID playerId, String personality) {
        return playerId.toString() + "_" + personality;
    }

    /**
     * 玩家退出游戏时清理相关状态
     *
     * @param player 退出游戏的玩家
     */
    public void onPlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();

        // 退出连续对话模式
        chatMode.remove(playerId);

        // 清理离线玩家的历史记录，释放内存
        clearAllHistory(playerId);

        // 清理最新AI回复缓存
        latestAIResponses.keySet().removeIf(key -> key.startsWith(playerId.toString()));
    }
}
