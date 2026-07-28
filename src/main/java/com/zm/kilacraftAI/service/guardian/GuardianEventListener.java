package com.zm.kilacraftAI.service.guardian;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;

/**
 * 守护全局事件 Listener：精选受支持的预见性事件类型，命中后转发引擎分发到匹配的事件型 monitor。
 *
 * <p>Folia 兼容：事件在实体所属区域线程触发。Listener 只读事件实体的固有属性（EntityType 枚举、
 * TargetReason 枚举），零跨区域读。单玩家归属（{@code event.getTarget() == player}），O(1) 定位，
 * 不遍历在线玩家。距离/视野判定延迟到引擎的 PlayerState snapshot（经 callSyncOnEntity 安全采集）。</p>
 *
 * <p>{@link EventPriority#MONITOR} + {@code ignoreCancelled=true}：只读观察（在所有处理之后），
 * 已取消的事件不触发——守护是旁观者，不参与事件处理链。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class GuardianEventListener implements Listener {

    private final GuardianEngine engine;

    GuardianEventListener(GuardianEngine engine) {
        this.engine = engine;
    }

    /**
     * 怪物锁定玩家（预见性：尚未造成伤害，玩家可能不知道背后有怪锁定自己）。
     * 单玩家归属——event.getTarget() 即被锁定者，O(1) 定位，无需遍历在线玩家。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        EntityType targetType = event.getEntity().getType();
        engine.dispatchEvent(EntityTargetEvent.class, event, player, targetType);
    }
}
