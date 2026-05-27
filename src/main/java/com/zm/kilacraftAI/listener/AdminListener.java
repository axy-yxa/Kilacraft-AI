package com.zm.kilacraftAI.listener;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.service.health.ManualSession;
import com.zm.kilacraftAI.service.health.ServerHealthGuardian;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 管理员功能事件监听器
 *
 * <p>监听离线事件，中断正在进行的 ManualSession 采样。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-10
 */
public class AdminListener implements Listener {

    private static final String LOG_PREFIX = "健康监控";

    private final KilacraftAI plugin;

    public AdminListener(KilacraftAI plugin) {
        this.plugin = plugin;
    }

    /**
     * 玩家退出时，如果是 ManualSession 的操作者，中断采样
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        ServerHealthGuardian guardian = plugin.getServerHealthGuardian();
        if (guardian == null) return;

        ManualSession session = guardian.getManualSession();
        String playerName = event.getPlayer().getName();

        // 原子操作：check + reset 在同一次锁获取中完成，避免 TOCTOU 竞态
        if (session.resetIfOperator(playerName)) {
            PluginLoggerUtil.info(LOG_PREFIX, "服主 {} 掉线，中断手动采样", playerName);
            // 停止 Spark Profiler，避免采样在操作者离线后继续运行
            FoliaCompat.dispatchCommand(Bukkit.getConsoleSender(), "spark profiler stop");
        }
    }
}
