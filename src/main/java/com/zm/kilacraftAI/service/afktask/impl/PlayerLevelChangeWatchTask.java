package com.zm.kilacraftAI.service.afktask.impl;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.enums.AFKTaskStatusEnum;
import com.zm.kilacraftAI.common.enums.AFKTaskTypeEnum;
import com.zm.kilacraftAI.service.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerLevelChangeEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家等级变化挂机任务
 * <p>
 * 监听指定玩家等级变化事件，当目标玩家升级或降级时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-10
 */
public class PlayerLevelChangeWatchTask extends AbstractEventWatchTask {

    private final String targetPlayerName;

    public PlayerLevelChangeWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskTypeEnum.PLAYER_LEVEL_CHANGE_WATCH, description, params);
        this.targetPlayerName = getParam("target_player", "");
    }

    @Override
    public void start() {
        if (targetPlayerName == null || targetPlayerName.isEmpty()) {
            failStart("缺少目标玩家名称参数");
            return;
        }

        try {
            registerListener();
            markRunning();
            PluginLoggerUtil.debug("挂机任务", "已启动: {}, 目标: {}, 模式: {}", getTaskId(), targetPlayerName, hasCallback() ? I18nService.tr("回调({}步)", getCallback().getCallbackTask().getSteps().size()) : I18nService.tr("纯通知"));
        } catch (Exception e) {
            failStart(I18nService.tr("监听器注册失败: {}", e.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        if (getStatus() != AFKTaskStatusEnum.RUNNING) {
            return;
        }

        String changedPlayerName = event.getPlayer().getName();
        if (!changedPlayerName.equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        if (!tryAcquireExecution()) {
            return;
        }

        int oldLevel = event.getOldLevel();
        int newLevel = event.getNewLevel();

        if (hasCallback()) {
            String direction = newLevel > oldLevel ? I18nService.tr("升级") : I18nService.tr("降级");
            String eventDesc = I18nService.tr("{} 等级变化（{} → {}，{}）", describeTarget(changedPlayerName), oldLevel, newLevel, direction);
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, changedPlayerName, oldLevel, newLevel));
        } else {
            String direction = newLevel > oldLevel ? I18nService.tr("升级") : I18nService.tr("降级");
            notifyWithLLMAnalysis(I18nService.tr("{} 等级变化（{} → {}，{}）", describeTarget(changedPlayerName), oldLevel, newLevel, direction));
            complete(I18nService.tr("{} 等级变化（{} → {}）", describeTarget(changedPlayerName), oldLevel, newLevel));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String triggeredPlayerName, int oldLevel, int newLevel) {
        String direction = newLevel > oldLevel ? "升级" : "降级";

        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{triggered_player}", triggeredPlayerName).replace("{creator}", getPlayerName()).replace("{old_level}", String.valueOf(oldLevel)).replace("{new_level}", String.valueOf(newLevel)).replace("{direction}", direction);
            });
        });
    }

    @Override
    public String getTaskDescription() {
        if (hasCallback()) {
            String goal = getCallback().getCallbackTask().getGoal();
            String goalDesc = (goal != null && !goal.isEmpty()) ? I18nService.tr("，目标：{}", goal) : "";
            return I18nService.tr("监视玩家 {} 等级变化，触发回调任务（{}步）{}", targetPlayerName, getCallback().getCallbackTask().getSteps().size(), goalDesc);
        }
        return I18nService.tr("监视玩家 {} 等级变化，变化后通知创建者（纯通知）", targetPlayerName);
    }
}
