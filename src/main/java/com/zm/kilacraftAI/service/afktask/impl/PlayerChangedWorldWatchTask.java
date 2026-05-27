package com.zm.kilacraftAI.service.afktask.impl;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.enums.AFKTaskStatusEnum;
import com.zm.kilacraftAI.common.enums.AFKTaskTypeEnum;
import com.zm.kilacraftAI.service.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skill.task.TaskPlan;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家世界切换挂机任务
 * <p>
 * 监听指定玩家切换世界事件，当目标玩家切换世界时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-10
 */
public class PlayerChangedWorldWatchTask extends AbstractEventWatchTask {

    private final String targetPlayerName;

    public PlayerChangedWorldWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskTypeEnum.PLAYER_CHANGED_WORLD_WATCH, description, params);
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
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
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

        World fromWorld = event.getFrom();
        World toWorld = event.getPlayer().getWorld();

        if (hasCallback()) {
            String eventDesc = I18nService.tr("{} 切换世界（{} → {}）", describeTarget(changedPlayerName), fromWorld.getName(), toWorld.getName());
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, changedPlayerName, fromWorld, toWorld));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("{} 切换世界（{} → {}）", describeTarget(changedPlayerName), fromWorld.getName(), toWorld.getName()));
            complete(I18nService.tr("{} 切换世界（{} → {}）", describeTarget(changedPlayerName), fromWorld.getName(), toWorld.getName()));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String triggeredPlayerName, World fromWorld, World toWorld) {
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{triggered_player}", triggeredPlayerName).replace("{creator}", getPlayerName()).replace("{from_world}", fromWorld.getName()).replace("{to_world}", toWorld.getName());
            });
        });
    }

    @Override
    public String getTaskDescription() {
        if (hasCallback()) {
            String goal = getCallback().getCallbackTask().getGoal();
            String goalDesc = (goal != null && !goal.isEmpty()) ? I18nService.tr("，目标：{}", goal) : "";
            return I18nService.tr("监视玩家 {} 切换世界，触发回调任务（{}步）{}", targetPlayerName, getCallback().getCallbackTask().getSteps().size(), goalDesc);
        }
        return I18nService.tr("监视玩家 {} 切换世界，切换后通知创建者（纯通知）", targetPlayerName);
    }
}
