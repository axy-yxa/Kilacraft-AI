package com.zm.kilacraftAI.service.afktask.impl;

import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.enums.AFKTaskStatusEnum;
import com.zm.kilacraftAI.common.enums.AFKTaskTypeEnum;
import com.zm.kilacraftAI.service.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家钓鱼挂机任务
 * <p>
 * 监听指定玩家钓鱼事件，当目标玩家钓到鱼或实体时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-24
 */
public class PlayerFishWatchTask extends AbstractEventWatchTask {

    private final String targetPlayerName;

    public PlayerFishWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskTypeEnum.PLAYER_FISH_WATCH, description, params);
        this.targetPlayerName = getParam("target_player", "");
    }

    @Override
    public void start() {
        if (getStatus() != AFKTaskStatusEnum.PENDING) {
            return;
        }
        registerListener();
        markRunning();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerFish(PlayerFishEvent event) {
        if (getStatus() != AFKTaskStatusEnum.RUNNING) {
            return;
        }

        if (!event.getPlayer().getName().equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH && event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }

        if (!tryAcquireExecution()) {
            return;
        }

        String caughtDesc = event.getCaught() != null ? event.getCaught().getName() : I18nService.tr("未知");

        if (hasCallback()) {
            String eventDesc = I18nService.tr("{} 钓到了：{}", describeTarget(targetPlayerName), caughtDesc);
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, caughtDesc));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("{} 钓到了：{}", describeTarget(targetPlayerName), caughtDesc));
            complete(I18nService.tr("{} 钓到了：{}", describeTarget(targetPlayerName), caughtDesc));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String caughtItem) {
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{caught_item}", caughtItem).replace("{creator}", getPlayerName());
            });
        });
    }

    @Override
    public String getTaskDescription() {
        String desc = I18nService.tr("监视玩家 {} 钓鱼", targetPlayerName);
        if (hasCallback()) {
            int stepCount = getCallback().getCallbackTask().getSteps().size();
            String goal = getCallback().getCallbackTask().getGoal();
            if (goal != null && !goal.isEmpty()) {
                desc = I18nService.tr("监视玩家 {} 钓鱼，触发回调任务（{}步），目标：{}", targetPlayerName, stepCount, goal);
            } else {
                desc = I18nService.tr("监视玩家 {} 钓鱼，触发回调任务（{}步）", targetPlayerName, stepCount);
            }
        }
        return desc;
    }
}
