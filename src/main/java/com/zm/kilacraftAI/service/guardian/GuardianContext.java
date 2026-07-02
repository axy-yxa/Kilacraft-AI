package com.zm.kilacraftAI.service.guardian;

import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * 守护求值上下文：每次 Monitor.eval 时由引擎构造，传给谓词与动作。
 *
 * <p>承载一次求值所需的全部周边信息：
 * <ul>
 *   <li>{@link #player()} / {@link #nowMillis()}：基础信息</li>
 *   <li>{@link #monitorId()}：触发该次求值的监听单元标识，供动作打审计标签</li>
 *   <li>{@link #triggerValue()}：触发谓词最近一次读到的数值，供模板渲染（如「还差 N 个铁锭」）</li>
 * </ul>
 * 后续阶段如需更多字段（冷却中枢、画像预言机等）以追加 record 分量为主。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public record GuardianContext(Player player, long nowMillis, String monitorId, Optional<Double> triggerValue) {

    public GuardianContext {
        triggerValue = triggerValue == null ? Optional.empty() : triggerValue;
    }

    /** 谓词单测用：仅带玩家，其余为空。 */
    public static GuardianContext of(Player player) {
        return new GuardianContext(player, System.currentTimeMillis(), null, Optional.empty());
    }

    /** 引擎求值用：带监听单元标识 + 触发数值。 */
    public static GuardianContext of(Player player, String monitorId, Optional<Double> triggerValue) {
        return new GuardianContext(player, System.currentTimeMillis(), monitorId, triggerValue);
    }
}
