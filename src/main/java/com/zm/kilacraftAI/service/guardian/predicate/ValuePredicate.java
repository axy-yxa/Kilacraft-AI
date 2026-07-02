package com.zm.kilacraftAI.service.guardian.predicate;

import java.util.Optional;

/**
 * 数值型原语基类：缓存最近一次读到的数值供模板渲染。子类在 {@link #test} 中经 {@link #recordValue} 记录。
 *
 * <p>非线程安全：每个 Monitor 的谓词实例在其求值回合内单线程使用（{@code test} → {@code lastValue} 紧邻调用）。
 * fan-out 不会让同一谓词实例并发求值——GuardianEngine 按 (玩家, monitor) 串行派发。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public abstract class ValuePredicate implements Predicate {

    private double lastValue = Double.NaN;

    /** 子类在 test 中读到数值后调用，缓存为 lastValue。 */
    protected void recordValue(double value) {
        this.lastValue = value;
    }

    @Override
    public Optional<Double> lastValue() {
        return Double.isNaN(lastValue) ? Optional.empty() : Optional.of(lastValue);
    }
}
