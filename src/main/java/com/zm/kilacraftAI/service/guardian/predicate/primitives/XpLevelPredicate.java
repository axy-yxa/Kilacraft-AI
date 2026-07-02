package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.Comparison;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.ValuePredicate;

/**
 * 经验等级原语：读 {@code PlayerState.xpLevel}，按比较符与阈值求值。
 *
 * <p>典型：等级 &gt;= 30（可附魔高级物品）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class XpLevelPredicate extends ValuePredicate {

    private final Comparison comparison;
    private final int threshold;

    public XpLevelPredicate(Comparison comparison, int threshold) {
        this.comparison = comparison;
        this.threshold = threshold;
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        int level = state.xpLevel();
        recordValue(level);
        return comparison.test(level, threshold);
    }

    public Comparison comparison() {
        return comparison;
    }

    public int threshold() {
        return threshold;
    }
}
