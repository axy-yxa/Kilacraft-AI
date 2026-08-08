package com.zm.kilacraftAI.skills.framework.task;

import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM 调用预算与全局熔断管理器。
 *
 * <p>统一治理所有 LLM 入口（玩家主动聊天 / 登录问候 / 对话推荐）的 per-player 调用频率，
 * 仅在异常工况（刷怪塔信号风暴、闸门 bug、配置错误导致狂调）下熔断，正常使用永不触发。
 * 不替代 {@code AIRequestValidatorUtil} 的聊天冷却（那是玩家主动入口的防刷屏）。</p>
 *
 * <p>线程模型：所有方法线程安全。计数靠滑动窗口（默认 1 小时），过期时间戳惰性淘汰。
 * 单例，由 {@link LLMOutputCoordinator} 持有。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-02
 */
public class LLMBudgetManager {

    /**
     * 默认预算上限：正常使用绝对达不到，仅防 runaway。服主可在 llm.yml 覆盖。
     */
    static final int DEFAULT_BUDGET_PER_HOUR = 200;
    private static final long WINDOW_MILLIS = 60L * 60L * 1000L;

    /**
     * 调用优先级：二元熔断——玩家主动永不熔断，被动调用熔断时统一拒绝。
     */
    public enum Priority {
        /**
         * 玩家主动发起（聊天/skill/task）——永不熔断，即使超预算也响应。
         */
        PLAYER_ACTIVE,
        /**
         * 被动调用（问候/推荐/第三方插件）——熔断窗口内统一拒绝。
         */
        PASSIVE
    }

    private volatile int budgetPerHour;
    private final ConcurrentHashMap<UUID, ConcurrentLinkedDeque<Long>> callWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> trippedUntil = new ConcurrentHashMap<>();

    public LLMBudgetManager(int budgetPerHour) {
        // ≤0 表示禁用治理（运行时 tryAcquire/recordCall 直通）；存原值让运行时检查判定。
        this.budgetPerHour = budgetPerHour;
    }

    /**
     * reload 时更新预算上限（volatile 快照发布，跨线程立即可见）。≤0 禁用。
     */
    public void updateBudget(int newBudget) {
        this.budgetPerHour = newBudget;
    }

    /**
     * 由输出场景推导优先级：玩家可见回复场景为 PLAYER_ACTIVE，被动输出场景为 PASSIVE。
     */
    public static Priority priorityOf(OutputScenarioEnum scenario) {
        if (scenario == null) return Priority.PASSIVE;
        return switch (scenario) {
            // 玩家主动发起的请求产生的回复——永不熔断
            case NORMAL_CHAT, SKILL_RESULT, TASK_RESULT -> Priority.PLAYER_ACTIVE;
            // 被动输出（问候/推荐/错误）——熔断时统一拒绝
            case GREETING, SUGGESTION, ERROR -> Priority.PASSIVE;
        };
    }

    /**
     * 判断本次 LLM 调用是否被允许。
     *
     * <p>二元熔断语义：PLAYER_ACTIVE 永不熔断（玩家自己发起的请求必须响应，即使超预算也照常，仅记 warn）；
     * PASSIVE 在熔断窗口内被拒。budgetPerHour≤0 视为禁用治理（全部放行）。</p>
     *
     * @return true 放行；false 当前处于熔断窗口、且本调用属 PASSIVE 优先级。
     */
    public boolean tryAcquire(UUID playerId, Priority priority) {
        if (budgetPerHour <= 0) return true;
        if (priority == Priority.PLAYER_ACTIVE) return true;

        Long until = trippedUntil.get(playerId);
        if (until != null) {
            if (until > System.currentTimeMillis()) {
                return false;
            }
            trippedUntil.remove(playerId);
        }
        return true;
    }

    /**
     * 记一次实际发生的 LLM 调用，维护滑动窗口计数；超阈值时设置熔断窗口。
     *
     * <p>玩家主动请求（PLAYER_ACTIVE）在 {@link #tryAcquire} 处永不熔断，
     * 但其调用仍计入窗口——风暴时把被动输出先压下去，玩家请求继续响应。</p>
     */
    public void recordCall(UUID playerId) {
        if (budgetPerHour <= 0) return;
        long now = System.currentTimeMillis();
        ConcurrentLinkedDeque<Long> window = callWindows.computeIfAbsent(playerId, k -> new ConcurrentLinkedDeque<>());
        synchronized (window) {
            window.addLast(now);
            // 惰性淘汰过期戳
            while (!window.isEmpty() && window.peekFirst() < now - WINDOW_MILLIS) {
                window.pollFirst();
            }
            // 维护窗口大小上界（防极端风暴撑爆 Deque：保留近 budget*2 条足以判定）
            int cap = Math.max(budgetPerHour * 2, DEFAULT_BUDGET_PER_HOUR * 2);
            while (window.size() > cap) window.pollFirst();

            if (window.size() > budgetPerHour) {
                long tripUntil = now + WINDOW_MILLIS;
                Long prev = trippedUntil.get(playerId);
                if (prev == null || prev < tripUntil) {
                    trippedUntil.put(playerId, tripUntil);
                    PluginLoggerUtil.warn("预算熔断", "玩家 {} 1 小时内调用 {} 次超阈值 {}，被动输出降级 1 小时", playerId, window.size(), budgetPerHour);
                }
            }
        }
    }

    /**
     * 玩家下线/清理时释放窗口（避免长期离线玩家的 Deque 累积）。
     */
    public void clearPlayer(UUID playerId) {
        callWindows.remove(playerId);
        trippedUntil.remove(playerId);
    }

    int currentCount(UUID playerId) {
        ConcurrentLinkedDeque<Long> window = callWindows.get(playerId);
        if (window == null) return 0;
        long threshold = System.currentTimeMillis() - WINDOW_MILLIS;
        int count = 0;
        synchronized (window) {
            for (Long ts : window) if (ts >= threshold) count++;
        }
        return count;
    }

    boolean isTripped(UUID playerId) {
        Long until = trippedUntil.get(playerId);
        return until != null && until > System.currentTimeMillis();
    }

    AtomicInteger activePlayerCount() {
        AtomicInteger n = new AtomicInteger();
        callWindows.forEachValue(32, w -> {
            if (!w.isEmpty()) n.incrementAndGet();
        });
        return n;
    }
}
