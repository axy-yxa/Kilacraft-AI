package com.zm.kilacraftAI.service.guardian;

import com.zm.kilacraftAI.config.GuardianConfigManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家活动状态跟踪器：维护每个玩家最后一次操作的时间戳，供守护引擎判定挂机。
 *
 * @author Zm_Mmm
 * @since 2026-07-22
 */
public final class PlayerActivityTracker implements Listener {

    private final GuardianConfigManager configManager;
    private final ConcurrentHashMap<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    public PlayerActivityTracker(GuardianConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * 玩家是否处于挂机状态：自最后操作以来超过阈值。
     */
    public boolean isAfk(UUID playerId) {
        return isAfk(playerId, System.currentTimeMillis());
    }

    /**
     * 带当前时间的重载，供测试注入确定性时钟。
     */
    public boolean isAfk(UUID playerId, long nowMillis) {
        Long last = lastActivity.get(playerId);
        if (last == null) {
            // 无记录（未上线或已下线清理）视为非挂机——交给上游的 isOnline/guardian 存在性检查处理
            return false;
        }
        long thresholdMillis = configManager.getAfkThresholdSeconds() * 1000L;
        return nowMillis - last > thresholdMillis;
    }

    /**
     * 手动标记活跃（测试或外部重置用）。
     */
    public void markActive(UUID playerId, long nowMillis) {
        lastActivity.put(playerId, nowMillis);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastActivity.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        // 仅实际位移（block 坐标变化）才更新——PlayerMoveEvent 每帧触发，纯视角转动是噪声
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    /**
     * 关闭：清空所有记录。由 KilacraftAI.onDisable 调用。
     */
    public void shutdown() {
        lastActivity.clear();
    }
}
