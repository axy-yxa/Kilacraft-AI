package com.zm.kilacraftAI.service.guardian.predicate;

import com.zm.kilacraftAI.service.guardian.GuardianContext;

import java.util.List;
import java.util.Optional;

/**
 * 守护谓词：在 IO 线程基于 {@link PlayerState} 快照做确定性布尔判断，永不调用 LLM、不调用 skill。
 *
 * <p>组合用 {@link #and}/{@link #not} 工厂；具体原语见 {@code primitives/} 包。
 * {@link #lastValue()} 暴露最近一次读到的数值，供模板渲染。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public interface Predicate {

    /**
     * 基于快照求值。ctx 提供玩家与时刻；谓词通常只读 state。
     */
    boolean test(PlayerState state, GuardianContext ctx);

    /**
     * 最近一次求值时读到的数值（无则空）；用于模板渲染。默认返回空。
     */
    default Optional<Double> lastValue() {
        return Optional.empty();
    }

    /**
     * 与门：所有子谓词均为 true 才 true，短路求值。
     */
    static Predicate and(Predicate... parts) {
        return new AndPredicate(List.of(parts));
    }

    /**
     * 非门：取反。
     */
    static Predicate not(Predicate inner) {
        return new NotPredicate(inner);
    }
}
