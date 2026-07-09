package com.zm.kilacraftAI.service.guardian.predicate;

import com.zm.kilacraftAI.service.guardian.GuardianContext;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 守护谓词：在 IO 线程基于 {@link PlayerState} 快照做确定性布尔判断，永不调用 LLM、不调用 skill。
 *
 * <p>组合用 {@link #and}/{@link #or}/{@link #not} 工厂；具体原语见 {@code primitives/} 包。
 * {@link #lastValue()} 暴露最近一次读到的数值，供模板渲染（如「还差 12 个铁锭」）。</p>
 *
 * <p>稳定性红线：谓词永不走 skill。读状态走直接 API / 快照；改状态（动作）才走 skill。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public interface Predicate {

    /** 基于快照求值。ctx 提供玩家与时刻；谓词通常只读 state。 */
    boolean test(PlayerState state, GuardianContext ctx);

    /** 最近一次求值时读到的数值（无则空）；用于模板渲染。默认返回空。 */
    default Optional<Double> lastValue() {
        return Optional.empty();
    }

    /**
     * 本谓词需要 {@link com.zm.kilacraftAI.service.guardian.predicate.PlayerStateService} 在快照中
     * 额外读取的熔炉位置（拉取式采集，按需读取而非全量）。引擎按一玩家一轮的到点 monitor 并集请求，一次 snapshot 喂所有。
     * 默认空；熔炉类原语覆写。组合谓词（And/Or/Not）并集子项。
     */
    default Set<BlockPos> requestedFurnacePositions() {
        return Set.of();
    }

    /** 与门：所有子谓词均为 true 才 true，短路求值。 */
    static Predicate and(Predicate... parts) {
        return new AndPredicate(List.of(parts));
    }

    /** 或门：任一子谓词为 true 即 true，短路求值。 */
    static Predicate or(Predicate... parts) {
        return new OrPredicate(List.of(parts));
    }

    /** 非门：取反。 */
    static Predicate not(Predicate inner) {
        return new NotPredicate(inner);
    }
}
