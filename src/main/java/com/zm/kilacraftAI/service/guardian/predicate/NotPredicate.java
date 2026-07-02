package com.zm.kilacraftAI.service.guardian.predicate;

import com.zm.kilacraftAI.service.guardian.GuardianContext;

import java.util.Objects;
import java.util.Set;

/**
 * 非门组合：对子谓词取反。{@code lastValue} 为空。
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class NotPredicate implements Predicate {

    private final Predicate inner;

    public NotPredicate(Predicate inner) {
        this.inner = Objects.requireNonNull(inner, "inner");
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        return !inner.test(state, ctx);
    }

    public Predicate inner() {
        return inner;
    }

    @Override
    public Set<BlockPos> requestedFurnacePositions() {
        return inner.requestedFurnacePositions();
    }
}
