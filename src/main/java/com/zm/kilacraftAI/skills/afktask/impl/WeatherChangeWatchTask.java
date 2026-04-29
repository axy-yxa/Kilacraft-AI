package com.zm.kilacraftAI.skills.afktask.impl;

import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.skills.afktask.AFKTaskStatus;
import com.zm.kilacraftAI.skills.afktask.AFKTaskType;
import com.zm.kilacraftAI.skills.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 天气变化挂机任务
 * <p>
 * 监听世界天气变化事件，当指定世界的天气发生变化时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-10
 */
public class WeatherChangeWatchTask extends AbstractEventWatchTask {

    private final String targetWorldName;

    public WeatherChangeWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.WEATHER_CHANGE_WATCH, description, params);
        this.targetWorldName = getParam("target_world", "");
    }

    @Override
    public void start() {
        try {
            registerListener();
            markRunning();
            String worldDesc = (targetWorldName != null && !targetWorldName.isEmpty()) ? targetWorldName : "玩家当前世界";
            PluginLogger.debug("挂机任务", "已启动: {}, 目标世界: {}, 模式: {}", getTaskId(), worldDesc, hasCallback() ? "回调(" + getCallback().getCallbackTask().getSteps().size() + "步)" : "纯通知");
        } catch (Exception e) {
            failStart(I18nService.tr("监听器注册失败: {}", e.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        World eventWorld = event.getWorld();

        if (targetWorldName != null && !targetWorldName.isEmpty()) {
            if (!eventWorld.getName().equalsIgnoreCase(targetWorldName)) {
                return;
            }
        } else {
            Player player = Bukkit.getPlayer(getPlayerUUID());
            if (player == null || !player.isOnline()) {
                return;
            }
            if (!player.getWorld().getName().equalsIgnoreCase(eventWorld.getName())) {
                return;
            }
        }

        if (!tryAcquireExecution()) {
            return;
        }

        boolean toWeatherState = event.toWeatherState();
        String weatherDesc = toWeatherState ? I18nService.tr("开始下雨/雷暴") : I18nService.tr("天气转晴");

        if (hasCallback()) {
            String eventDesc = I18nService.tr("世界 {} 天气变化（{}）", eventWorld.getName(), weatherDesc);
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, eventWorld.getName(), toWeatherState, weatherDesc));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("世界 {} 天气变化（{}）", eventWorld.getName(), weatherDesc));
            complete(I18nService.tr("世界 {} 天气变化（{}）", eventWorld.getName(), weatherDesc));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String worldName, boolean toWeatherState, String weatherDesc) {
        String weatherType = toWeatherState ? I18nService.tr("雨天/雷暴") : I18nService.tr("晴天");

        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{world_name}", worldName).replace("{creator}", getPlayerName()).replace("{weather_state}", weatherDesc).replace("{weather_type}", weatherType);
            });
        });
    }

    @Override
    public String getTaskDescription() {
        String worldDesc = (targetWorldName != null && !targetWorldName.isEmpty()) ? targetWorldName : I18nService.tr("当前世界");
        if (hasCallback()) {
            String goal = getCallback().getCallbackTask().getGoal();
            String goalDesc = (goal != null && !goal.isEmpty()) ? I18nService.tr("，目标：{}", goal) : "";
            return I18nService.tr("监视世界 {} 天气变化，触发回调任务（{}步）{}", worldDesc, getCallback().getCallbackTask().getSteps().size(), goalDesc);
        }
        return I18nService.tr("监视世界 {} 天气变化，变化后通知创建者（纯通知）", worldDesc);
    }
}
