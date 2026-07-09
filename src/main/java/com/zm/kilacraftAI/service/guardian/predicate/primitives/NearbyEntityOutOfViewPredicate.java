package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.Comparison;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.ValuePredicate;
import org.bukkit.entity.EntityType;

/**
 * 视野外实体计数原语：在快照扫描半径内统计「类型匹配 + 距离 &le; radius + 处于玩家视野外」的实体数，
 * 按比较符与阈值求值。
 *
 * <p>统计处于玩家视野外（侧方及背后）的指定类型实体数。</p>
 *
 * <p>「视野外」= 相对玩家朝向角度 &ge; {@value #OUT_OF_VIEW_MIN_ANGLE}°（侧方及背后，
 * 不转身看不到）。角度由 {@code PlayerStateService} 在快照采集时按玩家 yaw + 实体相对位置预算，
 * 存入 {@link PlayerState.NearbyEntity#relativeAngleDeg()}。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class NearbyEntityOutOfViewPredicate extends ValuePredicate {

    /** 视野外阈值：相对玩家朝向 ≥ 此角度视为玩家看不见（侧方及背后半平面）。 */
    static final double OUT_OF_VIEW_MIN_ANGLE = 90.0;

    private final EntityType entityType;
    private final double radius;
    private final Comparison comparison;
    private final int threshold;

    public NearbyEntityOutOfViewPredicate(EntityType entityType, double radius, Comparison comparison, int threshold) {
        this.entityType = entityType;
        this.radius = radius;
        this.comparison = comparison;
        this.threshold = threshold;
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        int count = 0;
        for (PlayerState.NearbyEntity e : state.nearbyEntities()) {
            if (e.type() == entityType
                    && e.distance() <= radius
                    && e.relativeAngleDeg() >= OUT_OF_VIEW_MIN_ANGLE) {
                count++;
            }
        }
        recordValue(count);
        return comparison.test(count, threshold);
    }

    public EntityType entityType() {
        return entityType;
    }

    public double radius() {
        return radius;
    }

    public Comparison comparison() {
        return comparison;
    }

    public int threshold() {
        return threshold;
    }
}
