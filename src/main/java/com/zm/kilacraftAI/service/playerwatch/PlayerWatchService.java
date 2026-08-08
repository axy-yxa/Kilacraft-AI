package com.zm.kilacraftAI.service.playerwatch;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.CacheCallTypeEnum;
import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.task.AnalysisSummary;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨玩家上下线订阅服务：玩家可订阅其他玩家的上线/下线通知。
 *
 * @author Zm_Mmm
 * @since 2026-07-21
 */
public final class PlayerWatchService implements Listener {

    private static final String LOG_MODULE = "跨玩家监控";
    /**
     * 玩家上线后延迟通知的 tick 数（2 秒），等玩家完全进服再通知订阅者。
     */
    private static final long JOIN_NOTIFY_DELAY_TICKS = 40L;
    /**
     * 单玩家订阅数量上限，防滥用。
     */
    private static final int MAX_SUBSCRIPTIONS_PER_PLAYER = 5;

    private final KilacraftAI plugin;
    /**
     * 订阅者 UUID → 订阅列表。订阅者下线时整个 key 被 remove。
     */
    private final Map<UUID, List<Subscription>> subscriptions = new ConcurrentHashMap<>();
    /**
     * 目标玩家名 → 待执行的 JOIN 延迟通知任务。QUIT 到达时取消，避免上线通知晚于下线通知（乱序）。
     */
    private final Map<String, FoliaCompat.ScheduledTask> pendingJoinTasks = new ConcurrentHashMap<>();

    public PlayerWatchService(KilacraftAI plugin) {
        this.plugin = plugin;
    }

    /**
     * 关闭：取消所有待执行的 JOIN 延迟任务 + 清空订阅状态，由 KilacraftAI.onDisable 调用（Listener 注销由调用方处理）。
     */
    public void shutdown() {
        for (FoliaCompat.ScheduledTask task : pendingJoinTasks.values()) {
            task.cancel();
        }
        pendingJoinTasks.clear();
        subscriptions.clear();
    }

    /**
     * 订阅指定玩家的上线/下线通知。
     *
     * @return 结果消息模板（含占位符 {} {}，依次为 target、trigger）与订阅总数
     */
    public SubscribeResult subscribe(UUID subscriber, String targetName, String triggerEvent, String note) {
        String normalizedTarget = targetName.trim();
        String normalizedTrigger = normalizeTrigger(triggerEvent);
        List<Subscription> list = subscriptions.computeIfAbsent(subscriber, k -> Collections.synchronizedList(new ArrayList<>()));

        synchronized (list) {
            // 同 target+trigger 已存在 → 视为刷新（更新 note），不新增
            for (int i = 0; i < list.size(); i++) {
                Subscription s = list.get(i);
                if (s.targetName().equalsIgnoreCase(normalizedTarget) && s.triggerEvent().equals(normalizedTrigger)) {
                    list.set(i, new Subscription(subscriber, normalizedTarget, normalizedTrigger, note, System.currentTimeMillis()));
                    return new SubscribeResult(I18nService.tr("已更新对 {} 的 {} 订阅", normalizedTarget, triggerDescription(normalizedTrigger)), list.size());
                }
            }
            if (list.size() >= MAX_SUBSCRIPTIONS_PER_PLAYER) {
                return new SubscribeResult(I18nService.tr("订阅数已达上限（{} 个），请先取消一些", list.size()), list.size());
            }
            list.add(new Subscription(subscriber, normalizedTarget, normalizedTrigger, note, System.currentTimeMillis()));
        }

        String message = I18nService.tr("已订阅 {} 的 {} 通知", normalizedTarget, triggerDescription(normalizedTrigger));
        return new SubscribeResult(message, list.size());
    }

    /**
     * 取消订阅。target/triggerEvent 任一为空按通配处理。
     *
     * @return 被移除的订阅数
     */
    public int unsubscribe(UUID subscriber, String targetName, String triggerEvent) {
        List<Subscription> list = subscriptions.get(subscriber);
        if (list == null) {
            return 0;
        }
        String normalizedTarget = targetName == null ? null : targetName.trim();
        String normalizedTrigger = triggerEvent == null || triggerEvent.isBlank() ? null : normalizeTrigger(triggerEvent);

        synchronized (list) {
            int before = list.size();
            list.removeIf(s -> (normalizedTarget == null || s.targetName().equalsIgnoreCase(normalizedTarget)) && (normalizedTrigger == null || s.triggerEvent().equals(normalizedTrigger)));
            int removed = before - list.size();
            if (list.isEmpty()) {
                subscriptions.remove(subscriber);
            }
            return removed;
        }
    }

    /**
     * 取消订阅者的所有订阅。返回被移除的数量。
     */
    public int unsubscribeAll(UUID subscriber) {
        List<Subscription> removed = subscriptions.remove(subscriber);
        return removed == null ? 0 : removed.size();
    }

    /**
     * 查询订阅者的所有活跃订阅（按创建时间倒序）。
     */
    public List<Subscription> query(UUID subscriber) {
        List<Subscription> list = subscriptions.get(subscriber);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            List<Subscription> copy = new ArrayList<>(list);
            copy.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));
            return copy;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        String name = event.getPlayer().getName();
        // 延迟通知，等被订阅玩家完全进服（位置/状态稳定）；记录任务便于 QUIT 时取消防乱序
        FoliaCompat.ScheduledTask task = FoliaCompat.runTaskLater(plugin, () -> {
            pendingJoinTasks.remove(name);
            dispatch(name, "JOIN");
        }, JOIN_NOTIFY_DELAY_TICKS);
        pendingJoinTasks.put(name, task);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        String name = event.getPlayer().getName();
        // 取消尚未发出的 JOIN 通知——避免「上线通知」晚于「下线通知」的乱序
        FoliaCompat.ScheduledTask pending = pendingJoinTasks.remove(name);
        if (pending != null) {
            pending.cancel();
        }
        dispatch(name, "QUIT");
        // 订阅者下线即清空其订阅——不持久化，重启也不恢复
        subscriptions.remove(event.getPlayer().getUniqueId());
    }

    /**
     * 分发上下线事件到所有匹配订阅者。在 IO 线程执行 LLM 通知。
     *
     * @param targetName    触发事件的玩家名
     * @param occurredEvent "JOIN" 或 "QUIT"
     */
    private void dispatch(String targetName, String occurredEvent) {
        if (subscriptions.isEmpty()) {
            return;
        }
        FoliaCompat.getIOPool().execute(() -> {
            for (List<Subscription> list : subscriptions.values()) {
                List<Subscription> snapshot;
                synchronized (list) {
                    snapshot = new ArrayList<>(list);
                }
                for (Subscription s : snapshot) {
                    if (!matchesEvent(s.triggerEvent(), occurredEvent)) {
                        continue;
                    }
                    if (!s.targetName().equalsIgnoreCase(targetName)) {
                        continue;
                    }
                    Player subscriber = Bukkit.getPlayer(s.subscriberUuid);
                    if (subscriber == null || !subscriber.isOnline()) {
                        PluginLoggerUtil.debug(LOG_MODULE, I18nService.tr("订阅者{}离线，跳过通知", s.subscriberUuid));
                        continue; // 离线不通知（不持久化）
                    }
                    notifySubscriber(subscriber, targetName, occurredEvent, s);
                }
            }
        });
    }

    private void notifySubscriber(Player subscriber, String targetName, String occurredEvent, Subscription s) {
        try {
            if (plugin.getLlmOutputCoordinator() == null) {
                return;
            }
            SkillContext ctx = new SkillContext(subscriber, "notify", java.util.Map.of());
            String eventDesc = I18nService.tr("{} 已{}{}", targetName, "JOIN".equals(occurredEvent) ? I18nService.tr("上线") : I18nService.tr("下线"), (s.note() != null && !s.note.isBlank()) ? I18nService.tr("（备注：{}）", s.note()) : "");
            AnalysisSummary summary = new AnalysisSummary().userMessage(I18nService.tr("你订阅的玩家状态有变化")).injectEventTrigger(eventDesc);
            plugin.getLlmOutputCoordinator().outputAnalysisResult(subscriber, summary, ctx, new ArrayDeque<>(), OutputScenarioEnum.SKILL_RESULT, false, CacheCallTypeEnum.SECONDARY_ANALYSIS).thenAccept(result -> {
                // 触发后把订阅备注（后续动作意图）展示为可执行操作点击项
                if (subscriber.isOnline() && plugin.getTriggerActionPresenter() != null) {
                    String triggerDesc = "JOIN".equals(occurredEvent) ? I18nService.tr("你订阅的玩家 {} 已上线", targetName) : I18nService.tr("你订阅的玩家 {} 已下线", targetName);
                    plugin.getTriggerActionPresenter().present(subscriber, triggerDesc, s.note());
                }
            });
        } catch (Exception e) {
            PluginLoggerUtil.warn(LOG_MODULE, I18nService.tr("通知订阅者失败（{} → {}）: {}", subscriber.getName(), targetName, e.getMessage()));
        }
    }

    /**
     * 触发事件归一化：JOIN/QUIT/BOTH，非法值降级 BOTH（信任 LLM 理解）。
     */
    private static String normalizeTrigger(String input) {
        if (input == null || input.isBlank()) {
            return "BOTH";
        }
        String up = input.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (up) {
            case "JOIN", "ONLINE" -> "JOIN";
            case "QUIT", "OFFLINE" -> "QUIT";
            default -> "BOTH";
        };
    }

    private static boolean matchesEvent(String subscribed, String occurred) {
        if ("BOTH".equals(subscribed)) {
            return true;
        }
        return subscribed.equals(occurred);
    }

    /**
     * 触发事件的人类可读描述（i18n）。
     */
    private static String triggerDescription(String trigger) {
        return switch (trigger) {
            case "JOIN" -> I18nService.tr("上线");
            case "QUIT" -> I18nService.tr("下线");
            default -> I18nService.tr("上下线");
        };
    }

    /**
     * 单条订阅记录。triggerEvent 取值：JOIN / QUIT / BOTH。
     */
    public record Subscription(UUID subscriberUuid, String targetName, String triggerEvent, String note,
                               long createdAt) {
    }

    /**
     * subscribe 操作的返回结果。
     */
    public record SubscribeResult(String message, int count) {
    }
}
