package com.zm.kilacraftAI.service.watch;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.CacheCallTypeEnum;
import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.common.enums.ServerEventTypeEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.WatchConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.model.event.ServerEvent;
import com.zm.kilacraftAI.service.event.EventCollector;
import com.zm.kilacraftAI.skills.framework.*;
import com.zm.kilacraftAI.skills.framework.task.AnalysisSummary;
import com.zm.kilacraftAI.skills.framework.task.LLMOutputCoordinator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 玩家自定义监听服务：支持两类监听来源。
 *
 * <p><b>轮询型（POLLING）</b>：定时执行 skill action 取返回值字段比较判定（如盯血量低于某值、盯背包物品到某数）。
 * 同一玩家的所有轮询 watch 合并为单个定时器任务。</p>
 *
 * <p><b>事件型（EVENT）</b>：通过 {@link PlayerWatchListener}（全局单例，常驻整个生命周期）监听 Bukkit 事件，
 * 命中后经 eventType→WatchRef 反向索引定位订阅者（findEventWatches），命中 filter 即通知
 * （如盯熔炉烧好、盯作物成熟、盯实体死亡）。坐标距离事件基于 watch 创建时快照位置判定。</p>
 *
 * <p><b>线程模型</b>：轮询定时器跑在异步调度线程（非 IO 池）；skill.execute 在异步线程发起，
 * 带超时同步等待结果。事件型由 Bukkit 事件线程触发。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-22
 */
public final class WatchService implements Listener {

    private static final String LOG_MODULE = "自定义监听";

    private final KilacraftAI plugin;
    private final WatchConfigManager configManager;

    /**
     * 玩家 → watch 列表
     */
    private final Map<UUID, List<Watch>> watches = new ConcurrentHashMap<>();
    /**
     * 玩家 → 轮询定时器（合并：同玩家一个定时器遍历所有轮询 watch）
     */
    private final Map<UUID, FoliaCompat.ScheduledTask> pollTasks = new ConcurrentHashMap<>();
    /**
     * 玩家 → 防重入标志（同一玩家不会并发 poll）
     */
    private final Map<UUID, AtomicBoolean> pollLocks = new ConcurrentHashMap<>();
    /**
     * 事件反向索引：eventType → 订阅该事件的 WatchRef 集合。单例 Listener 命中事件后按此定位订阅者。
     */
    private final Map<String, Set<WatchRef>> eventSubscribers = new ConcurrentHashMap<>();
    /**
     * 玩家 → 离线延迟删除定时器
     */
    private final Map<UUID, FoliaCompat.ScheduledTask> offlineTimers = new ConcurrentHashMap<>();
    /**
     * 全局 watch 计数（防群体滥用）
     */
    private final AtomicInteger globalCount = new AtomicInteger(0);

    /**
     * 全局单例事件 Listener（初始化注册一次，常驻整个生命周期）。
     */
    private PlayerWatchListener globalListener;

    private volatile boolean shutdown = false;

    public WatchService(KilacraftAI plugin, WatchConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * 初始化全局单例事件 Listener。由 KilacraftAI.initializeWatchSystem 在 registerEvents 之前调用。
     */
    public void initGlobalListener() {
        if (globalListener == null) {
            globalListener = new PlayerWatchListener(this);
            Bukkit.getPluginManager().registerEvents(globalListener, plugin);
        }
    }


    /**
     * 创建轮询型监听。
     *
     * @return CreateResult（success=true 时含 watchId；false 时含拒绝原因）
     */
    public synchronized CreateResult createPollingWatch(Player player, String skillName, String actionName, String resultPath, Map<String, String> params, String operator, String threshold, String intent, boolean singleShot, String displayName) {
        UUID id = player.getUniqueId();
        List<Watch> existing = watches.get(id);
        // 分类计数：轮询型上限
        int pollingCount = countByMode(existing, WatchMode.POLLING);
        if (pollingCount >= configManager.getMaxPollingWatches()) {
            return new CreateResult(false, null, I18nService.tr("条件监听数已达上限（{} 个），请先取消一些", configManager.getMaxPollingWatches()));
        }
        if (globalCount.get() >= configManager.getMaxWatchesGlobal()) {
            return new CreateResult(false, null, I18nService.tr("服务器监听任务已达上限，请稍后再试"));
        }
        // skill 存在性 + 可监听准入校验
        Skill skill = plugin.getSkillManager() != null ? plugin.getSkillManager().getSkill(skillName) : null;
        if (skill == null) {
            return new CreateResult(false, null, I18nService.tr("未知的技能：{}", skillName));
        }
        if (!(skill instanceof ProbeSource ps) || !ps.getProbeableActions().contains(actionName)) {
            return new CreateResult(false, null, I18nService.tr("技能 {} 的动作 {} 不支持监听", skillName, actionName));
        }
        // 权限兜底校验：防 AI 错误编排无权限 skill（WatchSkill.getDynamicContext 已用 getAvailableSkills 过滤可见列表）
        String requiredPerm = skill.getRequiredPermission();
        if (requiredPerm != null && !player.hasPermission(requiredPerm)) {
            return new CreateResult(false, null, I18nService.tr("你没有权限监听技能：{}", skillName));
        }
        if (resultPath == null || resultPath.isBlank()) {
            return new CreateResult(false, null, I18nService.tr("缺少参数: result_path(返回值字段名)"));
        }

        String watchId = generateWatchId(skillName, actionName);
        String display = (displayName != null && !displayName.isBlank()) ? displayName : (skillName + "." + actionName);
        Watch watch = new Watch(watchId, display, WatchMode.POLLING, skillName, actionName, resultPath, params != null ? params : Map.of(), operator, threshold, intent, singleShot, id, null, null);
        addWatch(id, watch, null);
        PluginLoggerUtil.info(LOG_MODULE, I18nService.tr("玩家 {} 创建条件监听: {}", player.getName(), watchId));
        return new CreateResult(true, watchId, I18nService.tr("已创建条件监听：{}", display));
    }

    /**
     * 创建事件型监听。
     *
     * @return CreateResult（success=true 时含 watchId；false 时含拒绝原因）
     */
    public synchronized CreateResult createEventWatch(Player player, String eventType, Map<String, String> filterParams, String intent, boolean singleShot, String displayName) {
        UUID id = player.getUniqueId();
        List<Watch> existing = watches.get(id);
        // 分类计数：事件型上限
        int eventCount = countByMode(existing, WatchMode.EVENT);
        if (eventCount >= configManager.getMaxEventWatches()) {
            return new CreateResult(false, null, I18nService.tr("事件监听数已达上限（{} 个），请先取消一些", configManager.getMaxEventWatches()));
        }
        if (globalCount.get() >= configManager.getMaxWatchesGlobal()) {
            return new CreateResult(false, null, I18nService.tr("服务器监听任务已达上限，请稍后再试"));
        }
        // eventType 合法性校验（11 种）
        if (!WatchEventTypes.isSupported(eventType)) {
            return new CreateResult(false, null, I18nService.tr("不支持的事件类型：{}", eventType));
        }
        // 坐标距离事件采集快照位置（创建时定格，此后玩家移动不影响归属判定）
        Location center = isSpatialEvent(eventType) ? player.getLocation().clone() : null;

        String watchId = generateWatchId("event", eventType);
        String display = (displayName != null && !displayName.isBlank()) ? displayName : eventType;
        Watch watch = new Watch(watchId, display, WatchMode.EVENT, null, null, null, null, null, null, intent, singleShot, id, eventType, filterParams != null ? filterParams : Map.of());
        addWatch(id, watch, center);
        PluginLoggerUtil.info(LOG_MODULE, I18nService.tr("玩家 {} 创建事件监听: {}", player.getName(), watchId));
        return new CreateResult(true, watchId, I18nService.tr("已创建事件监听：{}", display));
    }

    /**
     * 添加 watch 到玩家列表 + 全局计数 + 确保轮询定时器 + 入事件索引。center 仅 EVENT 坐标距离事件用。
     */
    private void addWatch(UUID id, Watch watch, Location center) {
        watches.computeIfAbsent(id, k -> Collections.synchronizedList(new ArrayList<>())).add(watch);
        globalCount.incrementAndGet();
        if (watch.mode() == WatchMode.POLLING) {
            ensurePollTask(id);
        } else {
            eventSubscribers.computeIfAbsent(watch.eventType(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(new WatchRef(id, watch, center));
        }
    }

    /**
     * 从事件索引移除指定 watch（EVENT 类型）。Set 空时移除 eventType key，防空壳泄漏。
     */
    private void removeFromIndex(Watch watch) {
        if (watch.mode() != WatchMode.EVENT || watch.eventType() == null) return;
        Set<WatchRef> subs = eventSubscribers.get(watch.eventType());
        if (subs == null) return;
        subs.removeIf(ref -> ref.watch() == watch);
        if (subs.isEmpty()) {
            eventSubscribers.remove(watch.eventType(), subs);
        }
    }

    /**
     * eventType 是否属于坐标距离归属事件（需快照位置做距离判定）。
     */
    private static boolean isSpatialEvent(String eventType) {
        return "furnace_smelt".equals(eventType) || "crop_mature".equals(eventType) || "entity_spawn".equals(eventType);
    }

    /**
     * 事件索引条目：watch 与其坐标距离事件的快照位置绑定。center 仅坐标距离事件用，自身/击杀事件为 null。
     */
    record WatchRef(UUID ownerId, Watch watch, Location center) {
    }

    /**
     * 统计某玩家某模式的 watch 数量。
     */
    private static int countByMode(List<Watch> list, WatchMode mode) {
        if (list == null) return 0;
        synchronized (list) {
            int count = 0;
            for (Watch w : list) {
                if (w.mode() == mode) count++;
            }
            return count;
        }
    }

    /**
     * 取消监听。按 watchId 或 displayName 模糊匹配。
     */
    public synchronized SkillResult cancelWatch(UUID ownerId, String watchId, String description) {
        List<Watch> list = watches.get(ownerId);
        if (list == null || list.isEmpty()) {
            return SkillResult.success(I18nService.tr("当前没有任何监听。"));
        }
        // 精确匹配 watchId
        if (watchId != null && !watchId.isBlank()) {
            List<Watch> removed = new ArrayList<>();
            synchronized (list) {
                list.removeIf(w -> {
                    if (w.watchId().equals(watchId)) {
                        removed.add(w);
                        return true;
                    }
                    return false;
                });
            }
            if (!removed.isEmpty()) {
                for (Watch w : removed) removeFromIndex(w);
                globalCount.addAndGet(-removed.size());
                cleanupIfEmpty(ownerId);
                return SkillResult.success(I18nService.tr("已取消监听：{}", watchId));
            }
        }
        // 模糊匹配 description
        if (description != null && !description.isBlank()) {
            List<Watch> snapshot;
            synchronized (list) {
                snapshot = new ArrayList<>(list);
            }
            String lowerDesc = description.toLowerCase();
            List<Watch> matched = snapshot.stream().filter(w -> w.displayName().toLowerCase().contains(lowerDesc)).toList();
            if (matched.size() == 1) {
                Watch target = matched.get(0);
                boolean removed;
                synchronized (list) {
                    removed = list.remove(target);
                }
                if (removed) {
                    removeFromIndex(target);
                    globalCount.decrementAndGet();
                    cleanupIfEmpty(ownerId);
                }
                return SkillResult.success(I18nService.tr("已取消监听：{}", target.displayName()));
            }
            if (matched.isEmpty()) {
                return SkillResult.success(I18nService.tr("没有找到匹配「{}」的监听。", description));
            }
            return SkillResult.needInfo(I18nService.tr("找到多个匹配的监听，请更精确指定：{}", matched.stream().map(Watch::displayName).reduce((a, b) -> a + "、" + b).orElse("")));
        }
        return SkillResult.needInfo(I18nService.tr("要取消哪个监听？可以说「取消盯铁锭的」，或先查询当前监听列表。"));
    }

    /**
     * 查询当前所有活跃监听。
     */
    public SkillResult listWatches(UUID ownerId) {
        List<Watch> list = watches.get(ownerId);
        if (list == null || list.isEmpty()) {
            return SkillResult.success(I18nService.tr("当前没有任何监听。"));
        }
        List<Watch> snapshot;
        synchronized (list) {
            snapshot = new ArrayList<>(list);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> watchList = new ArrayList<>();
        for (Watch w : snapshot) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("watch_id", w.watchId());
            item.put("display_name", w.displayName());
            item.put("mode", w.mode().name().toLowerCase());
            if (w.mode() == WatchMode.POLLING) {
                item.put("source", w.skillName() + "." + w.actionName());
                item.put("condition", w.operator() + " " + w.threshold());
            } else {
                item.put("event_type", w.eventType());
            }
            if (w.intent() != null && !w.intent().isBlank()) {
                item.put("intent", w.intent());
            }
            watchList.add(item);
        }
        data.put("watches", watchList);
        data.put("total", snapshot.size());
        StringBuilder msg = new StringBuilder(I18nService.tr("当前监听（共 {} 个）：\n", snapshot.size()));
        for (Watch w : snapshot) {
            msg.append("- ").append(w.displayName());
            if (w.mode() == WatchMode.POLLING) {
                msg.append("（").append(w.skillName()).append(".").append(w.actionName()).append(" ").append(w.operator()).append(" ").append(w.threshold()).append("）\n");
            } else {
                msg.append("（事件: ").append(w.eventType()).append("）\n");
            }
        }
        return SkillResult.success(msg.toString(), data);
    }

    /**
     * 确保该玩家有轮询定时器在跑（合并模式：一个定时器遍历所有 watch）。
     */
    private void ensurePollTask(UUID playerId) {
        if (pollTasks.containsKey(playerId)) {
            return;
        }
        try {
            long interval = configManager.getPollIntervalTicks();
            FoliaCompat.ScheduledTask task = FoliaCompat.runAsyncTimer(plugin, () -> pollPlayer(playerId), interval, interval);
            pollTasks.put(playerId, task);
        } catch (Exception e) {
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("轮询定时器创建失败，将在玩家下次活动时重试: {}", e.getMessage()));
        }
    }

    /**
     * 轮询单个玩家的所有 POLLING watch。跑在异步调度线程。
     * 防重入：同一玩家不会并发 poll（CAS 标志）。
     */
    private void pollPlayer(UUID playerId) {
        if (shutdown) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        AtomicBoolean lock = pollLocks.computeIfAbsent(playerId, k -> new AtomicBoolean(false));
        // 上一次还在跑
        if (!lock.compareAndSet(false, true)) return;

        try {
            List<Watch> list = watches.get(playerId);
            if (list == null || list.isEmpty()) return;
            List<Watch> snapshot;
            synchronized (list) {
                snapshot = new ArrayList<>(list);
            }
            long now = System.currentTimeMillis();
            long cooldownMs = configManager.getTriggerCooldownSeconds() * 1000L;
            List<Watch> toRemove = new ArrayList<>();
            List<Watch> toFailRemove = new ArrayList<>();

            for (Watch watch : snapshot) {
                if (watch.mode() != WatchMode.POLLING) continue; // 事件型不轮询
                if (now - watch.lastFireMillis() < cooldownMs) continue; // 冷却中
                EvaluationOutcome outcome = evaluatePolling(watch, player);
                switch (outcome) {
                    case MET -> {
                        triggerPolling(player, watch);
                        watch.updateFireTime(now);
                        if (watch.singleShot()) toRemove.add(watch);
                    }
                    case NOT_MET -> watch.resetFailures(); // 正常评估，重置失败计数
                    case FAILED -> {
                        int failures = watch.incrementFailures();
                        if (failures >= WatchConstants.MAX_CONSECUTIVE_FAILURES) {
                            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("监听连续失败{}次已自动删除（玩家{}，监听{}）", failures, player.getName(), watch.watchId()));
                            toFailRemove.add(watch);
                        }
                    }
                }
            }

            if (!toRemove.isEmpty()) {
                synchronized (list) {
                    list.removeAll(toRemove);
                }
                globalCount.addAndGet(-toRemove.size());
            }
            if (!toFailRemove.isEmpty()) {
                synchronized (list) {
                    list.removeAll(toFailRemove);
                }
                globalCount.addAndGet(-toFailRemove.size());
                for (Watch w : toFailRemove) {
                    notifyWatchFailure(player, w);
                }
            }
            if (!toRemove.isEmpty() || !toFailRemove.isEmpty()) {
                cleanupIfEmpty(playerId);
            }
        } finally {
            lock.set(false);
        }
    }

    /**
     * 执行 skill action 取值并判定条件。
     *
     * <p><b>probe 主体恒等于 owner 是有意设计</b>：player 参数既是订阅者（通知收件人）又是
     * probe 执行主体。SkillSecurityFilter 在 player==null 时会跳过消毒（直接放行 entities），
     * 打开数据隔离缺口；且 BukkitAPIExecutor 的 World/Server target 也依赖 player.getWorld()
     * 获取查询锚点。故 probe 必须以 owner 作为 player，保证消毒基准与数据所有者一致。
     * 监听"查世界状态"类需求（如盯 boss 刷新）时，查的是 owner 所在世界的状态，entities 传
     * 非玩家名参数（如 entity_type）不触发消毒，需求可正常满足。</p>
     */
    private EvaluationOutcome evaluatePolling(Watch watch, Player player) {
        try {
            Skill skill = plugin.getSkillManager().getSkill(watch.skillName());
            if (skill == null) {
                // skill 被卸载（reload/插件卸载）
                PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("监听评估失败：skill 不存在（玩家 {}，监听 {}，skill={}）", player.getName(), watch.watchId(), watch.skillName()));
                return EvaluationOutcome.FAILED;
            }

            Map<String, String> entities = new LinkedHashMap<>(watch.params());
            // 安全消毒（不可跳过，§6.4）
            Map<String, String> sanitized = SkillSecurityFilter.sanitize(watch.skillName(), watch.actionName(), new SkillContext(player, watch.actionName(), entities));
            if (sanitized == null) {
                PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("监听评估失败：安全消毒拒绝（玩家 {}，监听 {}，{}.{}）", player.getName(), watch.watchId(), watch.skillName(), watch.actionName()));
                return EvaluationOutcome.FAILED;
            }

            SkillContext ctx = new SkillContext(player, watch.actionName(), sanitized);
            SkillResult result;
            try {
                result = skill.execute(ctx).get(WatchConstants.SKILL_EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("监听 skill 执行超时: {}.{}", watch.skillName(), watch.actionName()));
                return EvaluationOutcome.FAILED;
            } catch (Exception e) {
                PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("监听 skill 执行异常: {}", e.getMessage()));
                return EvaluationOutcome.FAILED;
            }
            if (result == null || !result.isSuccess()) {
                PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("监听评估失败：skill 返回未成功（玩家 {}，监听 {}，{}.{}）", player.getName(), watch.watchId(), watch.skillName(), watch.actionName()));
                return EvaluationOutcome.FAILED;
            }

            Map<String, Object> data = result.getDataMap();
            if (data == null || !data.containsKey(watch.resultPath())) {
                PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("监听评估失败：结果路径缺失（玩家 {}，监听 {}，路径={}）", player.getName(), watch.watchId(), watch.resultPath()));
                return EvaluationOutcome.FAILED;
            }

            ProbeValue value = ProbeValue.from(data.get(watch.resultPath()));
            boolean met = ConditionEvaluator.test(value, watch.operator(), watch.threshold());
            return met ? EvaluationOutcome.MET : EvaluationOutcome.NOT_MET;
        } catch (Exception e) {
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("条件评估异常: {}", e.getMessage()));
            return EvaluationOutcome.FAILED;
        }
    }

    /**
     * 轮询评估三态
     */
    private enum EvaluationOutcome {MET, NOT_MET, FAILED}

    /**
     * 轮询型触发：条件满足。
     */
    void triggerPolling(Player player, Watch watch) {
        if (shutdown) return;
        PluginLoggerUtil.info(LOG_MODULE, I18nService.tr("条件监听触发: {}（玩家 {}）", watch.displayName(), player.getName()));
        notifyAi(player, I18nService.tr("你盯的{}条件满足了", watch.displayName()), buildEventDesc(I18nService.tr("你盯的{}条件满足了", watch.displayName()), watch.intent()));
        saveEvent(player, watch, I18nService.tr("监听 {} 条件满足", watch.displayName()));
    }

    /**
     * 事件型触发（由 PlayerWatchListener 调用）。owner 取自 watch.owner()。
     *
     * @param watch       命中的 watch
     * @param eventType   事件类型
     * @param filterValue 事件提供的 filter 值（如产物类型、实体类型），可为 null
     */
    void triggerEvent(Watch watch, String eventType, String filterValue) {
        if (shutdown) return;
        UUID ownerId = watch.owner();
        Player player = Bukkit.getPlayer(ownerId);
        if (player == null || !player.isOnline()) return; // 离线不通知
        long now = System.currentTimeMillis();
        long cooldownMs = configManager.getTriggerCooldownSeconds() * 1000L;
        // CAS 冷却：并发事件下仅一个能通过，避免重复触发
        long last = watch.lastFireMillis();
        if (now - last < cooldownMs) return;
        if (!watch.casFireTime(last, now)) return;
        PluginLoggerUtil.info(LOG_MODULE, I18nService.tr("事件监听触发: {}（玩家 {}）", watch.displayName(), player.getName()));

        String eventDesc = WatchEventTypes.describeEvent(eventType, filterValue);
        notifyAi(player, I18nService.tr("你盯的事件{}发生了", watch.displayName()), buildEventDesc(eventDesc, watch.intent()));
        saveEvent(player, watch, eventDesc);

        // single_shot 事件型 watch 触发后删除（同步移除索引）
        if (watch.singleShot()) {
            List<Watch> list = watches.get(ownerId);
            if (list != null) {
                synchronized (list) {
                    list.remove(watch);
                }
                removeFromIndex(watch);
                globalCount.decrementAndGet();
                cleanupIfEmpty(ownerId);
            }
        }
    }

    /**
     * 组装事件描述：基础描述 + 非空时追加监听创建时填写的 intent（后续意图）。
     * 与 PlayerWatchService.notifySubscriber 拼 note 的写法对齐——让二次分析 LLM 能看到玩家在创建监听时表达的后续意图。
     */
    private static String buildEventDesc(String baseDesc, String intent) {
        return (intent != null && !intent.isBlank()) ? baseDesc + I18nService.tr("（备注：{}）", intent) : baseDesc;
    }

    /**
     * 通知 AI（经 LLMOutputCoordinator 二次分析输出，技能结果场景——监听命中即玩家延后请求的结果交付）。
     */
    private void notifyAi(Player player, String userMessage, String eventDescription) {
        try {
            LLMOutputCoordinator coordinator = plugin.getLlmOutputCoordinator();
            if (coordinator == null) return;
            SkillContext ctx = new SkillContext(player, "notify", Map.of());
            AnalysisSummary summary = new AnalysisSummary().userMessage(userMessage).injectEventTrigger(eventDescription);
            coordinator.outputAnalysisResult(player, summary, ctx, new ArrayDeque<>(), OutputScenarioEnum.SKILL_RESULT, false, CacheCallTypeEnum.SECONDARY_ANALYSIS);
        } catch (Exception e) {
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("通知玩家失败: {}", e.getMessage()));
        }
    }

    /**
     * 写事件存档（供问候系统离线回顾）。
     */
    private void saveEvent(Player player, Watch watch, String eventDesc) {
        try {
            EventCollector collector = plugin.getEventCollector();
            if (collector == null) return;
            String data = eventDesc;
            if (watch.intent() != null && !watch.intent().isBlank()) {
                data += I18nService.tr("（意图：{}）", watch.intent());
            }
            collector.submitEvent(ServerEvent.of(ServerEventTypeEnum.PLAYER_WATCH_TRIGGERED, player.getUniqueId(), data));
        } catch (Exception e) {
            PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("写监听存档失败: {}", e.getMessage()));
        }
    }

    /**
     * 连续失败超限时通知玩家。
     */
    private void notifyWatchFailure(Player player, Watch watch) {
        notifyAi(player, I18nService.tr("监听可能配置有误"), I18nService.tr("监听{}连续{}次评估失败，已自动取消", watch.displayName(), WatchConstants.MAX_CONSECUTIVE_FAILURES));
    }

    /**
     * 自身事件/击杀归属查询：返回订阅该 eventType 且 owner == targetUuid 的 watch 列表。
     * 索引短路（eventType 无订阅者 → 返回空），命中后遍历判定 UUID 相等。
     */
    List<Watch> findEventWatches(String eventType, UUID targetUuid) {
        Set<WatchRef> subs = eventSubscribers.get(eventType);
        if (subs == null || subs.isEmpty()) return List.of();
        List<Watch> matched = null;
        for (WatchRef ref : subs) {
            if (ref.ownerId().equals(targetUuid)) {
                if (matched == null) matched = new ArrayList<>();
                matched.add(ref.watch());
            }
        }
        return matched != null ? matched : List.of();
    }

    /**
     * 坐标距离事件查询：返回事件位置半径内的 watch 列表。
     * 距离判定基于 watch 创建时的快照位置（WatchRef.center），不读玩家实时位置，消除 Folia 跨区域读。
     */
    List<Watch> findEventWatches(String eventType, Location eventLoc, double radius) {
        if (eventLoc == null || eventLoc.getWorld() == null) return List.of();
        Set<WatchRef> subs = eventSubscribers.get(eventType);
        if (subs == null || subs.isEmpty()) return List.of();
        String eventWorld = eventLoc.getWorld().getName();
        double radiusSq = radius * radius;
        List<Watch> matched = null;
        for (WatchRef ref : subs) {
            Location center = ref.center();
            if (center == null || center.getWorld() == null) continue;
            // 世界名粗筛（Folia 安全字符串比较）→ 距离判定（纯数学，基于快照位置）
            if (!eventWorld.equals(center.getWorld().getName())) continue;
            double dx = eventLoc.getX() - center.getX();
            double dy = eventLoc.getY() - center.getY();
            double dz = eventLoc.getZ() - center.getZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                if (matched == null) matched = new ArrayList<>();
                matched.add(ref.watch());
            }
        }
        return matched != null ? matched : List.of();
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        FoliaCompat.ScheduledTask timer = offlineTimers.remove(id);
        if (timer != null) timer.cancel();
        if (watches.containsKey(id) && !watches.get(id).isEmpty()) {
            // 恢复轮询定时器（事件型索引未清，单例 Listener 常驻，无需恢复）
            ensurePollTask(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // 暂停轮询定时器
        FoliaCompat.ScheduledTask task = pollTasks.remove(id);
        if (task != null) task.cancel();
        // 清理防重入锁
        pollLocks.remove(id);
        // 单例 Listener 常驻，事件索引在延迟窗口超时统一清理（防永久离线泄漏）
        // 如果没有 watch，不需要延迟删除
        if (!watches.containsKey(id) || watches.get(id).isEmpty()) return;
        // 启动延迟删除定时器
        long graceTicks = configManager.getOfflineGraceMinutes() * 60L * 20L;
        FoliaCompat.ScheduledTask timer = FoliaCompat.runTaskLater(plugin, () -> {
            List<Watch> removed = watches.remove(id);
            if (removed != null) {
                synchronized (removed) {
                    for (Watch w : removed) removeFromIndex(w);
                }
                globalCount.addAndGet(-removed.size());
            }
            offlineTimers.remove(id);
            pollTasks.remove(id);
            pollLocks.remove(id);
        }, graceTicks);
        offlineTimers.put(id, timer);
    }

    /**
     * 玩家 watch 列表为空时清理相关资源（定时器/Map 条目）。事件索引由各移除点的 removeFromIndex 同步维护。
     */
    private void cleanupIfEmpty(UUID playerId) {
        List<Watch> list = watches.get(playerId);
        if (list != null && list.isEmpty()) {
            watches.remove(playerId);
            FoliaCompat.ScheduledTask task = pollTasks.remove(playerId);
            if (task != null) task.cancel();
            pollLocks.remove(playerId);
        }
    }

    /**
     * 生成唯一的 watchId。
     */
    private static String generateWatchId(String prefix1, String prefix2) {
        return "watch_" + prefix1 + "_" + prefix2 + "_" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0xFFFF));
    }

    /**
     * reload 后按新 interval 重建所有在线玩家的轮询定时器。
     */
    public void onConfigReload() {
        for (UUID id : new ArrayList<>(pollTasks.keySet())) {
            FoliaCompat.ScheduledTask old = pollTasks.remove(id);
            if (old != null) old.cancel();
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && watches.containsKey(id) && !watches.get(id).isEmpty()) {
                if (countByMode(watches.get(id), WatchMode.POLLING) > 0) {
                    ensurePollTask(id);
                }
            }
        }
    }

    /**
     * 关闭：取消所有定时器 + 注销单例 Listener + 清空状态。
     */
    public void shutdown() {
        shutdown = true;
        for (var task : pollTasks.values()) task.cancel();
        pollTasks.clear();
        for (var task : offlineTimers.values()) task.cancel();
        offlineTimers.clear();
        if (globalListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(globalListener);
            globalListener = null;
        }
        eventSubscribers.clear();
        pollLocks.clear();
        watches.clear();
        globalCount.set(0);
    }


    /**
     * 一个监听单元（统一支持 POLLING / EVENT 双模式）。
     */
    public static final class Watch {
        private final String watchId;
        private final String displayName;
        private final WatchMode mode;

        // POLLING 字段
        private final String skillName;
        private final String actionName;
        private final String resultPath;
        private final Map<String, String> params;

        // 共有字段
        private final String operator;
        private final String threshold;
        private final String intent;
        private final boolean singleShot;
        private final UUID owner;

        // EVENT 字段
        private final String eventType;
        private final Map<String, String> filterParams;

        private final AtomicLong lastFireMillis = new AtomicLong(0);
        // 仅 pollPlayer 的 per-player CAS 锁串行访问（同一玩家的 watch 仅被一个轮询线程修改）；
        // 事件型 watch 不走此计数器。改 pollPlayer 或新增事件型失败计数路径前需重新评估并发。
        private int consecutiveFailures;

        Watch(String watchId, String displayName, WatchMode mode, String skillName, String actionName, String resultPath, Map<String, String> params, String operator, String threshold, String intent, boolean singleShot, UUID owner, String eventType, Map<String, String> filterParams) {
            this.watchId = watchId;
            this.displayName = displayName;
            this.mode = mode;
            this.skillName = skillName;
            this.actionName = actionName;
            this.resultPath = resultPath;
            this.params = params;
            this.operator = operator;
            this.threshold = threshold;
            this.intent = intent;
            this.singleShot = singleShot;
            this.owner = owner;
            this.eventType = eventType;
            this.filterParams = filterParams;
            this.consecutiveFailures = 0;
        }

        void updateFireTime(long millis) {
            this.lastFireMillis.set(millis);
        }

        boolean casFireTime(long expect, long update) {
            return this.lastFireMillis.compareAndSet(expect, update);
        }

        int incrementFailures() {
            return ++consecutiveFailures;
        }

        void resetFailures() {
            consecutiveFailures = 0;
        }

        public String watchId() {
            return watchId;
        }

        public String displayName() {
            return displayName;
        }

        public WatchMode mode() {
            return mode;
        }

        public String skillName() {
            return skillName;
        }

        public String actionName() {
            return actionName;
        }

        public String resultPath() {
            return resultPath;
        }

        public Map<String, String> params() {
            return params;
        }

        public String operator() {
            return operator;
        }

        public String threshold() {
            return threshold;
        }

        public String intent() {
            return intent;
        }

        public boolean singleShot() {
            return singleShot;
        }

        public UUID owner() {
            return owner;
        }

        public String eventType() {
            return eventType;
        }

        public Map<String, String> filterParams() {
            return filterParams;
        }

        public long lastFireMillis() {
            return lastFireMillis.get();
        }
    }

    /**
     * createWatch 的返回结果。
     */
    public record CreateResult(boolean success, String watchId, String message) {
    }
}
