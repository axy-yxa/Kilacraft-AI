package com.zm.kilacraftAI.service.guardian;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Set;

/**
 * 守护全局事件 Listener：精选受支持的事件类型，命中后转发引擎分发到匹配的事件型 monitor。
 *
 * <p>Bukkit 的事件分发按 @EventHandler 方法签名的事件类型匹配——无法用单一方法接收任意事件类型。
 * 故采用「精选白名单」：每种受支持事件一个 @EventHandler 方法。
 * {@link GuardianEngine#registerEventMonitor} 拒绝未登记的类型并告警。</p>
 *
 * <p>{@link EventPriority#MONITOR} + {@code ignoreCancelled=true}：只读观察（在所有处理之后），
 * 已取消的事件不触发——守护是旁观者，不参与事件处理链。</p>
 *
 * <p>玩家上下线（join/quit）走 {@link GuardianManager} 生命周期（持久化恢复/资源释放），
 * 不经引擎事件分发——它们不是「monitor 触发源」，而是「系统生命周期事件」。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class GuardianEventListener implements Listener {

    /** 受支持的事件类型白名单（@EventHandler 方法一一对应）。新增场景时在此登记 + 加对应方法）。 */
    private static final Set<Class<? extends org.bukkit.event.Event>> SUPPORTED =
            Set.of(EntityDamageEvent.class);

    private final GuardianEngine engine;
    private final GuardianManager manager;

    GuardianEventListener(GuardianEngine engine, GuardianManager manager) {
        this.engine = engine;
        this.manager = manager;
    }

    static boolean isSupported(Class<? extends org.bukkit.event.Event> type) {
        return SUPPORTED.contains(type);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        // 仅玩家受伤对玩家守护有意义
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        engine.dispatchEvent(EntityDamageEvent.class, event, player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (manager != null) {
            manager.onPlayerJoin(event.getPlayer());
        }
    }
}
