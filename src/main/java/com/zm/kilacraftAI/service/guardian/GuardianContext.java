package com.zm.kilacraftAI.service.guardian;

import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * 守护求值上下文：每次 Monitor.eval 时由引擎构造，传给谓词与动作。
 * {@code monitorId} 供动作打审计标签，{@code triggerValue} 供模板渲染。
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public record GuardianContext(Player player, long nowMillis, String monitorId, Optional<Double> triggerValue) {

    public GuardianContext {
        triggerValue = triggerValue == null ? Optional.empty() : triggerValue;
    }

    /** 仅带玩家，其余为空。 */
    public static GuardianContext of(Player player) {
        return new GuardianContext(player, System.currentTimeMillis(), null, Optional.empty());
    }

    /** 引擎求值用：带监听单元标识 + 触发数值。 */
    public static GuardianContext of(Player player, String monitorId, Optional<Double> triggerValue) {
        return new GuardianContext(player, System.currentTimeMillis(), monitorId, triggerValue);
    }
}
