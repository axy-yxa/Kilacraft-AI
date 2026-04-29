package com.zm.kilacraftAI.skills.afktask.impl;

import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.skills.afktask.AFKTaskStatus;
import com.zm.kilacraftAI.skills.afktask.AFKTaskType;
import com.zm.kilacraftAI.skills.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家传送挂机任务
 * <p>
 * 监听指定玩家传送事件，当目标玩家传送时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-10
 */
public class PlayerTeleportWatchTask extends AbstractEventWatchTask {

    private final String targetPlayerName;

    public PlayerTeleportWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.PLAYER_TELEPORT_WATCH, description, params);
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
            PluginLogger.debug("挂机任务", "已启动: {}, 目标: {}, 模式: {}", getTaskId(), targetPlayerName, hasCallback() ? "回调(" + getCallback().getCallbackTask().getSteps().size() + "步)" : "纯通知");
        } catch (Exception e) {
            failStart(I18nService.tr("监听器注册失败: {}", e.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        String teleportedPlayerName = event.getPlayer().getName();
        if (!teleportedPlayerName.equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        if (!tryAcquireExecution()) {
            return;
        }

        if (hasCallback()) {
            Location from = event.getFrom();
            Location to = event.getTo();
            String eventDesc = I18nService.tr("{} 已传送（从 X={}, Y={}, Z={}, 世界={} 到 X={}, Y={}, Z={}, 世界={}）", describeTarget(teleportedPlayerName), String.format("%.1f", from.getX()), String.format("%.1f", from.getY()), String.format("%.1f", from.getZ()), from.getWorld().getName(), String.format("%.1f", to.getX()), String.format("%.1f", to.getY()), String.format("%.1f", to.getZ()), to.getWorld().getName());
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, teleportedPlayerName, from, to));
        } else {
            Location from = event.getFrom();
            Location to = event.getTo();
            notifyWithLLMAnalysis(I18nService.tr("{} 已传送（从 X={}, Y={}, Z={}, 世界={} 到 X={}, Y={}, Z={}, 世界={}）", describeTarget(teleportedPlayerName), String.format("%.1f", from.getX()), String.format("%.1f", from.getY()), String.format("%.1f", from.getZ()), from.getWorld().getName(), String.format("%.1f", to.getX()), String.format("%.1f", to.getY()), String.format("%.1f", to.getZ()), to.getWorld().getName()));
            complete(I18nService.tr("{} 已传送", describeTarget(teleportedPlayerName)));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String triggeredPlayerName, Location fromLocation, Location toLocation) {
        String fromWorld = fromLocation.getWorld().getName();
        String toWorld = toLocation.getWorld().getName();

        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{triggered_player}", triggeredPlayerName).replace("{creator}", getPlayerName()).replace("{from_world}", fromWorld).replace("{to_world}", toWorld).replace("{from_x}", String.valueOf(fromLocation.getX())).replace("{from_y}", String.valueOf(fromLocation.getY())).replace("{from_z}", String.valueOf(fromLocation.getZ())).replace("{to_x}", String.valueOf(toLocation.getX())).replace("{to_y}", String.valueOf(toLocation.getY())).replace("{to_z}", String.valueOf(toLocation.getZ()));
            });
        });
    }

    @Override
    public String getTaskDescription() {
        if (hasCallback()) {
            String goal = getCallback().getCallbackTask().getGoal();
            String goalDesc = (goal != null && !goal.isEmpty()) ? I18nService.tr("，目标：{}", goal) : "";
            return I18nService.tr("监视玩家 {} 传送，触发回调任务（{}步）{}", targetPlayerName, getCallback().getCallbackTask().getSteps().size(), goalDesc);
        }
        return I18nService.tr("监视玩家 {} 传送，传送后通知创建者（纯通知）", targetPlayerName);
    }
}
