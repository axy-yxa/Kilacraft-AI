package com.zm.kilacraftAI.output;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.OutputConfigManager;
import com.zm.kilacraftAI.enums.OutputChannel;
import com.zm.kilacraftAI.manager.BossBarManager;
import com.zm.kilacraftAI.manager.ScoreboardManager;
import lombok.Getter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/**
 * 消息分发器
 * 根据配置将 AI 响应分发到不同的输出载体
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

    /**
     * Scoreboard Sidebar 管理器
     */
    @Getter
    private final ScoreboardManager scoreboardManager;

    public MessageDispatcher(KilacraftAI plugin) {
        this.config = plugin.getConfigManager().getOutputConfigManager();
        this.bossBarManager = new BossBarManager(plugin, config);
        this.scoreboardManager = new ScoreboardManager(plugin, config);
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
            case SIDEBAR:
                scoreboardManager.sendSidebar(player, message);
                break;
            default:
                // 未知载体，回退到 CHAT
                sendChat(player, message);
                break;
        }
    }

    /**
     * 发送聊天消息
     */
    private void sendChat(Player player, String message) {
        player.sendMessage(message);
    }

    /**
     * 发送 ActionBar 消息
     * <p>兼容 Spigot 1.16.5+ 和 Paper 的方式</p>
     */
    public void sendActionBar(Player player, String message) {
        // 使用 Spigot API（Paper/Folia 完全兼容）
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    /**
     * 发送 Title 消息
     */
    public void sendTitle(Player player, String message) {
        player.sendTitle(message, "", config.getTitleFadeInTicks(), config.getTitleStayTicks(), config.getTitleFadeOutTicks());
    }
}
