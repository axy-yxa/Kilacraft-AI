package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.Comparison;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.ValuePredicate;

/**
 * 背包剩余空格原语：按比较符与阈值求值。
 *
 * @author Zm_Mmm
 * @since 2026-07-11
 */
public final class InventoryFreeSlotsPredicate extends ValuePredicate {

    private final Comparison comparison;
    private final int threshold;

    public InventoryFreeSlotsPredicate(Comparison comparison, int threshold) {
        this.comparison = comparison;
        this.threshold = threshold;
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        int free = state.inventoryFreeSlots();
        recordValue(free);
        return comparison.test(free, threshold);
    }

    public Comparison comparison() {
        return comparison;
    }

    public int threshold() {
        return threshold;
    }
}
