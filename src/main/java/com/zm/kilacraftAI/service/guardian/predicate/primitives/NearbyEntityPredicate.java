package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.Comparison;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.ValuePredicate;
import org.bukkit.entity.EntityType;

/**
 * 半径内指定类型实体计数原语：在快照扫描半径内统计类型匹配且距离 &le; radius 的实体数，按比较符与阈值求值。
 *
 * <p>典型：5 格内有苦力怕（&gt;= 1）、附近怪物 &gt;= 3（危险）。
 * {@code radius} 不应超过 {@code PlayerStateService.scanRadius}，否则会少计。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class NearbyEntityPredicate extends ValuePredicate {

    private final EntityType entityType;
    private final double radius;
    private final Comparison comparison;
    private final int threshold;

    public NearbyEntityPredicate(EntityType entityType, double radius, Comparison comparison, int threshold) {
        this.entityType = entityType;
        this.radius = radius;
        this.comparison = comparison;
        this.threshold = threshold;
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        int count = 0;
        for (PlayerState.NearbyEntity e : state.nearbyEntities()) {
            if (e.type() == entityType && e.distance() <= radius) {
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
