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
     * 玩家退出游戏时清理相关状态
     *
     * @param player 退出游戏的玩家
     */
    public void onPlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();

        // 退出连续对话模式
        chatMode.remove(playerId);

        // TODO 可选：清理离线玩家的历史记录（如果需要节省内存）
        // clearAllHistory(playerId);
    }
}
