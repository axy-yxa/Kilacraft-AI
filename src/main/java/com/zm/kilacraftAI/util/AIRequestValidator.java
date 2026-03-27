package com.zm.kilacraftAI.util;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.manager.ConversationManager;
import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
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

    // 冷却时间存储（如果外部需要自己管理冷却）
    @Getter
    private final Map<UUID, Long> cooldowns;

    public AIRequestValidator(KilacraftAI plugin) {
        this.plugin = plugin;
        this.cooldowns = new ConcurrentHashMap<>();
    }

    /**
     * 检查玩家是否可以在当前世界使用 AI
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
     * 检查冷却时间，如果仍在冷却中则发送提示消息
     *
     * @param player 玩家
     * @return true=冷却已完成可以使用，false=仍在冷却中
     */
    public boolean checkCooldownAndNotify(Player player) {
        UUID playerId = player.getUniqueId();

        if (!isCooldownReady(playerId)) {
            int cooldownSeconds = plugin.getConfigManager().getCooldownSeconds();
            long lastUsed = cooldowns.get(playerId);
            long currentTime = System.currentTimeMillis();
            long timeLeft = (lastUsed + (cooldownSeconds * 1000L)) - currentTime;

            if (timeLeft > 0) {
                player.sendMessage("§c请等待 " + (timeLeft / 1000) + " 秒后再试！");
                return false;
            }
        }

        return true;
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
     * 检查插件命令的冷却时间，如果仍在冷却中则发送提示消息到控制台
     *
     * @param sender     命令发送者（控制台）
     * @param playerId   目标玩家 UUID（用于检查冷却）
     * @param playerName 目标玩家名称（用于显示）
     * @return true=冷却已完成可以使用，false=仍在冷却中
     */
    public boolean checkPluginCommandCooldownAndNotify(CommandSender sender, UUID playerId, String playerName) {
        if (playerId == null) {
            return true; // 没有玩家 UUID，跳过冷却检查
        }

        if (!isPluginCommandCooldownReady(playerId)) {
            int pluginsCooldownSeconds = plugin.getConfigManager().getPluginsCooldownSeconds();
            Long lastUsed = cooldowns.get(playerId);
            long currentTime = System.currentTimeMillis();
            long timeLeft = (lastUsed + (pluginsCooldownSeconds * 1000L)) - currentTime;

            if (timeLeft > 0) {
                sender.sendMessage("§c玩家 " + playerName + " 正在冷却中，请等待 " + (timeLeft / 1000) + " 秒后再试！");
                return false;
            }
        }

        return true;
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
     * 获取或创建历史记录队列
     *
     * @param historyMap 存储历史记录的 Map
     * @param playerId   玩家 UUID
     * @return 历史记录队列
     */
    public Deque<ConversationManager.Message> getOrCreateHistory(Map<UUID, Deque<ConversationManager.Message>> historyMap, UUID playerId) {
        return historyMap.computeIfAbsent(playerId, k -> new ArrayDeque<>());
    }

    /**
     * 保存对话到历史记录
     *
     * @param history     历史记录队列
     * @param userMessage 用户消息
     * @param aiResponse  AI 响应
     */
    public void saveToHistory(Deque<ConversationManager.Message> history, String userMessage, String aiResponse) {
        saveToHistory(history, userMessage, aiResponse, null, null);
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
        int maxHistory = plugin.getConfigManager().getMaxHistory();

        if (maxHistory <= 0 || history == null) {
            return; // 不保存历史记录
        }

        // 添加用户消息
        history.add(new ConversationManager.Message("user", userMessage));
        // 添加 AI 回复
        history.add(new ConversationManager.Message("assistant", aiResponse));

        // 保持历史记录不超过限制（每轮对话算 2 条）
        while (history.size() > maxHistory * 2) {
            ConversationManager.Message removed = history.removeFirst();

            // 调试模式日志
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] 移除最早的历史记录：" + removed.getContent().substring(0, Math.min(20, removed.getContent().length())) + "...");
            }
        }

        // 如果提供了 playerId 和 personality，保存到最新回复缓存
        if (playerId != null && personality != null) {
            plugin.getConversationManager().saveLatestAIResponse(playerId, personality, aiResponse);
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] 已保存 AI 回复到最新回复缓存：" + playerId + "_" + personality);
            }
        }

        // 调试模式：打印保存后的历史记录
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 已保存新对话，当前历史记录数量：" + history.size());
        }
    }

    /**
     * 检查世界限制，如果不允许则发送提示消息
     *
     * @param player  玩家
     * @param context 上下文描述（用于日志，如"命令模式"、"连续对话模式"等）
     * @return true=允许使用，false=禁止使用
     */
    public boolean checkWorldLimitAndNotify(Player player, String context) {
        if (!canUseAIInWorld(player)) {
            // 调试模式：打印玩家信息
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().warning("[DEBUG] [世界限制] 玩家 " + player.getName() + " 在禁止的世界 " + player.getWorld().getName() + " 尝试使用 " + MessageUtil.getAIName() + "（" + context + "）");
            }
            player.sendMessage("§c当前世界禁止使用 " + MessageUtil.getAIName() + "！");
            return false;
        }
        return true;
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

    /**
     * 从插件命令历史记录 key 中解析玩家 UUID
     *
     * @param key 历史记录 key（格式：UUID_人格名称）
     * @return 玩家 UUID，如果格式不正确则返回 null
     */
    public UUID parsePlayerIdFromKey(String key) {
        if (key == null || !key.contains("_")) {
            return null;
        }
        try {
            int lastUnderscore = key.lastIndexOf('_');
            String uuidPart = key.substring(0, lastUnderscore);
            return UUID.fromString(uuidPart);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
