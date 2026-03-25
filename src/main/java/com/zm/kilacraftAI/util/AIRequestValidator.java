package com.zm.kilacraftAI.util;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.listener.ChatListener;
import lombok.Getter;
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
        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(worldName)) {
            return false;
        }

        return true;
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

            if (timeLeft > 0) {
                return false; // 仍在冷却中
            }
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
    public Deque<ChatListener.Message> getOrCreateHistory(Map<UUID, Deque<ChatListener.Message>> historyMap, UUID playerId) {
        return historyMap.computeIfAbsent(playerId, k -> new ArrayDeque<>());
    }

    /**
     * 保存对话到历史记录
     *
     * @param history     历史记录队列
     * @param userMessage 用户消息
     * @param aiResponse  AI 响应
     */
    public void saveToHistory(Deque<ChatListener.Message> history, String userMessage, String aiResponse) {
        int maxHistory = plugin.getConfigManager().getMaxHistory();

        if (maxHistory <= 0 || history == null) {
            return; // 不保存历史记录
        }

        // 添加用户消息
        history.add(new ChatListener.Message("user", userMessage));
        // 添加 AI 回复
        history.add(new ChatListener.Message("assistant", aiResponse));

        // 保持历史记录不超过限制（每轮对话算 2 条）
        while (history.size() > maxHistory * 2) {
            ChatListener.Message removed = history.removeFirst();

            // 调试模式日志
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[DEBUG] 移除最早的历史记录：" + removed.getContent().substring(0, Math.min(20, removed.getContent().length())) + "...");
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

}
