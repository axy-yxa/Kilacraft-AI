package com.zm.kilacraftAI.service.afktask.impl;

import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.enums.AFKTaskStatusEnum;
import com.zm.kilacraftAI.common.enums.AFKTaskTypeEnum;
import com.zm.kilacraftAI.service.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skill.task.TaskPlan;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerBedLeaveEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家离开床挂机任务
 * <p>
 * 监听指定玩家离开床事件，当目标玩家离开床时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-10
 */
public class PlayerBedLeaveWatchTask extends AbstractEventWatchTask {

    private final String targetPlayerName;

    public PlayerBedLeaveWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskTypeEnum.PLAYER_BED_LEAVE_WATCH, description, params);
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
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        if (getStatus() != AFKTaskStatusEnum.RUNNING) {
            return;
        }

        String leavingPlayerName = event.getPlayer().getName();
        if (!leavingPlayerName.equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        if (!tryAcquireExecution()) {
            return;
        }

        Player targetPlayer = event.getPlayer();
        Location bedLocation = event.getBed() != null ? event.getBed().getLocation() : targetPlayer.getLocation();

        if (hasCallback()) {
            String eventDesc = I18nService.tr("{} 已离开床（坐标：{}, {}, {}，世界：{}）", describeTarget(leavingPlayerName), bedLocation.getBlockX(), bedLocation.getBlockY(), bedLocation.getBlockZ(), bedLocation.getWorld().getName());
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, leavingPlayerName, bedLocation));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("{} 已离开床", describeTarget(leavingPlayerName)));
            complete(I18nService.tr("{} 已离开床", describeTarget(leavingPlayerName)));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String triggeredPlayerName, Location bedLocation) {
        String worldName = bedLocation.getWorld().getName();
        String x = String.valueOf(bedLocation.getBlockX());
        String y = String.valueOf(bedLocation.getBlockY());
        String z = String.valueOf(bedLocation.getBlockZ());

        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{triggered_player}", triggeredPlayerName).replace("{creator}", getPlayerName()).replace("{x}", x).replace("{y}", y).replace("{z}", z).replace("{world}", worldName);
            });
        });
    }

    @Override
    public String getTaskDescription() {
        String desc = I18nService.tr("监视玩家 {} 离开床", targetPlayerName);
        if (hasCallback()) {
            int stepCount = getCallback().getCallbackTask().getSteps().size();
            String goal = getCallback().getCallbackTask().getGoal();
            if (goal != null && !goal.isEmpty()) {
                desc = I18nService.tr("监视玩家 {} 离开床，触发回调任务（{}步），目标：{}", targetPlayerName, stepCount, goal);
            } else {
                desc = I18nService.tr("监视玩家 {} 离开床，触发回调任务（{}步）", targetPlayerName, stepCount);
            }
        }
        return desc;
    }
}
