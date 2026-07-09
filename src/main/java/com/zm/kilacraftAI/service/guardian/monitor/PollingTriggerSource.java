package com.zm.kilacraftAI.service.guardian.monitor;

import java.util.Objects;

/**
 * 轮询型触发源：声明 cadence（轮询周期，ticks）。本身不求值——
 * 由 {@code GuardianEngine} 心跳按 cadence 调度，到点拉取快照并 {@link Monitor#eval}。
 *
 * <p>典型：资源（库存数，10–30s）、目标（铁锭 ≥ 64）、循环（熔炉 cookTime）。
 * cadence 按谓词类型默认：危险优先走事件源、资源/目标中频、市场慢。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class PollingTriggerSource implements TriggerSource {

    private final long cadenceTicks;

    public PollingTriggerSource(long cadenceTicks) {
        if (cadenceTicks <= 0) {
            throw new IllegalArgumentException("cadenceTicks 必须为正: " + cadenceTicks);
        }
        this.cadenceTicks = cadenceTicks;
    }

    /** 轮询周期（ticks，1秒=20）。 */
    public long cadenceTicks() {
        return cadenceTicks;
    }

    @Override
    public void bind(GuardianRuntime runtime, Monitor monitor) {
        // 轮询源无需注册——引擎心跳遍历 polling monitor 时按 cadence 调度
    }

    @Override
    public void unbind(GuardianRuntime runtime, Monitor monitor) {
        // 无源侧注册要摘除，但引擎在 evalAsync 创建的 per-monitor 锁需清理，防反复上下线缓慢泄漏
        runtime.unregister(monitor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PollingTriggerSource p && cadenceTicks == p.cadenceTicks;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cadenceTicks);
    }
}
