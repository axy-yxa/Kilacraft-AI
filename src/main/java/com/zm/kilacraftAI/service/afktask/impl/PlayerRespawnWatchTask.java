package com.zm.kilacraftAI.service.afktask.impl;

import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.enums.AFKTaskStatusEnum;
import com.zm.kilacraftAI.common.enums.AFKTaskTypeEnum;
import com.zm.kilacraftAI.service.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skill.task.TaskPlan;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家重生挂机任务
 * <p>
 * 监听指定玩家重生事件，当目标玩家重生时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-10
 */
public class PlayerRespawnWatchTask extends AbstractEventWatchTask {

    private final String targetPlayerName;

    public PlayerRespawnWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskTypeEnum.PLAYER_RESPAWN_WATCH, description, params);
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
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (getStatus() != AFKTaskStatusEnum.RUNNING) {
            return;
        }

        String respawnedPlayerName = event.getPlayer().getName();
        if (!respawnedPlayerName.equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        if (!tryAcquireExecution()) {
            return;
        }

        Location respawnLocation = event.getRespawnLocation();

        if (hasCallback()) {
            String eventDesc = I18nService.tr("{} 已重生（坐标：{}, {}, {}，世界：{}）", describeTarget(respawnedPlayerName), respawnLocation.getBlockX(), respawnLocation.getBlockY(), respawnLocation.getBlockZ(), respawnLocation.getWorld().getName());
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, respawnedPlayerName, respawnLocation));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("{} 已重生（坐标：{}, {}, {}，世界：{}）", describeTarget(respawnedPlayerName), respawnLocation.getBlockX(), respawnLocation.getBlockY(), respawnLocation.getBlockZ(), respawnLocation.getWorld().getName()));
            complete(I18nService.tr("{} 已重生", describeTarget(respawnedPlayerName)));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String triggeredPlayerName, Location respawnLocation) {
        String worldName = respawnLocation.getWorld().getName();
        String x = String.valueOf(respawnLocation.getBlockX());
        String y = String.valueOf(respawnLocation.getBlockY());
        String z = String.valueOf(respawnLocation.getBlockZ());

        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{triggered_player}", triggeredPlayerName).replace("{creator}", getPlayerName()).replace("{x}", x).replace("{y}", y).replace("{z}", z).replace("{world}", worldName);
            });
        });
    }

    @Override
    public String getTaskDescription() {
        String desc = I18nService.tr("监视玩家 {} 重生", targetPlayerName);
        if (hasCallback()) {
            int stepCount = getCallback().getCallbackTask().getSteps().size();
            String goal = getCallback().getCallbackTask().getGoal();
            if (goal != null && !goal.isEmpty()) {
                desc = I18nService.tr("监视玩家 {} 重生，触发回调任务（{}步），目标：{}", targetPlayerName, stepCount, goal);
            } else {
                desc = I18nService.tr("监视玩家 {} 重生，触发回调任务（{}步）", targetPlayerName, stepCount);
            }
        }
        return desc;
    }
}
