package com.zm.kilacraftAI.service.guardian.predicate;

import com.zm.kilacraftAI.service.guardian.GuardianContext;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 或门组合：任一子谓词为 true 即 true，短路求值（命中即停）。{@code lastValue} 为空。
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class OrPredicate implements Predicate {

    private final List<Predicate> components;

    public OrPredicate(List<Predicate> components) {
        Objects.requireNonNull(components, "components");
        this.components = List.copyOf(components);
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        for (Predicate p : components) {
            if (p.test(state, ctx)) {
                return true;
            }
        }
        return false;
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
