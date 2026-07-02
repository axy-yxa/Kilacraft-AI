package com.zm.kilacraftAI.service.guardian.monitor;

/**
 * 定时型触发源（§4.1）：声明一次性 {@code delayTicks} 或周期 {@code intervalTicks}。
 * 经 {@link GuardianRuntime#scheduleMonitor} 排程；纯时间驱动，不读状态。
 *
 * <p>典型：每日查商店、定时喂动物（周期）；N 分钟后提醒（一次性）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class ScheduledTriggerSource implements TriggerSource {

    private final long delayTicks;
    private final long intervalTicks;

    /** 周期触发：首次延迟 {@code delayTicks}，之后每 {@code intervalTicks} 触发。 */
    public static ScheduledTriggerSource recurring(long delayTicks, long intervalTicks) {
        if (intervalTicks <= 0) {
            throw new IllegalArgumentException("intervalTicks 必须为正: " + intervalTicks);
        }
        return new ScheduledTriggerSource(Math.max(delayTicks, 0), intervalTicks);
    }

    /** 一次性触发：{@code delayTicks} 后触发一次即止。 */
    public static ScheduledTriggerSource oneShot(long delayTicks) {
        if (delayTicks < 0) {
            throw new IllegalArgumentException("delayTicks 不得为负: " + delayTicks);
        }
        return new ScheduledTriggerSource(delayTicks, 0);
    }

    private ScheduledTriggerSource(long delayTicks, long intervalTicks) {
        this.delayTicks = delayTicks;
        this.intervalTicks = intervalTicks;
    }

    public long delayTicks() {
        return delayTicks;
    }

    /** 周期（ticks）；0 表示一次性。 */
    public long intervalTicks() {
        return intervalTicks;
    }

    public boolean isRecurring() {
        return intervalTicks > 0;
    }

    @Override
    public void bind(GuardianRuntime runtime, Monitor monitor) {
        runtime.scheduleMonitor(monitor, delayTicks, intervalTicks);
    }

    @Override
    public void unbind(GuardianRuntime runtime, Monitor monitor) {
        runtime.unregister(monitor);
    }
}
