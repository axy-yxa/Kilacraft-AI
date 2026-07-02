package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.Comparison;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.ValuePredicate;

/**
 * 血量百分比原语：读 {@code PlayerState.healthRatio}（0.0~1.0），按比较符与阈值求值。
 *
 * <p>典型：血量 &lt; 0.5 触发危险告警（配合 WATCH_EDGE 去抖）。危险场景优先走 EntityDamageEvent 事件源，
 * 此原语多用于轮询型资源/进度守护。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class HealthRatioPredicate extends ValuePredicate {

    private final Comparison comparison;
    private final double threshold;

    public HealthRatioPredicate(Comparison comparison, double threshold) {
        this.comparison = comparison;
        this.threshold = threshold;
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        double ratio = state.healthRatio();
        recordValue(ratio);
        return comparison.test(ratio, threshold);
    }

    public Comparison comparison() {
        return comparison;
    }

    public double threshold() {
        return threshold;
    }
}
