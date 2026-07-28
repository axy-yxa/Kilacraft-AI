package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.Predicate;
import org.bukkit.entity.EntityType;

/**
 * 视野外近距威胁判定：检查触发事件的实体类型，在快照中是否存在同类型、视野外且距离在阈值内的实例。
 *
 * <p>仅用于内置 {@code _threat_target} 事件型 monitor 的触发谓词。事件命中后引擎做 snapshot，
 * 本谓词从 {@link GuardianContext#entityType()} 拿到锁定玩家的实体类型，遍历 {@link PlayerState#nearbyEntities()}
 * 判定：{@code type 匹配 AND distance ≤ maxDistance AND relativeAngleDeg ≥ 90°}（视野外）。
 *
 * <p>意义：玩家正面对的威胁他能自己看到，不需要守护提醒；只有侧方/背后的近距威胁才值得发声。
 * 非 LLM 可发现，仅由内置套餐硬编码装配。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-22
 */
public final class ThreatOutOfViewAndNearPredicate implements Predicate {

    /**
     * 视野外阈值角度（度）：≥ 此值视为玩家侧方/背后，转身才能看到。
     */
    static final double OUT_OF_VIEW_MIN_ANGLE = 90.0;

    private final double maxDistance;

    public ThreatOutOfViewAndNearPredicate(double maxDistance) {
        this.maxDistance = maxDistance;
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        EntityType triggerType = ctx.entityType().orElse(null);
        if (triggerType == null) {
            return false;
        }
        for (PlayerState.NearbyEntity e : state.nearbyEntities()) {
            if (e.type() == triggerType && e.distance() <= maxDistance && e.relativeAngleDeg() >= OUT_OF_VIEW_MIN_ANGLE) {
                return true;
            }
        }
        return false;
    }
}
