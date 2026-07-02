package com.zm.kilacraftAI.service.guardian.monitor;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import org.bukkit.event.Event;

import java.util.function.Predicate;

/**
 * 监听单元运行时宿主契约：{@link TriggerSource} 通过它向引擎注册/摘除监听、提交事件信号。
 *
 * <p>窄接口——只暴露触发源需要的能力。具体实现由 {@code GuardianEngine}（运行时心跳 + 事件分发 + fan-out）
 * 提供：事件型源注册全局 Listener、定时型源排程、信号异步求值。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public interface GuardianRuntime {

    /**
     * 注册事件型监听：引擎的全局 Listener 匹配到 {@code eventType} 且通过 {@code filter} 后，
     * 为所属 monitor 异步提交求值信号。
     */
    <T extends Event> void registerEventMonitor(Monitor monitor, Class<T> eventType, Predicate<T> filter);

    /**
     * 排程定时型监听：{@code intervalTicks <= 0} 为一次性（仅 {@code delayTicks} 后触发一次）；
     * 否则周期触发。引擎负责按需拉取快照并求值。
     */
    void scheduleMonitor(Monitor monitor, long delayTicks, long intervalTicks);

    /** 摘除该 monitor 的全部注册（事件监听 + 定时任务）。 */
    void unregister(Monitor monitor);

    /** 事件命中或定时到点时，由引擎异步求值该 monitor（构建快照 + eval）。 */
    void submitSignal(Monitor monitor, GuardianContext ctx);
}
