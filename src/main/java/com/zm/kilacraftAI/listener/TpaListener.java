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
 * TPA 命令监听器
 *
 * <p>监听玩家传送请求命令，直接增强社交关系强度。
 * 不写入 kca_server_event 表，避免数据膨胀。</p>
 *
 * <p>CMI 等插件可能覆盖 /tpa 命令并 cancel 事件，但传送请求实际已发送，
 * 因此 {@code ignoreCancelled = false} 保证事件不被跳过。</p>
 */
public class TpaListener implements Listener {

    private static final Set<String> TPA_COMMANDS = Set.of("/tpa", "/tpahere", "/tpask", "/cmi tpa", "/cmi tpahere", "/cmi tpask");

    private final SocialGraph socialGraph;
    private final DatabaseManager databaseManager;
    private final PlayerProfileDao profileDao;

    public TpaListener(SocialGraph socialGraph, DatabaseManager databaseManager) {
        this.socialGraph = socialGraph;
        this.databaseManager = databaseManager;
        this.profileDao = new PlayerProfileDao(databaseManager.getTablePrefix());
    }

    // ignoreCancelled = false: CMI 等插件可能覆盖 /tpa 并 cancel 事件，但传送请求实际已发送
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        UUID senderUuid = event.getPlayer().getUniqueId();

        String targetName = extractTargetPlayer(message);
        if (targetName == null) return;

        final String senderName = event.getPlayer().getName();
        PluginLoggerUtil.debug("传送监听", "{} → {}, 解析到目标玩家: {}", senderName, targetName, message);

        // 异步查询目标玩家 UUID
        FoliaCompat.getIOPool().submit(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                var profile = profileDao.loadByName(conn, targetName);
                if (profile == null) {
                    PluginLoggerUtil.debug("传送监听", "目标玩家 {} 不在画像数据库中，跳过社交关系记录", targetName);
                    return;
                }

                UUID targetUuid = profile.getUuid();
                if (!targetUuid.equals(senderUuid)) {
                    PluginLoggerUtil.debug("传送监听", "记录社交关系: {} → {} (tpa_interaction)", senderName, targetName);
                    socialGraph.recordInteraction(senderUuid, targetUuid, "tpa_interaction");
                } else {
                    PluginLoggerUtil.debug("传送监听", "发送者和目标相同，跳过: {}", senderName);
                }
            } catch (Exception e) {
                PluginLoggerUtil.debug("传送监听", "解析目标玩家失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 解析 TPA 命令中的目标玩家名
     *
     * <p>支持两种格式：</p>
     * <ul>
     *   <li>原生命令：{@code /tpa Steve} → "Steve"</li>
     *   <li>CMI 格式：{@code /cmi tpa Steve} → "Steve"</li>
     * </ul>
     *
     * @param command 完整命令字符串
     * @return 目标玩家名，无法解析时返回 null
     */
    String extractTargetPlayer(String command) {
        String[] parts = command.split("\\s+", 4);
        if (parts.length < 2) return null;

        String cmd = parts[0].toLowerCase();

        // 原生命令格式：/tpa Steve
        if (TPA_COMMANDS.contains(cmd)) {
            return parts[1];
        }

        // CMI 格式：/cmi tpa Steve
        if (cmd.equals("/cmi") && parts.length >= 3) {
            String cmiSubCmd = cmd + " " + parts[1].toLowerCase();
            if (TPA_COMMANDS.contains(cmiSubCmd)) {
                return parts[2];
            }
        }

        return null;
    }
}
