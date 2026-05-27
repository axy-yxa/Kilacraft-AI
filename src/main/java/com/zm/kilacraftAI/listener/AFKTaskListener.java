package com.zm.kilacraftAI.listener;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.service.afktask.AFKTaskManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 挂机任务事件监听器
 *
 * <p>监听 Bukkit 事件，负责挂机任务的生命周期管理。</p>
 *
 * <h3>职责：</h3>
 * <ul>
 *   <li>玩家下线 → 自动取消该玩家的挂机任务</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-09
 */
public class AFKTaskListener implements Listener {

    private final KilacraftAI plugin;

    public AFKTaskListener(KilacraftAI plugin) {
        this.plugin = plugin;
    }

    /**
     * 玩家下线时自动取消挂机任务
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        AFKTaskManager manager = plugin.getAfkTaskManager();
        if (manager != null) {
            manager.onPlayerQuit(event.getPlayer().getUniqueId());
        }
    }
}
