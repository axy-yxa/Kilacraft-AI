package com.zm.kilacraftAI.service.guardian.monitor;

import org.bukkit.event.Event;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 事件型触发源（§4.1）：声明关心的 Bukkit 事件类型 + 过滤函数。
 *
 * <p>{@link #bind} 经 {@link GuardianRuntime#registerEventMonitor} 注册到引擎的全局 Listener；
 * 命中过滤后引擎异步提交信号。复用问候系统的 Listener 模式，不轮询、延迟最低。</p>
 *
 * <p>典型：{@code EntityDamageEvent}（危险）、{@code PlayerMoveEvent}（看门狗进区）。
 * 事件过滤由本源的 {@code filter} 承担；monitor 的触发谓词对事件型通常为 null（事件已过滤即触发）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class EventTriggerSource<T extends Event> implements TriggerSource {

    private final Class<T> eventType;
    private final Predicate<T> filter;

    public EventTriggerSource(Class<T> eventType, Predicate<T> filter) {
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.filter = filter != null ? filter : e -> true;
    }

    public static <T extends Event> EventTriggerSource<T> of(Class<T> eventType) {
        return new EventTriggerSource<>(eventType, e -> true);
    }

    public static <T extends Event> EventTriggerSource<T> of(Class<T> eventType, Predicate<T> filter) {
        return new EventTriggerSource<>(eventType, filter);
    }

    @Override
    public void bind(GuardianRuntime runtime, Monitor monitor) {
        runtime.registerEventMonitor(monitor, eventType, filter);
    }

    @Override
    public void unbind(GuardianRuntime runtime, Monitor monitor) {
        runtime.unregister(monitor);
    }

    public Class<T> eventType() {
        return eventType;
    }

    public Predicate<T> filter() {
        return filter;
    }
}
