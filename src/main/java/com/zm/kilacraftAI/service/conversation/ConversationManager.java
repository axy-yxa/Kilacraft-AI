package com.zm.kilacraftAI.service.conversation;

import com.zm.kilacraftAI.db.service.ConversationPersistenceService;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

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
    @Setter
    private ConversationPersistenceService persistenceService;

    /**
     * 玩家连续对话模式状态（线程安全，ChatListener 异步线程 + 主线程并发访问）
     */
    private final Map<UUID, Boolean> chatMode = new ConcurrentHashMap<>();

    /**
     * 玩家历史对话记录（普通命令和聊天模式）
     */
    private final Map<UUID, Deque<Message>> history = new ConcurrentHashMap<>();

    /**
     * 插件命令的历史记录（key: UUID_人格）
     */
    private final Map<String, Deque<Message>> pluginCommandHistory = new ConcurrentHashMap<>();

    /**
     * 已执行 clear 的玩家集合（一次性消耗标记）
     * <p>
     * 玩家执行 /ai clear 后加入此集合，阻止 {@link com.zm.kilacraftAI.db.service.ConversationPersistenceService#loadHistoryIfNeeded}
     * 从 DB 加载旧历史。标记在下次 loadHistoryIfNeeded 时一次性消耗（移除），
     * 在玩家下线时也会被清理。
     * </p>
     */
    private final Set<UUID> clearedPlayers = ConcurrentHashMap.newKeySet();

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
     * 每个玩家历史记录的最大条数
     * <p>超出时从头部移除最旧记录，防止长期运行导致 OOM。</p>
     */
    private static final int MAX_HISTORY_SIZE = 100;

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
     * 获取或创建玩家的历史记录，并自动截断超出上限的旧记录
     *
     * @param playerId 玩家 UUID
     * @return 历史记录队列
     */
    public Deque<Message> getOrCreateHistory(UUID playerId) {
        Deque<Message> deque = history.computeIfAbsent(playerId, k -> new ConcurrentLinkedDeque<>());
        trimHistory(deque);
        return deque;
    }

    /**
     * 获取玩家的历史记录（只读，不创建）
     * <p>用于判断历史是否存在，如 ConversationPersistenceService 的 DB 加载决策。</p>
     *
     * @param playerId 玩家 UUID
     * @return 历史记录队列，不存在则返回 null
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
     * 获取或创建插件命令的历史记录，并自动截断超出上限的旧记录
     *
     * @param historyKey 历史记录 key（格式：UUID_人格）
     * @return 历史记录队列
     */
    public Deque<Message> getOrCreatePluginHistory(String historyKey) {
        Deque<Message> deque = pluginCommandHistory.computeIfAbsent(historyKey, k -> new ConcurrentLinkedDeque<>());
        trimHistory(deque);
        return deque;
    }

    /**
     * 获取插件命令历史记录
     *
     * @param historyKey 历史记录 key（格式：UUID_人格）
     * @return 历史记录队列，如果不存在则返回 null
     */
    public Deque<Message> getPluginHistory(String historyKey) {
        return pluginCommandHistory.get(historyKey);
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
     * 截断历史记录到最大容量（从头部移除最旧记录）
     */
    private void trimHistory(Deque<Message> deque) {
        if (deque.size() <= MAX_HISTORY_SIZE) return;
        while (deque.size() > MAX_HISTORY_SIZE) {
            deque.pollFirst();
        }
    }

    /**
     * 清除指定玩家的所有历史记录（包括普通和插件命令），并标记为 cleared
     * <p>
     * cleared 标记会阻止后续 {@code loadHistoryIfNeeded} 从 DB 加载旧历史，
     * 使玩家从空白上下文开始新对话。标记在下次 loadHistoryIfNeeded 时一次性消耗。
     * </p>
     *
     * @param playerId 玩家 UUID
     */
    public void clearAllHistory(UUID playerId) {
        clearHistory(playerId);
        clearPluginHistory(playerId);
        clearedPlayers.add(playerId);
    }

    /**
     * 检查并消耗 cleared 标记
     * <p>
     * 如果玩家在 cleared 集合中，移除标记并返回 true；否则返回 false。
     * 此方法用于 {@code loadHistoryIfNeeded} 中判断是否应跳过 DB 加载。
     * </p>
     *
     * @param playerId 玩家 UUID
     * @return true 表示该玩家刚执行过 clear，应跳过 DB 加载
     */
    public boolean consumeCleared(UUID playerId) {
        return clearedPlayers.remove(playerId);
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

        // 5. 清理 cleared 标记（下线后再上线应正常加载 DB 历史）
        clearedPlayers.remove(playerId);
    }
}
