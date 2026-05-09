package com.zm.kilacraftAI.skills.afktask.impl;

import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.skills.afktask.AFKTaskStatus;
import com.zm.kilacraftAI.skills.afktask.AFKTaskType;
import com.zm.kilacraftAI.skills.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家上线挂机任务
 * <p>
 * 监听指定玩家加入服务器的事件，当目标玩家上线时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-09
 */
public class PlayerOnlineWatchTask extends AbstractEventWatchTask {

    /**
     * 目标玩家名称（被监视的玩家）
     */
    private final String targetPlayerName;

    /**
     * 构造玩家上线挂机任务
     */
    public PlayerOnlineWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.PLAYER_ONLINE_WATCH, description, params);
        this.targetPlayerName = getParam("target_player", "");
    }

    @Override
    public void start() {
        if (targetPlayerName == null || targetPlayerName.isEmpty()) {
            failStart("缺少目标玩家名称参数");
            return;
        }

        // 回调配置可选：如果为空或无步骤，则为纯通知模式（只通知上线，不执行回调）
        boolean hasCb = hasCallback();

        // 注意：目标玩家在线检查已在上游 AFKTaskSkill.handleCreateTask() 中完成
        // 到达此处时，目标玩家必定不在线，可以直接注册监听器

        // 注册事件监听器
        try {
            registerListener();
            markRunning();

            PluginLogger.debug("挂机任务", "已启动: {}, 目标: {}, 模式: {}", getTaskId(), targetPlayerName, hasCb ? I18nService.tr("回调({}步)", getCallback().getCallbackTask().getSteps().size()) : I18nService.tr("纯通知"));
        } catch (Exception e) {
            failStart(I18nService.tr("监听器注册失败: {}", e.getMessage()));
        }
    }

    /**
     * 监听玩家加入服务器事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        // 检查是否是目标玩家
        String joinedPlayerName = event.getPlayer().getName();
        if (!joinedPlayerName.equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        // 延迟执行：PlayerJoinEvent 触发时玩家尚未完全进入游戏世界，
        // 立即执行回调/通知可能导致操作失败（如传送、发送消息给目标玩家）
        // 40 ticks ≈ 2 秒，是 Minecraft 中玩家完全进入游戏的安全延迟
        FoliaCompat.runTaskLater(plugin, () -> handleTargetPlayerOnline(joinedPlayerName), 40L);
    }

    /**
     * 处理目标玩家上线（延迟执行，确保玩家已完全进入游戏）
     */
    private void handleTargetPlayerOnline(String joinedPlayerName) {
        // 再次检查状态：延迟期间任务可能已被取消
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        if (!tryAcquireExecution()) {
            return;
        }

        if (hasCallback()) {
            String eventDesc = I18nService.tr("{} 已上线", describeTarget(joinedPlayerName));
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, joinedPlayerName));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("{} 已上线", describeTarget(joinedPlayerName)));
            complete(I18nService.tr("{} 已上线", describeTarget(joinedPlayerName)));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String triggeredPlayerName) {
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{triggered_player}", triggeredPlayerName).replace("{creator}", getPlayerName());
            });
        });
    }

    @Override
    public String getTaskDescription() {
        if (hasCallback()) {
            String goal = getCallback().getCallbackTask().getGoal();
            String goalDesc = (goal != null && !goal.isEmpty()) ? I18nService.tr("，目标：{}", goal) : "";
            return I18nService.tr("监视玩家 {} 上线，触发回调任务（{}步）{}", targetPlayerName, getCallback().getCallbackTask().getSteps().size(), goalDesc);
        }
        return I18nService.tr("监视玩家 {} 上线，上线后通知创建者（纯通知）", targetPlayerName);
    }
}
