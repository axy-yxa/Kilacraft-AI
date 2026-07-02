package com.zm.kilacraftAI.service.guardian.predicate;

import com.zm.kilacraftAI.service.guardian.GuardianContext;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 与门组合：所有子谓词均为 true 才 true，短路求值。{@code lastValue} 为空（组合产布尔，无数值语义）。
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class AndPredicate implements Predicate {

    private final List<Predicate> components;

    public AndPredicate(List<Predicate> components) {
        Objects.requireNonNull(components, "components");
        this.components = List.copyOf(components);
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        for (Predicate p : components) {
            if (!p.test(state, ctx)) {
                return false;
            }
        }
        return true;
    }

    public List<Predicate> components() {
        return components;
    }

    @Override
    public Set<BlockPos> requestedFurnacePositions() {
        Set<BlockPos> all = new HashSet<>();
        for (Predicate p : components) {
            all.addAll(p.requestedFurnacePositions());
        }
        return all;
    }
}
