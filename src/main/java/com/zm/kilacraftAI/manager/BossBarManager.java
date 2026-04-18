package com.zm.kilacraftAI.manager;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.OutputConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BossBar 管理器
 *
 * @author Zm_Mmm
 * @since 2026-04-15
 */
public class BossBarManager {

    private final KilacraftAI plugin;
    private final OutputConfigManager config;

    /**
     * 玩家活跃的 BossBar 映射
     * <p>Key: Player UUID, Value: BossBar 实例</p>
     */
    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();

    /**
     * 玩家的定时移除任务映射
     * <p>Key: Player UUID, Value: 定时移除任务</p>
     * <p>新消息到来时取消旧任务，防止旧任务的定时器误删当前正在显示的 BossBar</p>
     */
    private final Map<UUID, BukkitTask> removalTasks = new ConcurrentHashMap<>();

    public BossBarManager(KilacraftAI plugin, OutputConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * 发送 BossBar 消息
     *
     * @param player  目标玩家
     * @param message 消息内容
     */
    public void sendBossBar(Player player, String message) {
        UUID playerId = player.getUniqueId();

        BossBar bar = activeBars.computeIfAbsent(playerId, uuid -> {
            BossBar newBar = Bukkit.createBossBar(message, config.getBossBarColor(), config.getBossBarStyle());
            newBar.setProgress(1.0);
            newBar.addPlayer(player);
            return newBar;
        });

        // 更新标题
        bar.setTitle(message);

        // 定时清理
        int durationSeconds = config.getBossBarDurationSeconds();
        if (durationSeconds > 0) {
            scheduleRemoval(playerId, durationSeconds);
        }
    }

    /**
     * 定时移除 BossBar（先取消旧任务再注册新任务）
     *
     * @param playerId     玩家 UUID
     * @param delaySeconds 延迟秒数
     */
    private void scheduleRemoval(UUID playerId, int delaySeconds) {
        // 取消旧的定时移除任务（防止连续对话时旧任务误删当前 BossBar）
        BukkitTask oldTask = removalTasks.get(playerId);
        if (oldTask != null) {
            oldTask.cancel();
        }

        // 注册新的定时移除任务
        BukkitTask newTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            removalTasks.remove(playerId);
            removeBossBar(playerId);
        }, delaySeconds * 20L);
        removalTasks.put(playerId, newTask);
    }

    /**
     * 移除玩家的 BossBar
     *
     * @param playerId 玩家 UUID
     */
    public void removeBossBar(UUID playerId) {
        // 取消定时移除任务
        BukkitTask task = removalTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }

        BossBar bar = activeBars.remove(playerId);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
    }

    /**
     * 清理所有 BossBar
     */
    public void cleanup() {
        // 取消所有定时移除任务
        removalTasks.values().forEach(BukkitTask::cancel);
        removalTasks.clear();

        activeBars.values().forEach(BossBar::removeAll);
        activeBars.clear();
    }
}
