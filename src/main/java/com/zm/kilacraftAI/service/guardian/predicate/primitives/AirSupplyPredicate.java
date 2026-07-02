package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.Comparison;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.ValuePredicate;

/**
 * 氧气剩余原语：读 {@code PlayerState.remainingAir}（ticks），按比较符与阈值求值。
 *
 * <p>典型：{@code remainingAir <= 60}（约 3 秒，溺水迫在眉睫），配合 {@link InWaterPredicate} 复合使用。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class AirSupplyPredicate extends ValuePredicate {

    private final Comparison comparison;
    private final int thresholdTicks;

    public AirSupplyPredicate(Comparison comparison, int thresholdTicks) {
        this.comparison = comparison;
        this.thresholdTicks = thresholdTicks;
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        int air = state.remainingAir();
        recordValue(air);
        return comparison.test(air, thresholdTicks);
    }

    public Comparison comparison() {
        return comparison;
    }

    public int thresholdTicks() {
        return thresholdTicks;
    }
}
