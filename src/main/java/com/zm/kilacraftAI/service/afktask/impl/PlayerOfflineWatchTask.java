package com.zm.kilacraftAI.service.afktask.impl;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.enums.AFKTaskStatusEnum;
import com.zm.kilacraftAI.common.enums.AFKTaskTypeEnum;
import com.zm.kilacraftAI.service.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skill.task.TaskPlan;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家下线挂机任务
 * <p>
 * 监听指定玩家离开服务器的事件，当目标玩家下线时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-09
 */
public class PlayerOfflineWatchTask extends AbstractEventWatchTask {

    private final String targetPlayerName;

    public PlayerOfflineWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskTypeEnum.PLAYER_OFFLINE_WATCH, description, params);
        this.targetPlayerName = getParam("target_player", "");
    }

    @Override
    public void start() {
        if (targetPlayerName == null || targetPlayerName.isEmpty()) {
            failStart("缺少目标玩家名称参数");
            return;
        }

        boolean hasCb = hasCallback();

        try {
            registerListener();
            markRunning();
            PluginLoggerUtil.debug("挂机任务", "已启动: {}, 目标: {}, 模式: {}", getTaskId(), targetPlayerName, hasCb ? I18nService.tr("回调({}步)", getCallback().getCallbackTask().getSteps().size()) : I18nService.tr("纯通知"));
        } catch (Exception e) {
            failStart(I18nService.tr("监听器注册失败: {}", e.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (getStatus() != AFKTaskStatusEnum.RUNNING) {
            return;
        }

        String quitPlayerName = event.getPlayer().getName();
        if (!quitPlayerName.equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        if (!tryAcquireExecution()) {
            return;
        }

        if (hasCallback()) {
            String eventDesc = I18nService.tr("{} 已下线", describeTarget(quitPlayerName));
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, quitPlayerName));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("{} 已下线", describeTarget(quitPlayerName)));
            complete(I18nService.tr("{} 已下线", describeTarget(quitPlayerName)));
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
            return I18nService.tr("监视玩家 {} 下线，触发回调任务（{}步）{}", targetPlayerName, getCallback().getCallbackTask().getSteps().size(), goalDesc);
        }
        return I18nService.tr("监视玩家 {} 下线，下线后通知创建者（纯通知）", targetPlayerName);
    }
}
