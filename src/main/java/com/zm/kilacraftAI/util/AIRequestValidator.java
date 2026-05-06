package com.zm.kilacraftAI.util;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.db.ConversationPersistenceService;
import com.zm.kilacraftAI.db.ConversationSource;
import com.zm.kilacraftAI.manager.ConversationManager;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 请求验证工具类
 *
 * <p>封装通用的验证逻辑，包括：</p>
 * <ul>
 *     <li>世界限制检查</li>
 *     <li>冷却时间检查</li>
 *     <li>历史记录管理</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
public class AIRequestValidator {

    private final KilacraftAI plugin;

    private final Map<UUID, Long> cooldowns;

    public AIRequestValidator(KilacraftAI plugin) {
        this.plugin = plugin;
        this.cooldowns = new ConcurrentHashMap<>();
    }

    /**
     * 检查世界限制
     *
     * @param player 玩家
     * @return true=允许使用，false=禁止使用
     */
    public boolean canUseAIInWorld(Player player) {
        String worldName = player.getWorld().getName();
        ConfigManager config = plugin.getConfigManager();

        // 获取配置
        var allowedWorlds = config.getAllowedWorlds();
        var bannedWorlds = config.getBannedWorlds();

        // 优先检查禁止列表（如果在禁止列表中，直接返回 false）
        if (!bannedWorlds.isEmpty() && bannedWorlds.contains(worldName)) {
            return false;
        }

        // 再检查允许列表（如果为空表示所有世界都允许）
        return allowedWorlds.isEmpty() || allowedWorlds.contains(worldName);
    }

    /**
     * 检查冷却时间
     *
     * @param playerId 玩家 UUID
     * @return true=冷却已完成可以使用，false=仍在冷却中
     */
    public boolean isCooldownReady(UUID playerId) {
        int cooldownSeconds = plugin.getConfigManager().getCooldownSeconds();

        // 如果配置为 0 或不大于 0，表示无冷却
        if (cooldownSeconds <= 0) {
            return true;
        }

        long currentTime = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            Long lastUsed = cooldowns.get(playerId);
            long timeLeft = (lastUsed + (cooldownSeconds * 1000L)) - currentTime;

            return timeLeft <= 0; // 仍在冷却中
        }

        return true; // 冷却已完成
    }

    /**
     * 获取冷却剩余时间（秒）
     *
     * @param playerId 玩家 UUID
     * @return 剩余秒数，如果不在冷却中则返回 0
     */
    public long getRemainingCooldownSeconds(UUID playerId) {
        int cooldownSeconds = plugin.getConfigManager().getCooldownSeconds();

        if (cooldownSeconds <= 0 || !cooldowns.containsKey(playerId)) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        Long lastUsed = cooldowns.get(playerId);
        long timeLeft = (lastUsed + (cooldownSeconds * 1000L)) - currentTime;

        return Math.max(0, timeLeft / 1000);
    }

    /**
     * 检查插件命令的冷却时间
     *
     * <p>插件命令使用独立的冷却配置</p>
     *
     * @param playerId 玩家 UUID
     * @return true=冷却已完成可以使用，false=仍在冷却中
     */
    public boolean isPluginCommandCooldownReady(UUID playerId) {
        int pluginsCooldownSeconds = plugin.getConfigManager().getPluginsCooldownSeconds();

        // 如果配置为 -1，使用普通冷却时间
        if (pluginsCooldownSeconds < 0) {
            return isCooldownReady(playerId);
        }

        // 如果配置为 0，表示无冷却
        if (pluginsCooldownSeconds <= 0) {
            return true;
        }

        long currentTime = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            Long lastUsed = cooldowns.get(playerId);
            long timeLeft = (lastUsed + (pluginsCooldownSeconds * 1000L)) - currentTime;

            return timeLeft <= 0; // 仍在冷却中
        }

        return true; // 冷却已完成
    }

    /**
     * 获取插件命令冷却剩余时间（秒）
     *
     * @param playerId 玩家 UUID
     * @return 剩余秒数，如果不在冷却中则返回 0
     */
    public long getPluginCommandRemainingCooldownSeconds(UUID playerId) {
        int pluginsCooldownSeconds = plugin.getConfigManager().getPluginsCooldownSeconds();

        // 如果配置为 -1，使用普通冷却时间
        if (pluginsCooldownSeconds < 0) {
            return getRemainingCooldownSeconds(playerId);
        }

        if (pluginsCooldownSeconds <= 0 || !cooldowns.containsKey(playerId)) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        Long lastUsed = cooldowns.get(playerId);
        long timeLeft = (lastUsed + (pluginsCooldownSeconds * 1000L)) - currentTime;

        return Math.max(0, timeLeft / 1000);
    }

    /**
     * 记录冷却时间开始
     *
     * @param playerId 玩家 UUID
     */
    public void startCooldown(UUID playerId) {
        cooldowns.put(playerId, System.currentTimeMillis());
    }

    /**
     * 保存对话到历史记录（支持保存到最新回复缓存）
     *
     * @param history     历史记录队列
     * @param userMessage 用户消息
     * @param aiResponse  AI 响应
     * @param playerId    玩家 UUID（用于保存到最新回复缓存，如果为 null 则不保存）
     * @param personality 人格名称（用于保存到最新回复缓存，如果为 null 则不保存）
     */
    public void saveToHistory(Deque<ConversationManager.Message> history, String userMessage, String aiResponse, UUID playerId, String personality) {
        saveToHistory(history, userMessage, aiResponse, playerId, personality, ConversationSource.PLUGIN);
    }

    /**
     * 保存对话到历史记录（完整版本，含持久化）
     *
     * @param history     历史记录队列
     * @param userMessage 用户消息
     * @param aiResponse  AI 响应
     * @param playerId    玩家 UUID（用于持久化和最新回复缓存）
     * @param personality 人格名称（用于持久化和最新回复缓存，null 视为空串）
     * @param source      来源标识（null 则不持久化）
     */
    public void saveToHistory(Deque<ConversationManager.Message> history, String userMessage, String aiResponse,
                              UUID playerId, String personality, ConversationSource source) {
        int maxHistory = plugin.getConfigManager().getMaxHistory();

        if (maxHistory <= 0 || history == null) {
            return;
        }

        // 添加用户消息
        history.add(new ConversationManager.Message("user", userMessage));
        // 添加 AI 回复
        history.add(new ConversationManager.Message("assistant", aiResponse));

        // 保持历史记录不超过限制（每轮对话算 2 条）
        while (history.size() > maxHistory * 2) {
            ConversationManager.Message removed = history.removeFirst();

            PluginLogger.debug("历史管理", "移除最早的历史记录：{}", removed.getContent().substring(0, Math.min(20, removed.getContent().length())) + "...");
        }

        // 如果提供了 playerId 和 personality，保存到最新回复缓存
        if (playerId != null && personality != null) {
            plugin.getConversationManager().saveLatestAIResponse(playerId, personality, aiResponse);
            PluginLogger.debug("历史管理", "已保存 AI 回复到最新回复缓存：{}_{}", playerId, personality);
        }

        // 异步提交到持久化队列（从 plugin 获取，避免未注入问题）
        ConversationPersistenceService persistence = plugin.getPersistenceService();
        if (persistence != null && playerId != null && source != null) {
            String personalityValue = (personality != null && !personality.isEmpty()) ? personality : "";
            persistence.submit(playerId, "user", userMessage, personalityValue, source.getValue());
            persistence.submit(playerId, "assistant", aiResponse, personalityValue, source.getValue());
        }

        PluginLogger.debug("历史管理", "已保存新对话，当前历史记录数量：{}", history.size());
    }

    /**
     * 为插件命令生成隔离的历史记录 key
     *
     * <p>格式：UUID_人格名称</p>
     * <p>这样可以让不同人格的对话历史相互隔离</p>
     *
     * @param playerId    玩家 UUID
     * @param personality 人格名称
     * @return 隔离的历史记录 key
     */
    public String getPluginCommandHistoryKey(UUID playerId, String personality) {
        return playerId.toString() + "_" + personality;
    }

}
