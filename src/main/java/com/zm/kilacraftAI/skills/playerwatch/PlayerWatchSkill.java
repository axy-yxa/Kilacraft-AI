package com.zm.kilacraftAI.skills.playerwatch;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.playerwatch.PlayerWatchService;
import com.zm.kilacraftAI.skills.framework.*;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 跨玩家上下线订阅技能：玩家通过自然语言订阅其他玩家的上线/下线通知。
 *
 * @author Zm_Mmm
 * @since 2026-07-21
 */
public class PlayerWatchSkill implements Skill {

    private static final String SKILL_NAME = "player_watch";
    private static final String LOG_PREFIX = "跨玩家监控";

    private final SkillConfigManager configManager;

    public PlayerWatchSkill() {
        this.configManager = SkillConfigManager.getInstance();
        if (configManager != null && configManager.getSkillConfig(this) == null) {
            configManager.saveDefaultSkillConfig(this);
            configManager.loadSingleSkillConfig(this);
        }
    }

    private SkillConfig getConfig() {
        return configManager != null ? configManager.getSkillConfig(this) : null;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        SkillConfig config = getConfig();
        return (config != null && !config.getDescription().isEmpty()) ? config.getDescription() : null;
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        return (config != null && config.getActionDescriptions() != null) ? new LinkedHashMap<>(config.getActionDescriptions()) : Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        return (config != null && config.getHints() != null && !config.getHints().isEmpty()) ? new ArrayList<>(config.getHints()) : Collections.emptyList();
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.PLAYER_WATCH.getNode();
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        if (context.getPlayer() == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("此功能仅限玩家使用")));
        }
        if (!PluginPermissionEnum.PLAYER_WATCH.hasPermission(context.getPlayer())) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.PLAYER_WATCH.getNode())));
        }
        PlayerWatchService service = KilacraftAI.getInstance().getPlayerWatchService();
        if (service == null) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("跨玩家监控未启用")));
        }
        String action = context.getAction() == null ? "" : context.getAction();
        return switch (action) {
            case "subscribe" -> CompletableFuture.completedFuture(handleSubscribe(context, service));
            case "unsubscribe" -> CompletableFuture.completedFuture(handleUnsubscribe(context, service));
            case "list" -> CompletableFuture.completedFuture(handleList(context, service));
            case "unsubscribe_all" -> CompletableFuture.completedFuture(handleUnsubscribeAll(context, service));
            default -> CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未知动作: {}", action)));
        };
    }

    private SkillResult handleSubscribe(SkillContext context, PlayerWatchService service) {
        String target = SkillEntityHelper.getString(context, "target_player");
        if (target == null) {
            return SkillResult.needInfo(I18nService.tr("要盯哪个玩家？请告诉我玩家名。"));
        }
        String triggerEvent = SkillEntityHelper.getString(context, "trigger_event");
        String note = SkillEntityHelper.getString(context, "note");
        PluginLoggerUtil.debug(LOG_PREFIX, I18nService.tr("订阅请求：玩家={}, 目标={}, 触发事件={}", context.getPlayer().getName(), target, triggerEvent));
        PlayerWatchService.SubscribeResult result = service.subscribe(context.getPlayer().getUniqueId(), target, triggerEvent, note);

        PluginLoggerUtil.debug(LOG_PREFIX, I18nService.tr("订阅完成：玩家={}, 当前订阅数={}", context.getPlayer().getName(), result.count()));
        String normalizedTrigger = (triggerEvent == null) ? "BOTH" : triggerEvent.trim().toUpperCase();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("target_player", target.trim());
        data.put("trigger_event", normalizedTrigger);
        data.put("subscription_count", result.count());
        return SkillResult.success(result.message(), data);
    }

    private SkillResult handleUnsubscribe(SkillContext context, PlayerWatchService service) {
        String target = SkillEntityHelper.getString(context, "target_player");
        String triggerEvent = SkillEntityHelper.getString(context, "trigger_event");
        if (target == null && triggerEvent == null) {
            return SkillResult.needInfo(I18nService.tr("要取消哪个订阅？请告诉我玩家名（或说\"全部取消\"）。"));
        }
        int removed = service.unsubscribe(context.getPlayer().getUniqueId(), target, triggerEvent);
        PluginLoggerUtil.debug(LOG_PREFIX, I18nService.tr("取消订阅：玩家={}, 目标={}, 已移除={}", context.getPlayer().getName(), target, removed));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("target_player", target);
        data.put("removed_count", removed);
        if (removed == 0) {
            return SkillResult.success(I18nService.tr("没有找到匹配的订阅"), data);
        }
        return SkillResult.success(I18nService.tr("已取消 {} 个订阅", removed), data);
    }

    private SkillResult handleList(SkillContext context, PlayerWatchService service) {
        List<PlayerWatchService.Subscription> subs = service.query(context.getPlayer().getUniqueId());
        PluginLoggerUtil.debug(LOG_PREFIX, I18nService.tr("查询订阅：玩家={}, 共 {} 条", context.getPlayer().getName(), subs.size()));
        List<Map<String, Object>> subList = new ArrayList<>();
        for (PlayerWatchService.Subscription s : subs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("target_player", s.targetName());
            item.put("trigger_event", s.triggerEvent());
            // 实时查询目标当前在线状态——订阅存在不等于目标在线，避免二次分析 LLM 误读为已上线
            boolean online = Bukkit.getPlayerExact(s.targetName()) != null;
            item.put("online", online);
            item.put("note", s.note());
            item.put("created_at", s.createdAt());
            subList.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subscriptions", subList);
        data.put("total", subs.size());
        if (subs.isEmpty()) {
            return SkillResult.success(I18nService.tr("你当前没有订阅"), data);
        }
        StringBuilder msg = new StringBuilder(I18nService.tr("你的订阅（共 {} 个）：", subs.size()));
        for (PlayerWatchService.Subscription s : subs) {
            boolean online = Bukkit.getPlayerExact(s.targetName()) != null;
            msg.append("\n").append(s.targetName()).append(" [").append(s.triggerEvent()).append("]").append(online ? I18nService.tr(" [在线]") : I18nService.tr(" [离线]"));
            if (s.note() != null && !s.note().isBlank()) {
                msg.append(" - ").append(s.note());
            }
        }
        return SkillResult.success(msg.toString(), data);
    }

    private SkillResult handleUnsubscribeAll(SkillContext context, PlayerWatchService service) {
        int removed = service.unsubscribeAll(context.getPlayer().getUniqueId());
        PluginLoggerUtil.debug(LOG_PREFIX, I18nService.tr("取消全部订阅：玩家={}, 已移除={}", context.getPlayer().getName(), removed));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("removed_count", removed);
        if (removed == 0) {
            return SkillResult.success(I18nService.tr("你没有订阅可取消"), data);
        }
        return SkillResult.success(I18nService.tr("已取消全部 {} 个订阅", removed), data);
    }
}
