package com.zm.kilacraftAI.skills.framework.resume;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.ConfigManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 待确认续体管理器（框架内部，不进 SPI Jar）。
 *
 * <p>每玩家一个槽位：needInfo 时由 {@link com.zm.kilacraftAI.skills.framework.SkillManager} 写入（{@link #save}），
 * 玩家下轮确认/补值时由 {@code AIRequestHandler} 经 {@link #claim} 原子认领并恢复执行。claim 走
 * {@code ConcurrentHashMap.remove}，保证同一槽位不会被并发重复消费。</p>
 *
 * <p>镜像 {@link com.zm.kilacraftAI.skills.framework.SkillSecurityFilter}：
 * 私有构造 + static 缓存 + {@code getInstance()} 单例 + Listener。</p>
 *
 * @author Zm_Mmm
 * @since 2026-06-17
 */
public class PendingResumeManager implements Listener {

    private static final PendingResumeManager INSTANCE = new PendingResumeManager();

    private static final Map<UUID, PendingResume> SLOTS = new ConcurrentHashMap<>();

    /**
     * claim 时暂存的 round，供本次恢复执行若再次 needInfo 时由 {@link #save} 继承（round+1），
     * 使 maxRounds 能正确计数恢复轮次。随 {@link #clear} 一并清理。
     */
    private static final Map<UUID, Integer> RESUME_CARRY = new ConcurrentHashMap<>();

    /**
     * 可注入时钟（测试用），生产代码经 {@link #now()} 读取。
     */
    private LongSupplier clock = System::currentTimeMillis;

    private PendingResumeManager() {
    }

    public static PendingResumeManager getInstance() {
        return INSTANCE;
    }

    /**
     * 测试用：注入可控时钟。
     */
    static void setTestClock(LongSupplier clock) {
        INSTANCE.clock = clock;
    }

    /**
     * 测试用：恢复系统时钟。
     */
    static void resetTestClock() {
        INSTANCE.clock = System::currentTimeMillis;
    }

    private long now() {
        return clock.getAsLong();
    }

    /**
     * 保存或刷新续体。同 skill+action 视为刷新（round+1），否则覆盖为 round=0。
     * round 超过 maxRounds 时拒绝写入并清空。
     */
    public void save(UUID playerId, String skill, String action, Map<String, String> entities, String message) {
        if (!isEnabled() || playerId == null) {
            return;
        }
        long ts = now();
        long ttl = ttlMillis();
        int maxRounds = maxRounds();

        // 优先继承 claim 暂存的 round（恢复执行内再次 needInfo）；否则按同 op 刷新；否则 0
        int round = 0;
        Integer carried = RESUME_CARRY.remove(playerId);
        if (carried != null) {
            round = carried + 1;
        } else {
            PendingResume existing = SLOTS.get(playerId);
            if (existing != null && sameOp(existing, skill, action)) {
                round = existing.getRound() + 1;
            }
        }
        if (round > maxRounds) {
            SLOTS.remove(playerId);
            PluginLoggerUtil.warn("续体", "玩家 {} 的待确认续体 {}.{} 超过最大恢复次数 {}，已清空以防死循环", playerId, skill, action, maxRounds);
            return;
        }

        PendingResume slot = new PendingResume(playerId, skill, action, entities != null ? new HashMap<>(entities) : new HashMap<>(), message, ts, ts + ttl, round);
        SLOTS.put(playerId, slot);
        PluginLoggerUtil.debug("续体", "保存待确认续体：{}.{}（round={}，TTL={}ms）", skill, action, round, ttl);
    }

    /**
     * 读取活跃续体（非消费），过期则清空并返回 null。恢复执行须用 {@link #claim}。
     */
    public PendingResume get(UUID playerId) {
        if (playerId == null || !isEnabled()) {
            return null;
        }
        PendingResume slot = SLOTS.get(playerId);
        if (slot == null) {
            return null;
        }
        if (slot.isExpired(now())) {
            SLOTS.remove(playerId);
            PluginLoggerUtil.debug("续体", "玩家 {} 的待确认续体已过期，已清空", playerId);
            return null;
        }
        return slot;
    }

    /**
     * 原子认领（remove-and-return）并暂存 round。已过期或已被认领返回 null。
     */
    public PendingResume claim(UUID playerId) {
        if (playerId == null || !isEnabled()) {
            return null;
        }
        PendingResume slot = SLOTS.remove(playerId);
        if (slot == null) {
            return null;
        }
        if (slot.isExpired(now())) {
            PluginLoggerUtil.debug("续体", "玩家 {} 的待确认续体认领时发现已过期", playerId);
            RESUME_CARRY.remove(playerId);
            return null;
        }
        RESUME_CARRY.put(playerId, slot.getRound());
        return slot;
    }

    /**
     * 清除玩家续体（含暂存 round）。
     */
    public void clear(UUID playerId) {
        if (playerId != null) {
            SLOTS.remove(playerId);
            RESUME_CARRY.remove(playerId);
        }
    }

    /**
     * 取消玩家续体（语义同 {@link #clear}）。
     */
    public void cancel(UUID playerId) {
        clear(playerId);
    }

    /**
     * 清除全部续体（onDisable 用）。
     */
    public void clearAll() {
        int n = SLOTS.size();
        SLOTS.clear();
        RESUME_CARRY.clear();
        if (n > 0) {
            PluginLoggerUtil.debug("续体", "插件卸载，清除全部 {} 个待确认续体", n);
        }
    }

    /**
     * 清理过期续体（reaper 周期调用）。
     *
     * @return 本次清理移除的条目数
     */
    public int cleanupExpired() {
        long ts = now();
        int removed = 0;
        Iterator<Map.Entry<UUID, PendingResume>> it = SLOTS.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired(ts)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        SLOTS.remove(id);
        RESUME_CARRY.remove(id);
    }

    /**
     * 测试用配置覆盖（非 null 时优先于真实 config）。
     */
    private volatile Boolean enabledOverride;
    private volatile Integer ttlSecondsOverride;
    private volatile Integer maxRoundsOverride;

    /**
     * 测试用：注入配置覆盖。
     */
    static void setTestConfig(Boolean enabled, Integer ttlSeconds, Integer maxRounds) {
        INSTANCE.enabledOverride = enabled;
        INSTANCE.ttlSecondsOverride = ttlSeconds;
        INSTANCE.maxRoundsOverride = maxRounds;
    }

    /**
     * 测试用：清除配置覆盖。
     */
    static void resetTestConfig() {
        INSTANCE.enabledOverride = null;
        INSTANCE.ttlSecondsOverride = null;
        INSTANCE.maxRoundsOverride = null;
    }

    private boolean isEnabled() {
        if (enabledOverride != null) return enabledOverride;
        ConfigManager cfg = config();
        return cfg == null || cfg.isPendingResumeEnabled();
    }

    private long ttlMillis() {
        int seconds = ttlSecondsOverride != null ? ttlSecondsOverride : defaultTtlSeconds();
        return Math.max(1, seconds) * 1000L;
    }

    private int maxRounds() {
        int max = maxRoundsOverride != null ? maxRoundsOverride : defaultMaxRounds();
        return Math.max(1, max);
    }

    private int defaultTtlSeconds() {
        ConfigManager cfg = config();
        return cfg == null ? 300 : cfg.getPendingResumeTtlSeconds();
    }

    private int defaultMaxRounds() {
        ConfigManager cfg = config();
        return cfg == null ? 5 : cfg.getPendingResumeMaxRounds();
    }

    private ConfigManager config() {
        KilacraftAI plugin = KilacraftAI.getInstance();
        return plugin == null ? null : plugin.getConfigManager();
    }

    private static boolean sameOp(PendingResume existing, String skill, String action) {
        return Objects.equals(existing.getSkillName(), skill) && Objects.equals(existing.getAction(), action);
    }
}
