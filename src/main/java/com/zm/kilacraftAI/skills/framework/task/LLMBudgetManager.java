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
 * <p>统一治理所有 LLM 入口（玩家主动聊天 / 守护被动输出 / 登录问候）的 per-player 调用频率，
 * 仅在异常工况（刷怪塔信号风暴、闸门 bug、配置错误导致狂调）下熔断，正常使用永不触发。
 * 不替代 {@code AIRequestValidatorUtil} 的聊天冷却（那是玩家主动入口的防刷屏），
 * 也不替代守护自身的 {@code GuardianCooldownHub}（那是开口频率/反馈调节）。</p>
 *
 * <p>线程模型：所有方法线程安全。计数靠滑动窗口（默认 1 小时），过期时间戳惰性淘汰。
 * 单例，由 {@link LLMOutputCoordinator} 持有。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-02
 */
public class LLMBudgetManager {

    /** 默认预算上限：正常使用绝对达不到，仅防 runaway。服主可在 llm.yml 覆盖。 */
    static final int DEFAULT_BUDGET_PER_HOUR = 200;
    private static final long WINDOW_MILLIS = 60L * 60L * 1000L;

    /** 玩家主动请求优先级最高，守护被动输出可被丢弃/降级，问候次之。 */
    public enum Priority {
        /** 玩家主动发起（聊天/skill/task）——最高优先级，不受守护熔断降级影响。 */
        PLAYER_ACTIVE,
        /** 登录问候——次高，被动但玩家可见。 */
        GREETING,
        /** 守护被动输出——最低，熔断时降级为模板/丢弃。 */
        GUARDIAN
    }

    private volatile int budgetPerHour;
    private final ConcurrentHashMap<UUID, ConcurrentLinkedDeque<Long>> callWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> trippedUntil = new ConcurrentHashMap<>();

    public LLMBudgetManager(int budgetPerHour) {
        // ≤0 表示禁用治理（运行时 tryAcquire/recordCall 直通）；存原值让运行时检查判定。
        this.budgetPerHour = budgetPerHour;
    }

    /** reload 时更新预算上限（volatile 快照发布，跨线程立即可见）。≤0 禁用。 */
    public void updateBudget(int newBudget) {
        this.budgetPerHour = newBudget;
    }

    /** 由输出场景推导优先级。守护专用 GUARDIAN 场景映射为被动优先级（熔断时可降级）。 */
    public static Priority priorityOf(OutputScenarioEnum scenario) {
        if (scenario == null) return Priority.GUARDIAN;
        return switch (scenario) {
            case NORMAL_CHAT, SKILL_RESULT, TASK_RESULT -> Priority.PLAYER_ACTIVE;
            case GREETING -> Priority.GREETING;
            // 守护主动输出（L1 模板不经此处，L3 走 coordinator 经预算层）+ 错误：被动优先级。
            case GUARDIAN, ERROR -> Priority.GUARDIAN;
        };
    }

    /**
     * 判断本次 LLM 调用是否被允许。
     *
     * <p>熔断语义按优先级区分（玩家主动 > 守护被动，不让玩家等）：
     * PLAYER_ACTIVE 永不熔断（玩家自己发起的请求必须响应，即使超预算也照常，仅记 warn）；
     * GREETING / GUARDIAN 在熔断窗口内被拒。budgetPerHour≤0 视为禁用治理（全部放行）。</p>
     *
     * @return true 放行；false 当前处于熔断窗口、且本调用属可降级优先级。
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
     * 记一次实际发生的 LLM 调用（无论优先级）。
     *
     * <p>PLAYER_ACTIVE 超预算时仅记 warn（不熔断玩家），但 GREETING/GUARDIAN 后续调用会被熔断。
     * 即：玩家风暴时，守护/问候先被压，玩家请求仍响应——保护玩家体验。</p>
     */
    public void recordCall(UUID playerId) {
        recordCall(playerId, Priority.PLAYER_ACTIVE);
    }

    public void recordCall(UUID playerId, Priority priority) {
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
                    if (priority != Priority.PLAYER_ACTIVE) {
                        PluginLoggerUtil.warn("守护系统", "LLM 预算熔断：玩家 {} 1 小时内调用 {} 次超阈值 {}，被动输出降级 1 小时",
                                playerId, window.size(), budgetPerHour);
                    } else {
                        PluginLoggerUtil.warn("守护系统", "LLM 预算超限：玩家 {} 1 小时内调用 {} 次超阈值 {}（玩家主动请求仍响应）",
                                playerId, window.size(), budgetPerHour);
                    }
                }
            }
        }
    }

    /** 玩家下线/清理时释放窗口（避免长期离线玩家的 Deque 累积）。 */
    public void clearPlayer(UUID playerId) {
        callWindows.remove(playerId);
        trippedUntil.remove(playerId);
    }

    // —— 测试可见性（包私有，仅单测用）——

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
        callWindows.forEachValue(32, w -> { if (!w.isEmpty()) n.incrementAndGet(); });
        return n;
    }
}
