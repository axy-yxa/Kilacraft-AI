package com.zm.kilacraftAI.service.afktask.impl;

import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.enums.AFKTaskStatusEnum;
import com.zm.kilacraftAI.common.enums.AFKTaskTypeEnum;
import com.zm.kilacraftAI.service.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家聊天挂机任务
 * <p>
 * 监听指定玩家聊天事件，当目标玩家发送包含指定关键词的消息时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-24
 */
public class PlayerChatWatchTask extends AbstractEventWatchTask {

    private final String targetPlayerName;
    private final String keyword;

    public PlayerChatWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskTypeEnum.PLAYER_CHAT_WATCH, description, params);
        this.targetPlayerName = getParam("target_player", "");
        this.keyword = getParam("keyword", "");
    }

    @Override
    public void start() {
        if (getStatus() != AFKTaskStatusEnum.PENDING) {
            return;
        }
        registerListener();
        markRunning();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (getStatus() != AFKTaskStatusEnum.RUNNING) {
            return;
        }

        if (!event.getPlayer().getName().equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        String message = event.getMessage();
        if (keyword != null && !keyword.isEmpty() && !message.toLowerCase().contains(keyword.toLowerCase())) {
            return;
        }

        if (!tryAcquireExecution()) {
            return;
        }

        if (hasCallback()) {
            String eventDesc = I18nService.tr("{} 发送了聊天消息：{}", describeTarget(targetPlayerName), message);
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, message));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("{} 发送了聊天消息：{}", describeTarget(targetPlayerName), message));
            complete(I18nService.tr("{} 发送了聊天消息", describeTarget(targetPlayerName)));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String chatMessage) {
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{chat_message}", chatMessage).replace("{creator}", getPlayerName()).replace("{triggered_player}", targetPlayerName);
            });
        });
    }

    @Override
    public String getTaskDescription() {
        String kwDesc = (keyword != null && !keyword.isEmpty()) ? I18nService.tr("，关键词：{}", keyword) : "";
        String desc = I18nService.tr("监视玩家 {} 聊天{}", targetPlayerName, kwDesc);
        if (hasCallback()) {
            int stepCount = getCallback().getCallbackTask().getSteps().size();
            String goal = getCallback().getCallbackTask().getGoal();
            if (goal != null && !goal.isEmpty()) {
                desc = I18nService.tr("监视玩家 {} 聊天{}，触发回调任务（{}步），目标：{}", targetPlayerName, kwDesc, stepCount, goal);
            } else {
                desc = I18nService.tr("监视玩家 {} 聊天{}，触发回调任务（{}步）", targetPlayerName, kwDesc, stepCount);
            }
        }
        return desc;
    }
}
