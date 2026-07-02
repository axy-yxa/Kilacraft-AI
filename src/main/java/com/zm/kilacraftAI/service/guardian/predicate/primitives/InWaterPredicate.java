package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.Predicate;

/**
 * 涉水原语：{@code inWater} 为 true 时触发。
 *
 * <p>配合 {@link AirSupplyPredicate} 用于溺水告警（复合「在水中 AND 氧气低」）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class InWaterPredicate implements Predicate {

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        return state.inWater();
    }
}
