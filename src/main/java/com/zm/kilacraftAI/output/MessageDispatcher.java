package com.zm.kilacraftAI.output;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.OutputConfigManager;
import com.zm.kilacraftAI.enums.OutputChannel;
import lombok.Getter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息分发器
 *
 * <p>核心组件：根据配置将 AI 响应分发到不同的输出载体。</p>
 *
 * <h3>支持的载体：</h3>
 * <ul>
 *   <li>CHAT - player.sendMessage()</li>
 *   <li>ACTION_BAR - player.sendActionBar()</li>
 *   <li>BOSS_BAR - Bukkit.createBossBar() + 生命周期管理</li>
 *   <li>TITLE - player.sendTitle()</li>
 * </ul>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li>向后兼容：默认 CHAT 与原有行为完全一致</li>
 *   <li>职责单一：只负责消息分发，不关心业务逻辑</li>
 *   <li>资源管理：BossBar 需要手动清理，由 BossBarManager 内部管理</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-15
 */
public class MessageDispatcher {

    private final OutputConfigManager config;
    /**
     * BossBar 管理器（用于清理资源）
     */
    @Getter
    private final BossBarManager bossBarManager;

    public MessageDispatcher(KilacraftAI plugin) {
        this.config = plugin.getConfigManager().getOutputConfigManager();
        this.bossBarManager = new BossBarManager(plugin, config);
    }

    /**
     * 分发单条消息到指定载体
     *
     * @param player  目标玩家
     * @param message 已格式化的消息（包含前缀等）
     * @param channel 输出载体
     */
    public void dispatch(Player player, String message, OutputChannel channel) {
        if (player == null || !player.isOnline()) {
            return;
        }

        switch (channel) {
            case CHAT:
                sendChat(player, message);
                break;
            case ACTION_BAR:
                sendActionBar(player, message);
                break;
            case BOSS_BAR:
                bossBarManager.sendBossBar(player, message);
                break;
            case TITLE:
                sendTitle(player, message);
                break;
            default:
                // 未知载体，回退到 CHAT
                sendChat(player, message);
                break;
        }
    }

    /**
     * 发送聊天消息
     * <p>原有逻辑：player.sendMessage(message)</p>
     */
    private void sendChat(Player player, String message) {
        player.sendMessage(message);
    }

    /**
     * 发送 ActionBar 消息
     * <p>兼容 Spigot 1.16.5+ 和 Paper 的方式</p>
     */
    public void sendActionBar(Player player, String message) {
        // Spigot 1.16.5+ 兼容方式（Paper 也支持）
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    /**
     * 发送 Title 消息（公开方法，供流式输出使用）
     * <p>原有逻辑无此功能，新增载体</p>
     */
    public void sendTitle(Player player, String message) {
        player.sendTitle(message, "", config.getTitleFadeInTicks(), config.getTitleStayTicks(), config.getTitleFadeOutTicks());
    }

    /**
     * BossBar 管理器（内部类）
     *
     * <p>负责 BossBar 的创建、更新、清理和生命周期管理。</p>
     */
    static class BossBarManager {

        private final KilacraftAI plugin;
        private final OutputConfigManager config;

        /**
         * 玩家活跃的 BossBar 映射
         * <p>Key: Player UUID, Value: BossBar 实例</p>
         */
        private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();

        public BossBarManager(KilacraftAI plugin, OutputConfigManager config) {
            this.plugin = plugin;
            this.config = config;
        }

        /**
         * 发送 BossBar 消息
         *
         * <p>逻辑：</p>
         * <ol>
         *   <li>如果玩家已有 BossBar，更新标题</li>
         *   <li>如果没有，创建新的 BossBar</li>
         *   <li>如果配置了 duration_seconds > 0，定时清理</li>
         * </ol>
         */
        public void sendBossBar(Player player, String message) {
            UUID playerId = player.getUniqueId();

            BossBar bar = activeBars.computeIfAbsent(playerId, uuid -> {
                BossBar newBar = Bukkit.createBossBar(message, config.getBossBarColor(), config.getBossBarStyle());
                newBar.setProgress(1.0); // 满进度
                newBar.addPlayer(player);
                return newBar;
            });

            // 更新标题
            bar.setTitle(message);

            // 定时清理（如果配置了时长）
            int durationSeconds = config.getBossBarDurationSeconds();
            if (durationSeconds > 0) {
                scheduleRemoval(playerId, durationSeconds);
            }
        }

        /**
         * 定时移除 BossBar
         */
        private void scheduleRemoval(UUID playerId, int delaySeconds) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                removeBossBar(playerId);
            }, delaySeconds * 20L); // ticks = seconds * 20
        }

        /**
         * 移除玩家的 BossBar
         */
        public void removeBossBar(UUID playerId) {
            BossBar bar = activeBars.remove(playerId);
            if (bar != null) {
                bar.removeAll(); // 从所有玩家中移除
                bar.setVisible(false);
            }
        }

        /**
         * 清理所有 BossBar（插件卸载时调用）
         */
        public void cleanup() {
            activeBars.values().forEach(BossBar::removeAll);
            activeBars.clear();
        }
    }
}
