package com.zm.kilacraftAI.service.guardian;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.scheduler.ManagedTask;
import com.zm.kilacraftAI.service.guardian.action.Outcome;
import com.zm.kilacraftAI.service.guardian.monitor.GuardianRuntime;
import com.zm.kilacraftAI.service.guardian.monitor.Monitor;
import com.zm.kilacraftAI.service.guardian.monitor.MonitorState;
import com.zm.kilacraftAI.service.guardian.predicate.BlockPos;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerStateService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * 守护运行时引擎：外层心跳合批 + IO 池 fan-out + 频率治理。
 *
 * <p>实现 {@link ManagedTask}（注册为单个高频心跳，CAS 防重入由 {@code TaskScheduler} 负责），
 * 同时实现 {@link GuardianRuntime}（事件型/定时型源的注册与信号提交）。三路信号汇聚到
 * {@link #submitSignal}：轮询由心跳按 cadence 驱动、事件由全局 Listener 分发、定时由 runAsyncTimer/runTaskLater 驱动。</p>
 *
 * <p>线程模型：心跳在异步定时线程跑；每玩家 tick 与每信号 eval 都 fan-out 到 IO 池。
 * 同一 monitor 的 eval 用 per-monitor {@link ReentrantLock} {@code tryLock} 串行——
 * 重叠的 eval 直接跳过（守护低频，下一轮/下次事件重试），避免并发改写谓词/退避计数。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class GuardianEngine implements ManagedTask, GuardianRuntime {

    private static final String LOG_MODULE = "守护系统";
    private static final long DEFAULT_TICK_INTERVAL_TICKS = 20L; // ~1s
    private static final long TICKS_TO_MILLIS = 50L;

    private final KilacraftAI plugin;
    private final PlayerStateService playerStateService;
    private final long tickIntervalTicks;

    /** 玩家 → Guardian（内存态）。 */
    private final Map<UUID, Guardian> guardians = new ConcurrentHashMap<>();
    /** Monitor → 所属玩家（事件/定时信号时反查 player）。 */
    private final Map<Monitor, UUID> monitorOwner = new ConcurrentHashMap<>();
    /** Monitor → 事件注册（事件型源）。 */
    private final Map<Monitor, EventRegistration<?>> eventRegistrations = new ConcurrentHashMap<>();
    /** Monitor → 定时任务句柄（定时型源）。 */
    private final Map<Monitor, FoliaCompat.ScheduledTask> scheduledTasks = new ConcurrentHashMap<>();
    /** Player → eval 串行锁：同一玩家的所有 monitor eval 串行，保证 hub 闸门 check-then-fire 原子（防跨 monitor 竞态绕过冷却）。 */
    private final Map<UUID, ReentrantLock> playerEvalLocks = new ConcurrentHashMap<>();

    private volatile boolean shutdown = false;
    private GuardianEventListener eventListener;

    public GuardianEngine(KilacraftAI plugin, PlayerStateService playerStateService) {
        this(plugin, playerStateService, DEFAULT_TICK_INTERVAL_TICKS);
    }

    public GuardianEngine(KilacraftAI plugin, PlayerStateService playerStateService, long tickIntervalTicks) {
        this.plugin = plugin;
        this.playerStateService = playerStateService;
        this.tickIntervalTicks = tickIntervalTicks;
    }

    /** 注册全局事件 Listener（插件 onEnable 调用）。manager 用于 join/quit 生命周期。 */
    public void start(GuardianManager manager) {
        if (eventListener == null) {
            eventListener = new GuardianEventListener(this, manager);
            Bukkit.getPluginManager().registerEvents(eventListener, plugin);
        }
    }

    /** 标记关闭、摘除 Listener（插件 onDisable，须在 taskScheduler.shutdownAll 之前）。 */
    public void shutdown() {
        shutdown = true;
        if (eventListener != null) {
            HandlerList.unregisterAll(eventListener);
            eventListener = null;
        }
        for (FoliaCompat.ScheduledTask task : scheduledTasks.values()) {
            task.cancel();
        }
        scheduledTasks.clear();
        // 释放全部引用，防止热重载/独立调用 shutdown 时残留
        guardians.clear();
        monitorOwner.clear();
        eventRegistrations.clear();
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
        return tickIntervalTicks;
    }

    @Override
    public boolean enabled() {
        return !shutdown;
    }

    /** 心跳：每玩家异步 tick（慢快照不拖累其他人）。返回 0 避免 TaskScheduler 每秒刷日志。 */
    @Override
    public int execute() {
        if (shutdown) {
            return 0;
        }
        // 全局开关关闭时不跑心跳——防"服主关了开关但已启用玩家仍在被烧 token/发告警"
        if (plugin.getGuardianConfigManager() != null && !plugin.getGuardianConfigManager().isEnabled()) {
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
            } catch (RejectedExecutionException ignored) {
                // IO 池饱和——本轮跳过该玩家，下轮重试
            }
        }
        return 0;
    }

    void tickPlayer(Player player, Guardian guardian, long now) {
        if (shutdown || !player.isOnline()) {
            return;
        }
        List<Monitor> due = duePollingMonitors(guardian, now);
        if (due.isEmpty()) {
            return;
        }
        Set<BlockPos> furnaces = collectFurnacePositions(due);
        PlayerState state;
        try {
            state = playerStateService.snapshot(player, furnaces);
        } catch (Exception e) {
            // callSync 超时等异常不应传播到 IO 池（否则进 stderr 静默卡死），降级为本轮跳过
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("守护快照采集失败（玩家 {}）: {}", player.getName(), e.getMessage()));
            return;
        }
        if (state == null) {
            return;
        }
        for (Monitor m : due) {
            GuardianContext ctx = GuardianContext.of(player, m.id(), Optional.empty());
            evalAsync(m, state, ctx);
        }
    }

    /** 到点的轮询型 monitor（非终态/非暂停、cadence 已过）。 */
    List<Monitor> duePollingMonitors(Guardian guardian, long nowMillis) {
        List<Monitor> out = new ArrayList<>();
        for (Monitor m : guardian.monitors()) {
            if (!m.isPolling()) {
                continue;
            }
            MonitorState s = m.state();
            if (s.isTerminal() || s == MonitorState.PAUSED) {
                continue;
            }
            long cadenceMs = m.cadenceTicks() * TICKS_TO_MILLIS;
            if (nowMillis - m.lastEvalMillis() >= cadenceMs) {
                out.add(m);
            }
        }
        return out;
    }

    /** 收集一组 monitor 的熔炉位置并集（一次 snapshot 喂所有，拉取式采集避免每个谓词各自同步取数）。 */
    static Set<BlockPos> collectFurnacePositions(List<Monitor> monitors) {
        Set<BlockPos> all = new HashSet<>();
        for (Monitor m : monitors) {
            all.addAll(m.requestedFurnacePositions());
        }
        return all;
    }

    @Override
    public <T extends Event> void registerEventMonitor(Monitor monitor, Class<T> eventType, Predicate<T> filter) {
        if (!GuardianEventListener.isSupported(eventType)) {
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("不支持的事件类型（需在 GuardianEventListener 登记）: {}", eventType.getSimpleName()));
            return;
        }
        eventRegistrations.put(monitor, new EventRegistration<>(eventType, filter));
    }

    @Override
    public void scheduleMonitor(Monitor monitor, long delayTicks, long intervalTicks) {
        FoliaCompat.ScheduledTask task;
        if (intervalTicks > 0) {
            task = FoliaCompat.runAsyncTimer(plugin, () -> fireScheduled(monitor), delayTicks, intervalTicks);
        } else {
            task = FoliaCompat.runTaskLater(plugin, () -> fireScheduled(monitor), delayTicks);
        }
        if (task != null) {
            scheduledTasks.put(monitor, task);
        }
    }

    private void fireScheduled(Monitor monitor) {
        if (shutdown) {
            return;
        }
        Player player = playerFor(monitor);
        if (player == null) {
            return;
        }
        submitSignal(monitor, GuardianContext.of(player, monitor.id(), Optional.empty()));
    }

    @Override
    public void unregister(Monitor monitor) {
        eventRegistrations.remove(monitor);
        FoliaCompat.ScheduledTask task = scheduledTasks.remove(monitor);
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public void submitSignal(Monitor monitor, GuardianContext ctx) {
        if (shutdown) {
            return;
        }
        // 全局开关关闭时不处理信号（与心跳一致，防已启用玩家在关后被烧 token）
        if (plugin.getGuardianConfigManager() != null && !plugin.getGuardianConfigManager().isEnabled()) {
            return;
        }
        Player player = ctx.player();
        if (player == null || !player.isOnline()) {
            return;
        }
        // 轻量冷却预检：snapshot 前先挡掉冷却中的信号，避免事件风暴时每次都做主线程快照
        GuardianCooldownHub hub = hubFor(player);
        if (hub != null && !hub.shouldEvaluate(monitor, ctx.nowMillis())) {
            return;
        }
        Set<BlockPos> furnaces = monitor.requestedFurnacePositions();
        PlayerState state;
        try {
            state = playerStateService.snapshot(player, furnaces);
        } catch (Exception e) {
            // 同 tickPlayer：快照异常降级跳过，不传播
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("守护快照采集失败（玩家 {}）: {}", player.getName(), e.getMessage()));
            return;
        }
        if (state == null) {
            return;
        }
        evalAsync(monitor, state, ctx);
    }

    /** 事件 Listener 命中后回调：在所属玩家的 guardian 中找匹配的事件型 monitor。 */
    @SuppressWarnings("unchecked")
    <T extends Event> void dispatchEvent(Class<T> type, T event, Player player) {
        Guardian g = guardians.get(player.getUniqueId());
        if (g == null) {
            return;
        }
        for (Monitor m : g.monitors()) {
            EventRegistration<?> reg = eventRegistrations.get(m);
            if (reg == null || reg.eventType != type) {
                continue;
            }
            try {
                if (((Predicate<T>) reg.filter).test(event)) {
                    // 异步化：Folia 下事件分发在区域线程，submitSignal 内的 snapshot 会 callSyncOnEntity
                    // 投递回同一区域线程，若在事件线程同步等会自死锁。投到 IO 池异步处理避免阻塞事件链。
                    final GuardianContext ctx = GuardianContext.of(player, m.id(), Optional.empty());
                    try {
                        FoliaCompat.getIOPool().execute(() -> submitSignal(m, ctx));
                    } catch (RejectedExecutionException ignored2) {
                        // IO 池饱和——跳过本次事件
                    }
                }
            } catch (ClassCastException ignored) {
                // 类型不匹配的注册不应出现（注册时校验），防御跳过
            }
        }
    }

    /**
     * 异步求值一个 monitor。hub 闸门的 check 与 onFired 都在 per-player lock 内，
     * 保证同一玩家跨 monitor 的 check-then-fire 原子（防两个不同 monitor 的事件并发绕过冷却）。
     * tryLock 跳过重叠 eval（守护低频，下轮/下次事件重试）。
     */
    private void evalAsync(Monitor monitor, PlayerState state, GuardianContext ctx) {
        try {
            FoliaCompat.getIOPool().execute(() -> {
                // per-player 锁：串行化同玩家的 monitor eval，保证 hub 冷却闸门不被并发绕过
                ReentrantLock lock = playerEvalLocks.computeIfAbsent(ctx.player().getUniqueId(), k -> new ReentrantLock());
                if (!lock.tryLock()) {
                    return;
                }
                try {
                    // shutdown 后在途 eval 不再执行动作（防关服后仍烧 LLM token）
                    if (shutdown) {
                        return;
                    }
                    // eval 前再次校验在线：hub==null 检查到 eval 之间玩家可能下线
                    if (!ctx.player().isOnline()) {
                        return;
                    }
                    GuardianCooldownHub hub = hubFor(ctx.player());
                    // hub==null 表示该玩家已无活跃 Guardian（下线/停用），不应再 eval——否则会对已下线玩家发输出
                    if (hub == null) {
                        return;
                    }
                    if (!hub.shouldEvaluate(monitor, ctx.nowMillis())) {
                        return;
                    }
                    Optional<Outcome> result = monitor.eval(state, ctx);
                    if (result.isPresent()) {
                        hub.onFired(monitor, ctx.nowMillis());
                    }
                } catch (Exception e) {
                    PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("monitor 求值异常: {}", e.getMessage()), e);
                } finally {
                    lock.unlock();
                }
            });
        } catch (RejectedExecutionException ignored) {
            // IO 池饱和——跳过
        }
    }

    private GuardianCooldownHub hubFor(Player player) {
        if (player == null) {
            return null;
        }
        Guardian g = guardians.get(player.getUniqueId());
        return g != null ? g.hub() : null;
    }

    public void registerGuardian(UUID playerId, Guardian guardian) {
        guardians.put(playerId, guardian);
        for (Monitor m : guardian.monitors()) {
            monitorOwner.put(m, playerId);
            m.resume();
            m.source().bind(this, m);
        }
    }

    public void unregisterGuardian(UUID playerId) {
        Guardian g = guardians.remove(playerId);
        if (g == null) {
            return;
        }
        for (Monitor m : g.monitors()) {
            m.markPaused();
            m.source().unbind(this, m);
            monitorOwner.remove(m);
        }
        // 释放 per-player eval 锁，避免长期离线玩家累积
        playerEvalLocks.remove(playerId);
    }

    public Guardian getGuardian(UUID playerId) {
        return guardians.get(playerId);
    }

    private Player playerFor(Monitor monitor) {
        UUID id = monitorOwner.get(monitor);
        return id != null ? Bukkit.getPlayer(id) : null;
    }

    /** 事件注册记录（类型 + 过滤谓词）。 */
    private static final class EventRegistration<T extends Event> {
        final Class<T> eventType;
        final Predicate<T> filter;

        EventRegistration(Class<T> eventType, Predicate<T> filter) {
            this.eventType = eventType;
            this.filter = filter;
        }
    }
}
