package com.zm.kilacraftAI.listener;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.PlayerProfileDao;
import com.zm.kilacraftAI.model.profile.SocialGraph;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.sql.Connection;
import java.util.Set;
import java.util.UUID;

/**
 * 私聊监听器
 *
 * <p>监听私聊命令，直接增强社交关系强度。
 * 不写入 kca_server_event 表，避免数据膨胀。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-27
 */
public class PrivateChatListener implements Listener {

    private static final Set<String> PRIVATE_CHAT_COMMANDS = Set.of("/msg", "/tell", "/whisper", "/w", "/pm", "/cmi msg", "/cmi tell", "/cmi whisper", "/cmi w", "/cmi pm");

    private final SocialGraph socialGraph;
    private final DatabaseManager databaseManager;
    private final PlayerProfileDao profileDao;

    public PrivateChatListener(SocialGraph socialGraph, DatabaseManager databaseManager) {
        this.socialGraph = socialGraph;
        this.databaseManager = databaseManager;
        this.profileDao = new PlayerProfileDao(databaseManager.getTablePrefix());
    }

    // ignoreCancelled = false: CMI 等插件可能覆盖 /msg 并 cancel 事件，但私聊实际已发送
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        UUID senderUuid = event.getPlayer().getUniqueId();

        String targetName = extractTargetPlayer(message);
        if (targetName == null) return;

        final String senderName = event.getPlayer().getName();
        PluginLoggerUtil.debug("私聊监听", "{} → {}, 解析到目标玩家: {}", senderName, targetName, message);

        // 异步查询目标玩家 UUID
        FoliaCompat.getIOPool().submit(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                var profile = profileDao.loadByName(conn, targetName);
                if (profile == null) {
                    PluginLoggerUtil.debug("私聊监听", "目标玩家 {} 不在画像数据库中，跳过社交关系记录", targetName);
                    return;
                }

                UUID targetUuid = profile.getUuid();
                if (!targetUuid.equals(senderUuid)) {
                    PluginLoggerUtil.debug("私聊监听", "记录社交关系: {} → {} (private_chat)", senderName, targetName);
                    socialGraph.recordInteraction(senderUuid, targetUuid, "private_chat");
                } else {
                    PluginLoggerUtil.debug("私聊监听", "发送者和目标相同，跳过: {}", senderName);
                }
            } catch (Exception e) {
                PluginLoggerUtil.debug("私聊监听", "解析目标玩家失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 解析私聊命令中的目标玩家名
     *
     * <p>支持两种格式：</p>
     * <ul>
     *   <li>原生命令：{@code /msg Steve hello} → "Steve"</li>
     *   <li>CMI 格式：{@code /cmi msg Steve hello} → "Steve"</li>
     * </ul>
     *
     * @param command 完整命令字符串
     * @return 目标玩家名，无法解析时返回 null
     */
    String extractTargetPlayer(String command) {
        String[] parts = command.split("\\s+", 4);
        if (parts.length < 2) return null;

        String cmd = parts[0].toLowerCase();

        // 原生命令格式：/msg Steve hello
        if (PRIVATE_CHAT_COMMANDS.contains(cmd)) {
            return parts[1];
        }

        // CMI 格式：/cmi msg Steve hello
        if (cmd.equals("/cmi") && parts.length >= 3) {
            String cmiSubCmd = cmd + " " + parts[1].toLowerCase();
            if (PRIVATE_CHAT_COMMANDS.contains(cmiSubCmd)) {
                return parts[2];
            }
        }

        return null;
    }
}
