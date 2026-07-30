package com.zm.kilacraftAI.service.guardian.monitor;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.action.GuardianLlmAction;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.Predicate;
import lombok.Getter;
import org.bukkit.event.Event;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 常驻内置提醒单元。一个 monitor = 触发源 + 事后过滤谓词 + LLM 发声 + 冷却。
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class Monitor {

    private final String id;
    private final String displayName;
    /**
     * 轮询 cadence（ticks）；事件型为 0。
     */
    private final long cadenceTicks;
    /**
     * 事件型的事件类型；轮询型为 null。
     */
    private final Class<? extends Event> eventType;
    /**
     * 事件型的过滤器（命中才提交信号）；轮询型为 null。
     */
    private final java.util.function.Predicate<Event> eventFilter;
    /**
     * 事后过滤谓词；null = 无条件触发（事件 filter 即门控）。
     */
    private final Predicate triggerPredicate;
    private final GuardianLlmAction action;
    private final long cooldownMillis;

    @Getter
    private volatile boolean paused;             // 下线挂起（上线恢复）
    private volatile boolean previousTrigger;    // 轮询边沿检测：上一态
    private volatile boolean hasFired;           // 是否已触发过（首触发不受冷却约束）
    private volatile long lastEvalMillis;        // 上次 eval 时刻（cadence 门控）
    private volatile long lastFireMillis;        // 上次触发时刻（冷却门控）

    private Monitor(String id, String displayName, long cadenceTicks, Class<? extends Event> eventType, java.util.function.Predicate<Event> eventFilter, Predicate triggerPredicate, GuardianLlmAction action, long cooldownMillis) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = (displayName == null || displayName.isBlank()) ? id : displayName;
        if (cadenceTicks < 0) {
            throw new IllegalArgumentException(I18nService.tr("cadenceTicks 不得为负: {}", cadenceTicks));
        }
        if (cooldownMillis < 0) {
            throw new IllegalArgumentException(I18nService.tr("cooldownMillis 不得为负: {}", cooldownMillis));
        }
        this.cadenceTicks = cadenceTicks;
        this.eventType = eventType;
        this.eventFilter = eventFilter;
        this.triggerPredicate = triggerPredicate;
        this.action = Objects.requireNonNull(action, "action");
        this.cooldownMillis = cooldownMillis;
    }

    /**
     * 轮询型求值：cadence 门控 + 边沿/每轮判定 + 冷却检查。仅引擎对轮询型 monitor 调用。
     *
     * @return 若本轮应触发则含回填 triggerValue 的 ctx；否则空（未到点/未满足/冷却中/挂起）
     */
    public Optional<GuardianContext> eval(PlayerState state, GuardianContext ctx) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(ctx, "ctx");
        if (paused) {
            return Optional.empty();
        }
        long now = ctx.nowMillis();
        long cadenceMs = cadenceTicks * 50L;
        if (now - lastEvalMillis < cadenceMs) {
            return Optional.empty();
        }
        lastEvalMillis = now;

        boolean current = triggerPredicate == null || triggerPredicate.test(state, ctx);
        // 有 triggerPredicate → 边沿（假→真才触发）；无 → 每轮到点即触发
        if (triggerPredicate != null) {
            boolean edge = !previousTrigger && current;
            previousTrigger = current;
            if (!edge) {
                return Optional.empty();
            }
        }

        if (hasFired && now - lastFireMillis < cooldownMillis) {
            PluginLoggerUtil.debug("守护系统", I18nService.tr("守护 monitor 冷却中跳过（monitor={}）", id));
            return Optional.empty();
        }
        hasFired = true;
        lastFireMillis = now;
        PluginLoggerUtil.debug("守护系统", I18nService.tr("守护 monitor 触发（玩家 {}，monitor={}）", ctx.player() != null ? ctx.player().getName() : "?", id));
        return Optional.of(enrichTriggerValue(ctx));
    }

    /**
     * 异步执行 LLM 发声（不阻塞调用线程）。返回 true=已发声，false=本轮跳过
     * （玩家离线/守护被关/预算熔断/LLM 失败）。调用方应在锁外调用。
     */
    public CompletableFuture<Boolean> executeAction(GuardianContext ctx) {
        return action.perform(ctx);
    }

    /**
     * 从触发谓词的 lastValue 提取数值，回填到 ctx 供 LLM 消息渲染。
     * 谓词在 eval 内已执行 test（含 recordValue），此处紧邻读取。
     */
    private GuardianContext enrichTriggerValue(GuardianContext ctx) {
        if (triggerPredicate == null) {
            return ctx;
        }
        Optional<Double> tv = triggerPredicate.lastValue();
        return tv.map(aDouble -> GuardianContext.withTriggerValue(ctx, aDouble)).orElse(ctx);
    }

    public String id() {
        return id;
    }

    /**
     * 玩家可见的人类可读名。
     */
    public String displayName() {
        return displayName;
    }

    public boolean isPolling() {
        return eventType == null;
    }

    /**
     * 是否带事后过滤谓词（事件型用它决定是否需要快照求值）。
     */
    public boolean hasTriggerPredicate() {
        return triggerPredicate != null;
    }

    public long cadenceTicks() {
        return cadenceTicks;
    }

    public Class<? extends Event> eventType() {
        return eventType;
    }

    public java.util.function.Predicate<Event> eventFilter() {
        return eventFilter;
    }

    public long lastEvalMillis() {
        return lastEvalMillis;
    }

    /**
     * 引擎在下线时调用。
     */
    public void markPaused() {
        paused = true;
    }

    /**
     * 引擎在上线恢复时调用：解除挂起 + 重置边沿状态（避免恢复后误触发假→真边沿）。
     */
    public void resume() {
        if (paused) {
            previousTrigger = false;
            paused = false;
        }
    }

    /**
     * 全配置构造器。触发方式二选一：polling(cadence) 或 event(type+filter)。
     */
    public static Builder polling(String id, GuardianLlmAction action, long cadenceTicks) {
        return new Builder(id, action, cadenceTicks, null, null);
    }

    public static <T extends Event> Builder event(String id, GuardianLlmAction action, Class<T> eventType, java.util.function.Predicate<T> filter) {
        return new Builder(id, action, 0L, eventType, filter);
    }

    public static final class Builder {
        private final String id;
        private final GuardianLlmAction action;
        private final long cadenceTicks;
        private final Class<? extends Event> eventType;
        private final java.util.function.Predicate<Event> eventFilter;
        private String displayName;
        private Predicate triggerPredicate;
        private long cooldownMillis = 5_000L;

        @SuppressWarnings("unchecked")
        <T extends Event> Builder(String id, GuardianLlmAction action, long cadenceTicks, Class<T> eventType, java.util.function.Predicate<T> filter) {
            this.id = id;
            this.action = action;
            this.cadenceTicks = cadenceTicks;
            this.eventType = eventType;
            this.eventFilter = (java.util.function.Predicate<Event>) filter;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder trigger(Predicate triggerPredicate) {
            this.triggerPredicate = triggerPredicate;
            return this;
        }

        public Builder cooldownMillis(long cooldownMillis) {
            this.cooldownMillis = cooldownMillis;
            return this;
        }

        public Monitor build() {
            return new Monitor(id, displayName, cadenceTicks, eventType, eventFilter, triggerPredicate, action, cooldownMillis);
        }
    }
}
