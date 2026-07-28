package com.zm.kilacraftAI.service.guardian.predicate;

import com.zm.kilacraftAI.service.guardian.GuardianContext;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 与门组合：所有子谓词均为 true 才 true，短路求值。
 * {@code lastValue} 返回本轮最后一个实际求值的子谓词的数值，供模板渲染。
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class AndPredicate implements Predicate {

    private final List<Predicate> components;
    private volatile int lastEvaluatedIndex = -1;

    public AndPredicate(List<Predicate> components) {
        Objects.requireNonNull(components, "components");
        this.components = List.copyOf(components);
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        for (int i = 0; i < components.size(); i++) {
            if (!components.get(i).test(state, ctx)) {
                lastEvaluatedIndex = i;
                return false;
            }
        }
        lastEvaluatedIndex = components.size() - 1;
        return true;
    }

    public List<Predicate> components() {
        return components;
    }

    @Override
    public Optional<Double> lastValue() {
        int idx = lastEvaluatedIndex;
        return idx >= 0 ? components.get(idx).lastValue() : Optional.empty();
    }
}
