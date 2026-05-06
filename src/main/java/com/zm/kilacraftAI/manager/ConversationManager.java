package com.zm.kilacraftAI.manager;

import com.zm.kilacraftAI.db.ConversationPersistenceService;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话管理器
 *
 * @author Zm_Mmm
 * @since 2026-03-26
 */
public class ConversationManager {

    /**
     * 对话持久化服务（可选，由 KilacraftAI 注入）
     */
    private ConversationPersistenceService persistenceService;

    /**
     * 玩家连续对话模式状态（线程安全，ChatListener 异步线程 + 主线程并发访问）
     */
    private final Map<UUID, Boolean> chatMode = new ConcurrentHashMap<>();

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
     * 保存 AI 回复到最新回复缓存
     * <p>
     * 在对话结束时调用，保存 AI 的最新回复
     * </p>
     *
     * @param playerId    玩家 UUID
     * @param personality 人偶类型
     * @param response    AI 回复内容
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
     * @param playerId    玩家 UUID
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
     * @param playerId    玩家 UUID
     * @param personality 人偶类型
     * @return 历史记录键（格式：UUID_人格）
     */
    private String generatePluginHistoryKey(UUID playerId, String personality) {
        return playerId.toString() + "_" + personality;
    }

    /**
     * 注入对话持久化服务
     *
     * @param persistenceService 持久化服务（可为 null，表示不持久化）
     */
    public void setPersistenceService(ConversationPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * 玩家退出游戏时清理相关状态
     *
     * <p>改造后流程：先 flush 持久化 → 再清理内存，确保不丢数据</p>
     *
     * @param player 退出游戏的玩家
     */
    public void onPlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();

        // 1. 先将内存中的历史异步写入 DB（新增）
        if (persistenceService != null) {
            persistenceService.flushPlayer(playerId);
        }

        // 2. 退出连续对话模式
        chatMode.remove(playerId);

        // 3. 清理离线玩家的历史记录，释放内存
        clearAllHistory(playerId);

        // 4. 清理最新AI回复缓存
        latestAIResponses.keySet().removeIf(key -> key.startsWith(playerId.toString()));
    }
}
