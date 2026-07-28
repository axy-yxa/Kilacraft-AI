package com.zm.kilacraftAI.service.guardian;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * 守护求值上下文：每次 Monitor.eval 时由引擎构造，传给谓词与动作。
 * {@code triggerValue}/{@code entityType} 用于拼装 LLM 用户消息文本。
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public record GuardianContext(Player player, long nowMillis, Double triggerValue, Optional<EntityType> entityType) {

    /**
     * 轮询型 monitor 用：可选触发数值。
     */
    public static GuardianContext of(Player player, Double triggerValue) {
        return new GuardianContext(player, System.currentTimeMillis(), triggerValue, Optional.empty());
    }

    /**
     * 事件型 monitor 用：携带触发实体类型。
     */
    public static GuardianContext of(Player player, EntityType entityType) {
        return new GuardianContext(player, System.currentTimeMillis(), null, Optional.of(entityType));
    }

    /**
     * 谓词求值后回填触发数值（其余字段继承源 ctx），供模板渲染。
     */
    public static GuardianContext withTriggerValue(GuardianContext src, Double triggerValue) {
        return new GuardianContext(src.player, src.nowMillis, triggerValue, src.entityType);
    }
}
