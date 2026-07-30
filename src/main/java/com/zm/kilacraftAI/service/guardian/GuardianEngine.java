package com.zm.kilacraftAI.service.guardian;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.GuardianConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.scheduler.ManagedTask;
import com.zm.kilacraftAI.service.guardian.monitor.Monitor;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerStateService;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;

import static org.bukkit.Bukkit.getPlayer;

/**
 * 守护运行时引擎：外层心跳合批 + IO 池 fan-out + 频率治理。
 *
 * <p>实现 {@link ManagedTask}（注册为单个高频心跳，CAS 防重入由 {@code TaskScheduler} 负责）。
 * 两路信号：轮询型由心跳按 cadence 驱动 {@link #tickPlayer}；事件型由 {@link GuardianEventListener}
 * 分发到 {@link #dispatchEvent}，对事件型 monitor 即时求值 triggerPredicate。</p>
 *
 * <p>线程模型：心跳在异步定时线程跑；每玩家 tick 与每信号 eval 都 fan-out 到 IO 池。
 * 同一玩家的 monitor eval 用 per-player {@link ReentrantLock} {@code tryLock} 串行——
 * 重叠的 eval 直接跳过（守护低频，下一轮/下次事件重试），避免并发改写谓词/冷却字段。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class GuardianEngine implements ManagedTask {

    private static final String LOG_MODULE = "守护系统";
    private static final long DEFAULT_TICK_INTERVAL_TICKS = 20L; // ~1s
    private static final long TICKS_TO_MILLIS = 50L;

    private final KilacraftAI plugin;
    private final PlayerStateService playerStateService;
    private final PlayerActivityTracker activityTracker;

    /**
     * 玩家 → Guardian（内存态）。
     */
    private final Map<UUID, Guardian> guardians = new ConcurrentHashMap<>();
    /**
     * Player → eval 串行锁：同一玩家的所有 monitor eval 串行，保证 eval 与冷却字段不并发改写。
     */
    private final Map<UUID, ReentrantLock> playerEvalLocks = new ConcurrentHashMap<>();
    /**
     * 已记录"进入挂机"的玩家集合，用于 AFK 边沿去重日志（进入/退出各记一次，而非每秒心跳刷屏）。
     */
    private final Set<UUID> afkLogged = ConcurrentHashMap.newKeySet();

    private volatile boolean shutdown = false;
    private volatile GuardianEventListener eventListener;

    public GuardianEngine(KilacraftAI plugin, PlayerStateService playerStateService) {
        this(plugin, playerStateService, null);
    }

    public GuardianEngine(KilacraftAI plugin, PlayerStateService playerStateService, PlayerActivityTracker activityTracker) {
        this.plugin = plugin;
        this.playerStateService = playerStateService;
        this.activityTracker = activityTracker;
    }

    /**
     * 注册全局事件 Listener（插件 onEnable 调用）。
     */
    public void start() {
        if (eventListener == null) {
            eventListener = new GuardianEventListener(this);
            Bukkit.getPluginManager().registerEvents(eventListener, plugin);
        }
    }

    /**
     * 标记关闭、摘除 Listener、中断在途 LLM 动作（插件 onDisable，须在 taskScheduler.shutdownAll 之前）。
     */
    public void shutdown() {
        shutdown = true;
        if (eventListener != null) {
            HandlerList.unregisterAll(eventListener);
            eventListener = null;
        }
        // 中断在途守护 LLM 调用 + 收尾流式 UI，避免动作在后续子系统关闭后完成导致写入已关闭的 persistence/DB
        if (plugin != null && plugin.getLlmManager() != null && plugin.getLlmManager().getCurrentProvider() != null) {
            for (UUID playerId : guardians.keySet()) {
                plugin.getLlmManager().getCurrentProvider().cancelInFlight(playerId);
                if (plugin.getResponsePipeline() != null) {
                    Player p = getPlayer(playerId);
                    if (p != null) {
                        plugin.getResponsePipeline().cancelStream(p);
                    }
                }
            }
        }
        guardians.clear();
        playerEvalLocks.clear();
        PluginLoggerUtil.info(LOG_MODULE, I18nService.tr("守护引擎已标记关闭"));
    }

    @Override
    public String name() {
        return I18nService.tr("守护心跳");
    }

    @Override
    public String description() {
        return I18nService.tr("守护系统轮询心跳");
    }

    @Override
    public long delayTicks() {
        return 400L; // 首次延迟 20s，等服务稳定
    }

    @Override
    public long intervalTicks() {
        GuardianConfigManager cm = plugin != null ? plugin.getGuardianConfigManager() : null;
        return cm != null ? cm.getHeartbeatIntervalTicks() : DEFAULT_TICK_INTERVAL_TICKS;
    }

    @Override
    public boolean enabled() {
        return !shutdown;
    }

    /**
     * 心跳：每玩家异步 tick（慢快照不拖累其他人）。返回 0 避免 TaskScheduler 每秒刷日志。
     */
    @Override
    public int execute() {
        if (shutdown) {
            return 0;
        }
        // 全局开关关闭时不跑心跳——防"服主关了开关但已启用玩家仍在被烧 token/发告警"
        if (plugin != null && plugin.getGuardianConfigManager() != null && !plugin.getGuardianConfigManager().isEnabled()) {
            return 0;
        }
        final long now = System.currentTimeMillis();
        // 快照在线玩家列表，避免遍历期间玩家上下线导致 CME
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player player : online) {
            Guardian g = guardians.get(player.getUniqueId());
            if (g == null) {
                continue;
            }
            try {
                FoliaCompat.getIOPool().execute(() -> tickPlayer(player, g, now));
            } catch (RejectedExecutionException e) {
                PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("IO 池饱和，跳过心跳（玩家 {}）", player.getName()));
            }
        }
        return 0;
    }

    void tickPlayer(Player player, Guardian guardian, long now) {
        if (shutdown || !player.isOnline()) {
            return;
        }
        if (isAfkSkipWithEdgeLog(player)) {
            return;
        }
        List<Monitor> due = duePollingMonitors(guardian, now);
        if (due.isEmpty()) {
            return;
        }
        PlayerState state;
        try {
            state = playerStateService.snapshot(player);
        } catch (Exception e) {
            // callSync 超时等异常不应传播到 IO 池（否则进 stderr 静默卡死），降级为本轮跳过
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("守护快照采集失败（玩家 {}）: {}", player.getName(), e.getMessage()));
            return;
        }
        if (state == null) {
            return;
        }
        for (Monitor m : due) {
            GuardianContext ctx = GuardianContext.of(player, (Double) null);
            evalAsync(m, state, ctx, false);
        }
    }

    /**
     * AFK 跳过判定 + 边沿去重日志：玩家挂机时跳过守护，但仅在状态翻转时记日志
     * （进入挂机记一次、退出挂机记一次），避免每秒心跳刷屏。tickPlayer 与 submitSignal 共用。
     */
    private boolean isAfkSkipWithEdgeLog(Player player) {
        if (activityTracker == null || !activityTracker.isAfk(player.getUniqueId())) {
            // 非挂机：若此前记录过"进入挂机"，则本次是退出边沿
            if (afkLogged.remove(player.getUniqueId())) {
                PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("玩家 {} 退出挂机状态，恢复守护", player.getName()));
            }
            return false;
        }
        // 挂机：仅首次进入记日志，后续心跳静默跳过
        if (afkLogged.add(player.getUniqueId())) {
            PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("玩家 {} 进入挂机状态，暂停守护", player.getName()));
        }
        return true;
    }

    /**
     * 到点的轮询型 monitor（非挂起、cadence 已过）。
     */
    List<Monitor> duePollingMonitors(Guardian guardian, long nowMillis) {
        List<Monitor> out = new ArrayList<>();
        for (Monitor m : guardian.monitors()) {
            if (!m.isPolling() || m.isPaused()) {
                continue;
            }
            long cadenceMs = m.cadenceTicks() * TICKS_TO_MILLIS;
            if (nowMillis - m.lastEvalMillis() >= cadenceMs) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * 事件型回调（携带触发实体类型供 LLM 消息渲染）。entityType 为 null 时不填充。
     * 对命中的事件型 monitor 即时求值 triggerPredicate（若有），通过则提交信号。
     */
    @SuppressWarnings("unchecked")
    <T extends Event> void dispatchEvent(Class<T> type, T event, Player player, EntityType entityType) {
        Guardian g = guardians.get(player.getUniqueId());
        if (g == null) {
            return;
        }
        for (Monitor m : g.monitors()) {
            if (m.eventType() != type) {
                continue;
            }
            java.util.function.Predicate<Event> filter = m.eventFilter();
            try {
                if (filter != null && !filter.test(event)) {
                    PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("守护事件被 filter 拒绝（玩家 {}，monitor={}）", player.getName(), m.id()));
                    continue;
                }
            } catch (ClassCastException e) {
                // filter 期望类型与 eventType 不匹配——该 monitor 永远不会触发
                PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("守护事件 filter 类型转换异常（玩家 {}，monitor={}）: {}", player.getName(), m.id(), e.getMessage()));
                continue;
            }
            final GuardianContext ctx = entityType != null ? GuardianContext.of(player, entityType) : GuardianContext.of(player, (Double) null);
            submitSignal(m, ctx);
        }
    }

    /**
     * 提交一个事件型 monitor 信号。事件 filter 已命中；若 monitor 带 triggerPredicate 则在锁内快照后求值。
     */
    private void submitSignal(Monitor monitor, GuardianContext ctx) {
        if (shutdown) {
            return;
        }
        if (plugin != null && plugin.getGuardianConfigManager() != null && !plugin.getGuardianConfigManager().isEnabled()) {
            return;
        }
        Player player = ctx.player();
        if (player == null || !player.isOnline()) {
            return;
        }
        if (isAfkSkipWithEdgeLog(player)) {
            return; // 挂机：事件型也不告警
        }
        // 事件型带 triggerPredicate（视野外威胁）：锁内快照 + 求值，通过则进 executeAction
        if (monitor.hasTriggerPredicate()) {
            evalAsync(monitor, null, ctx, true);
        } else {
            // 无 triggerPredicate（事件 filter 即门控）：直接执行动作
            runAction(monitor, ctx);
        }
    }

    /**
     * 异步求值一个 monitor（轮询型，或事件型带 triggerPredicate）。
     * snapshot + eval 都在 per-player lock 内，保证 eval 与冷却字段不并发改写。
     * 动作执行（LLM 调用）在锁外异步进行。失败=本轮跳过，不改 monitor 状态。
     *
     * @param state        预采集的快照；null 表示需要在锁内采集（事件型路径）
     * @param needSnapshot state 为 null 时是否在锁内做 snapshot
     */
    private void evalAsync(Monitor monitor, PlayerState state, GuardianContext ctx, boolean needSnapshot) {
        try {
            FoliaCompat.getIOPool().execute(() -> {
                if (shutdown || !ctx.player().isOnline()) {
                    return;
                }
                ReentrantLock lock = playerEvalLocks.get(ctx.player().getUniqueId());
                if (lock == null) {
                    return;
                }
                if (!lock.tryLock()) {
                    // 事件型带 triggerPredicate 的威胁若遇 eval 锁竞争被跳过，错过即错过（事件无下一轮）
                    PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("守护 eval 锁竞争跳过（玩家 {}，monitor={}）", ctx.player().getName(), monitor.id()));
                    return;
                }
                GuardianContext actionCtx = null;
                try {
                    if (shutdown || !ctx.player().isOnline()) {
                        return;
                    }
                    PlayerState snapshot = state;
                    if (snapshot == null && needSnapshot) {
                        snapshot = playerStateService.snapshot(ctx.player());
                    }
                    if (snapshot == null) {
                        // 事件型 monitor 命中后快照采集返回 null，威胁被静默吞掉
                        PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("守护快照为空跳过（玩家 {}，monitor={}）", ctx.player().getName(), monitor.id()));
                        return;
                    }
                    Optional<GuardianContext> result = monitor.eval(snapshot, ctx);
                    if (result.isPresent()) {
                        actionCtx = result.get();
                    }
                } catch (Exception e) {
                    PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("monitor 求值异常: {}", e.getMessage()), e);
                    return;
                } finally {
                    lock.unlock();
                }
                if (actionCtx != null) {
                    runAction(monitor, actionCtx);
                }
            });
        } catch (RejectedExecutionException e) {
            PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("IO 池饱和，守护 eval 被跳过"));
        }
    }

    /**
     * 锁外异步执行动作（LLM 调用，耗时数秒）。返回值仅用于观测日志，不驱动 monitor 状态。
     * 同步异常（如 IO 池饱和拒绝提交）在此捕获，不让它逃逸出 IO 池 lambda 导致 stderr 静默死亡。
     */
    private void runAction(Monitor monitor, GuardianContext ctx) {
        CompletableFuture<Boolean> future;
        try {
            future = monitor.executeAction(ctx);
        } catch (RejectedExecutionException e) {
            PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("IO 池饱和，守护动作被跳过（monitor={}）", monitor.id()));
            return;
        } catch (Exception e) {
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("monitor 动作提交异常: {}", e.getMessage()), e);
            return;
        }
        future.thenAccept(success -> {
            if (!success) {
                PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("守护动作本轮跳过（monitor={}）", monitor.id()));
            }
        }).exceptionally(e -> {
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("monitor 动作执行异常: {}", e.getMessage()), e);
            return null;
        });
    }

    public void registerGuardian(UUID playerId, Guardian guardian) {
        if (shutdown) {
            return;
        }
        guardians.put(playerId, guardian);
        playerEvalLocks.computeIfAbsent(playerId, k -> new ReentrantLock());
        for (Monitor m : guardian.monitors()) {
            m.resume();
        }
    }

    public void unregisterGuardian(UUID playerId) {
        Guardian g = guardians.remove(playerId);
        if (g == null) {
            return;
        }
        for (Monitor m : g.monitors()) {
            m.markPaused();
        }
        // 释放 per-player eval 锁，避免长期离线玩家累积
        playerEvalLocks.remove(playerId);
        // 清理 AFK 边沿去重状态
        afkLogged.remove(playerId);
    }

}
