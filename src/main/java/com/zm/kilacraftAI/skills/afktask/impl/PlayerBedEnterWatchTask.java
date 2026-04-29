package com.zm.kilacraftAI.skills.afktask.impl;

import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.skills.afktask.AFKTaskStatus;
import com.zm.kilacraftAI.skills.afktask.AFKTaskType;
import com.zm.kilacraftAI.skills.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerBedEnterEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家进入床挂机任务
 * <p>
 * 监听指定玩家进入床（睡觉）事件，当目标玩家进入床时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-10
 */
public class PlayerBedEnterWatchTask extends AbstractEventWatchTask {

    private final String targetPlayerName;

    public PlayerBedEnterWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.PLAYER_BED_ENTER_WATCH, description, params);
        this.targetPlayerName = getParam("target_player", "");
    }

    @Override
    public void start() {
        if (getStatus() != AFKTaskStatus.PENDING) {
            return;
        }
        registerListener();
        markRunning();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        String sleepingPlayerName = event.getPlayer().getName();
        if (!sleepingPlayerName.equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        if (!tryAcquireExecution()) {
            return;
        }

        Player targetPlayer = event.getPlayer();
        Location bedLocation = event.getBed() != null ? event.getBed().getLocation() : targetPlayer.getLocation();

        if (hasCallback()) {
            String eventDesc = I18nService.tr("{} 已进入床睡觉（坐标：{}, {}, {}，世界：{}）", describeTarget(sleepingPlayerName), bedLocation.getBlockX(), bedLocation.getBlockY(), bedLocation.getBlockZ(), bedLocation.getWorld().getName());
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, sleepingPlayerName, bedLocation));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("{} 已进入床睡觉（坐标：{}, {}, {}，世界：{}）", describeTarget(sleepingPlayerName), bedLocation.getBlockX(), bedLocation.getBlockY(), bedLocation.getBlockZ(), bedLocation.getWorld().getName()));
            complete(I18nService.tr("{} 已进入床睡觉", describeTarget(sleepingPlayerName)));
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
        String desc = I18nService.tr("监视玩家 {} 进入床", targetPlayerName);
        if (hasCallback()) {
            int stepCount = getCallback().getCallbackTask().getSteps().size();
            String goal = getCallback().getCallbackTask().getGoal();
            if (goal != null && !goal.isEmpty()) {
                desc = I18nService.tr("监视玩家 {} 进入床，触发回调任务（{}步），目标：{}", targetPlayerName, stepCount, goal);
            } else {
                desc = I18nService.tr("监视玩家 {} 进入床，触发回调任务（{}步）", targetPlayerName, stepCount);
            }
        }
        return desc;
    }
}
