package com.zm.kilacraftAI.service.guardian.monitor;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.guardian.AlertCategory;
import com.zm.kilacraftAI.service.guardian.AlertPriority;
import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.action.GuardianAction;
import com.zm.kilacraftAI.service.guardian.action.Outcome;
import com.zm.kilacraftAI.service.guardian.predicate.BlockPos;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.Predicate;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 原子监听单元：触发源 + 触发谓词 + 动作 + 策略 + 终止谓词。
 *
 * <p>{@link #eval} 按策略决定是否开火 → 执行动作 → 按统一 {@link Outcome} 迁移状态机。
 * Monitor 只认 Outcome，动作层（含 skill）的失败语义经 Outcome 归一化。</p>
 *
 * <p>线程模型：同一 monitor 的 eval 由 {@link GuardianEngine} 的 per-player lock 串行化，
 * 内部状态字段无需加锁；{@code state} 等用 volatile 保证 lifecycle 线程（markPaused/resume）可见。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class Monitor {

    private static final String LOG_MODULE = "守护系统";
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_COOLDOWN_MS = 5_000L;
    private static final long BACKOFF_BASE_MS = 2_000L;
    private static final long BACKOFF_CAP_MS = 60_000L;

    private final String id;
    private final TriggerSource source;
    private final Predicate triggerPredicate;   // null = 无条件（事件/定时源，已由源过滤）
    private final GuardianAction action;
    private final Policy policy;
    private final Predicate goalPredicate;      // null = 无终止目标
    private final int maxRetries;
    private final long cooldownMillis;
    private final AlertCategory category;
    private final AlertPriority priority;

    private volatile MonitorState state = MonitorState.RUNNING;
    private volatile boolean previousTrigger;    // WATCH_EDGE 边沿检测：上一态（跨 IO 线程可见）
    private volatile boolean hasFired;           // 是否已开过火（首火不受冷却约束）
    private volatile int retryCount;
    private volatile long lastEvalMillis;
    private volatile long nextRetryMillis;      // BLOCKED 退避到点
    private volatile long lastFireMillis;

    public Monitor(String id, TriggerSource source, Predicate triggerPredicate,
                   GuardianAction action, Policy policy, Predicate goalPredicate) {
        this(id, source, triggerPredicate, action, policy, goalPredicate,
                DEFAULT_MAX_RETRIES, DEFAULT_COOLDOWN_MS, AlertCategory.GENERAL, AlertPriority.NORMAL);
    }

    public Monitor(String id, TriggerSource source, Predicate triggerPredicate,
                   GuardianAction action, Policy policy, Predicate goalPredicate,
                   int maxRetries, long cooldownMillis) {
        this(id, source, triggerPredicate, action, policy, goalPredicate,
                maxRetries, cooldownMillis, AlertCategory.GENERAL, AlertPriority.NORMAL);
    }

    public Monitor(String id, TriggerSource source, Predicate triggerPredicate,
                   GuardianAction action, Policy policy, Predicate goalPredicate,
                   int maxRetries, long cooldownMillis,
                   AlertCategory category, AlertPriority priority) {
        this.id = Objects.requireNonNull(id, "id");
        this.source = Objects.requireNonNull(source, "source");
        this.action = Objects.requireNonNull(action, "action");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.triggerPredicate = triggerPredicate;
        this.goalPredicate = goalPredicate;
        if (policy == Policy.UNTIL_GOAL && goalPredicate == null) {
            throw new IllegalArgumentException("UNTIL_GOAL 策略必须提供 goalPredicate: " + id);
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries 不得为负: " + maxRetries);
        }
        if (cooldownMillis < 0) {
            throw new IllegalArgumentException("cooldownMillis 不得为负: " + cooldownMillis);
        }
        this.maxRetries = maxRetries;
        this.cooldownMillis = cooldownMillis;
        this.category = Objects.requireNonNull(category, "category");
        this.priority = Objects.requireNonNull(priority, "priority");
    }

    /**
     * 求值一轮。由引擎在 polling cadence 到点 / 事件信号 / 定时到点时调用。
     *
     * @return 若本轮流过动作则含 Outcome；否则空（未到点 / 谓词未满足 / 冷却中 / 终态）
     */
    public Optional<Outcome> eval(PlayerState state, GuardianContext ctx) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(ctx, "ctx");
        if (this.state.isTerminal() || this.state == MonitorState.PAUSED) {
            return Optional.empty();
        }
        // BLOCKED 退避未到点
        if (this.state == MonitorState.BLOCKED && ctx.nowMillis() < nextRetryMillis) {
            return Optional.empty();
        }
        // 到点 → RUNNING（WAITING/BLOCKED 都先回 RUNNING 再判定）
        if (this.state != MonitorState.RUNNING) {
            transitionTo(MonitorState.RUNNING);
        }
        this.lastEvalMillis = ctx.nowMillis();

        // UNTIL_GOAL：先查目标，已达成 → DONE（不开火）
        if (policy == Policy.UNTIL_GOAL && goalPredicate.test(state, ctx)) {
            transitionTo(MonitorState.DONE);
            return Optional.empty();
        }

        if (!decideFire(state, ctx)) {
            transitionTo(MonitorState.WAITING);
            return Optional.empty();
        }

        // 自身冷却去抖；首火不受约束
        if (hasFired && ctx.nowMillis() - lastFireMillis < cooldownMillis) {
            transitionTo(MonitorState.WAITING);
            return Optional.empty();
        }

        hasFired = true;
        lastFireMillis = ctx.nowMillis();
        Outcome outcome = action.perform(ctx).join();
        applyOutcome(outcome, ctx);
        return Optional.of(outcome);
    }

    /** 按策略决定本轮是否开火。 */
    private boolean decideFire(PlayerState state, GuardianContext ctx) {
        if (policy == Policy.UNTIL_GOAL) {
            return true; // 目标已在 eval 前检查
        }
        boolean current = (triggerPredicate != null) ? triggerPredicate.test(state, ctx) : true;
        return switch (policy) {
            case WATCH_EDGE -> {
                boolean edge = !previousTrigger && current;
                previousTrigger = current;
                yield edge;
            }
            case WHILE_TRUE -> {
                previousTrigger = current;
                yield current;
            }
            case RECURRING, ONE_SHOT -> true;
            default -> false;
        };
    }

    /** Outcome → 状态迁移。 */
    private void applyOutcome(Outcome outcome, GuardianContext ctx) {
        switch (outcome) {
            case SUCCESS -> {
                retryCount = 0;
                transitionTo(policy.isOneShot() ? MonitorState.DONE : MonitorState.WAITING);
            }
            case IN_PROGRESS -> transitionTo(MonitorState.WAITING);
            case NEED_INFO -> transitionTo(MonitorState.WAITING); // 暂停 re-arm；PendingResumeManager 接管续体
            case TRANSIENT_FAIL -> {
                retryCount++;
                if (retryCount > maxRetries) {
                    transitionTo(MonitorState.FAILED);
                } else {
                    nextRetryMillis = ctx.nowMillis() + backoffMillis(retryCount);
                    transitionTo(MonitorState.BLOCKED);
                }
            }
            case PERMANENT_FAIL -> transitionTo(MonitorState.FAILED);
        }
    }

    /** 指数退避：2s, 4s, 8s ... cap 60s。 */
    private static long backoffMillis(int retry) {
        long ms = BACKOFF_BASE_MS << Math.min(retry - 1, 5);
        return Math.min(ms, BACKOFF_CAP_MS);
    }

    /**
     * 原子化状态迁移：synchronized 保证 check-then-set 不被 lifecycle（markPaused/resume/cancel）
     * 跨线程打断——否则 RUNNING→WAITING 与 RUNNING→CANCELLED 并发会 last-writer-wins，取消信号丢失。
     * 不在动作执行段持锁（eval 调用本方法后才 perform），lifecycle 不会被动作阻塞。
     */
    private synchronized void transitionTo(MonitorState next) {
        if (next == state) {
            return;
        }
        if (!state.canTransitionTo(next)) {
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("非法状态迁移: {}→{}（monitor={}）", state, next, id));
            return;
        }
        state = next;
    }

    public String id() {
        return id;
    }

    public MonitorState state() {
        return state;
    }

    public Policy policy() {
        return policy;
    }

    public TriggerSource source() {
        return source;
    }

    public AlertCategory category() {
        return category;
    }

    public AlertPriority priority() {
        return priority;
    }

    public long lastEvalMillis() {
        return lastEvalMillis;
    }

    /** 是否为轮询型（引擎心跳按 cadence 调度）。 */
    public boolean isPolling() {
        return source instanceof PollingTriggerSource;
    }

    /** 轮询 cadence（ticks）；非轮询返回 0。 */
    public long cadenceTicks() {
        return source instanceof PollingTriggerSource p ? p.cadenceTicks() : 0L;
    }

    /** 触发谓词 + 目标谓词需要快照额外读取的熔炉位置并集（引擎单快照策略用，一次 snapshot 喂所有谓词）。 */
    public Set<BlockPos> requestedFurnacePositions() {
        Set<BlockPos> all = new HashSet<>();
        if (triggerPredicate != null) {
            all.addAll(triggerPredicate.requestedFurnacePositions());
        }
        if (goalPredicate != null) {
            all.addAll(goalPredicate.requestedFurnacePositions());
        }
        return all;
    }

    /** 引擎在下线/取消时调用。 */
    public void markPaused() {
        transitionTo(MonitorState.PAUSED);
    }

    /** 引擎在上线恢复时调用。 */
    public void resume() {
        if (state == MonitorState.PAUSED) {
            previousTrigger = false;
            transitionTo(MonitorState.RUNNING);
        }
    }

    public void cancel() {
        transitionTo(MonitorState.CANCELLED);
    }

    /** 全配置构造器。必备项入参，其余链式可选。 */
    public static Builder builder(String id, TriggerSource source, GuardianAction action, Policy policy) {
        return new Builder(id, source, action, policy);
    }

    public static final class Builder {
        private final String id;
        private final TriggerSource source;
        private final GuardianAction action;
        private final Policy policy;
        private Predicate triggerPredicate;
        private Predicate goalPredicate;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private long cooldownMillis = DEFAULT_COOLDOWN_MS;
        private AlertCategory category = AlertCategory.GENERAL;
        private AlertPriority priority = AlertPriority.NORMAL;

        private Builder(String id, TriggerSource source, GuardianAction action, Policy policy) {
            this.id = id;
            this.source = source;
            this.action = action;
            this.policy = policy;
        }

        public Builder trigger(Predicate triggerPredicate) { this.triggerPredicate = triggerPredicate; return this; }
        public Builder goal(Predicate goalPredicate) { this.goalPredicate = goalPredicate; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder cooldownMillis(long cooldownMillis) { this.cooldownMillis = cooldownMillis; return this; }
        public Builder category(AlertCategory category) { this.category = category; return this; }
        public Builder priority(AlertPriority priority) { this.priority = priority; return this; }

        public Monitor build() {
            return new Monitor(id, source, triggerPredicate, action, policy, goalPredicate,
                    maxRetries, cooldownMillis, category, priority);
        }
    }
}
