package com.zm.kilacraftAI.service.guardian;

import com.zm.kilacraftAI.service.guardian.monitor.Monitor;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 跨 monitor 的防刷屏协调层（§4.5 / §14.4）。每玩家一个，由 {@link Guardian} 持有。
 *
 * <p>五重闸门，按优先级递降求值（命中即抑制）：
 * <ol>
 *   <li><b>静音列表</b>（反馈即配置）：玩家显式静音的分类，绝对压制（含 CRITICAL）——玩家意愿最高。</li>
 *   <li><b>CRITICAL 抢占</b>：{@link AlertPriority#CRITICAL} 直接放行，跳过下方的相关性/冷却。</li>
 *   <li><b>画像相关性</b>（D15，默认 always-relevant，ProfileManager 后续接入）：建筑党压制战斗等。</li>
 *   <li><b>全局冷却</b>：任一 monitor 开火后所有 monitor 静默 N 秒（防连珠）。</li>
 *   <li><b>分类冷却</b>：同分类 M 秒内不重复（5 分钟内不重复「食物不够」）。{@link AlertCategory#GENERAL} 不受此约束。</li>
 * </ol>
 *
 * <p>无硬编码频率阈值（D5/D6）——频率由「冷却/滞回 + 玩家反馈」驱动。线程安全：字段用 volatile/Concurrent，
 * shouldEvaluate 与 onFired 由 GuardianEngine 的 per-monitor 锁串行内调用。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class GuardianCooldownHub {

    private static final long DEFAULT_GLOBAL_COOLDOWN_MS = 5_000L;
    private static final long DEFAULT_CATEGORY_COOLDOWN_MS = 300_000L; // 5 分钟

    private final long globalCooldownMillis;
    private final long categoryCooldownMillis;
    private final Predicate<AlertCategory> relevance;

    private volatile boolean hasFiredGlobal;
    private volatile long lastGlobalFireMillis;
    private final Map<AlertCategory, Long> lastCategoryFireMillis = new ConcurrentHashMap<>();
    private final Set<AlertCategory> silenced = ConcurrentHashMap.newKeySet();

    public GuardianCooldownHub() {
        this(DEFAULT_GLOBAL_COOLDOWN_MS, DEFAULT_CATEGORY_COOLDOWN_MS, cat -> true);
    }

    public GuardianCooldownHub(long globalCooldownMillis, long categoryCooldownMillis, Predicate<AlertCategory> relevance) {
        if (globalCooldownMillis < 0) {
            throw new IllegalArgumentException("globalCooldownMillis 不得为负: " + globalCooldownMillis);
        }
        if (categoryCooldownMillis < 0) {
            throw new IllegalArgumentException("categoryCooldownMillis 不得为负: " + categoryCooldownMillis);
        }
        this.globalCooldownMillis = globalCooldownMillis;
        this.categoryCooldownMillis = categoryCooldownMillis;
        this.relevance = relevance != null ? relevance : cat -> true;
    }

    /**
     * 引擎在派发 eval 前询问：该 monitor 此刻是否允许求值（开火）。
     * 命中任一抑制条件即返回 false。
     */
    public boolean shouldEvaluate(Monitor monitor, long nowMillis) {
        Objects.requireNonNull(monitor, "monitor");
        AlertCategory cat = monitor.category();
        AlertPriority pri = monitor.priority();

        // 1. 静音列表：玩家显式反馈，绝对压制（含 CRITICAL）
        if (silenced.contains(cat)) {
            return false;
        }
        // 2. CRITICAL 抢占：跳过相关性 + 冷却
        if (pri.isCritical()) {
            return true;
        }
        // 3. 画像相关性
        if (!relevance.test(cat)) {
            return false;
        }
        // 4. 全局冷却（首火前不受约束——hasFiredGlobal 区分「未开火」与「t=0 开火」）
        if (hasFiredGlobal && nowMillis - lastGlobalFireMillis < globalCooldownMillis) {
            return false;
        }
        // 5. 分类冷却（GENERAL 豁免——未分类只走全局）
        if (cat != AlertCategory.GENERAL) {
            Long lastCat = lastCategoryFireMillis.get(cat);
            if (lastCat != null && nowMillis - lastCat < categoryCooldownMillis) {
                return false;
            }
        }
        return true;
    }

    /** monitor 实际开火后由引擎回调：更新全局 + 分类冷却锚点。 */
    public void onFired(Monitor monitor, long nowMillis) {
        hasFiredGlobal = true;
        lastGlobalFireMillis = nowMillis;
        lastCategoryFireMillis.put(monitor.category(), nowMillis);
    }

    // ==================== 反馈即配置（静音列表） ====================

    public void silence(AlertCategory category) {
        silenced.add(category);
    }

    public void unsilence(AlertCategory category) {
        silenced.remove(category);
    }

    public boolean isSilenced(AlertCategory category) {
        return silenced.contains(category);
    }

    /** 当前静音的分类快照（持久化 Step 7 写 guardian_profile.silence_list）。 */
    public Set<AlertCategory> silencedCategories() {
        return Set.copyOf(silenced);
    }

    // ==================== 测试/调试查询 ====================

    public long globalCooldownMillis() {
        return globalCooldownMillis;
    }

    public long categoryCooldownMillis() {
        return categoryCooldownMillis;
    }
}
