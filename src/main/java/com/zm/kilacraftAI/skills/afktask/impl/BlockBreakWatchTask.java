package com.zm.kilacraftAI.skills.afktask.impl;

import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.skills.afktask.AFKTaskStatus;
import com.zm.kilacraftAI.skills.afktask.AFKTaskType;
import com.zm.kilacraftAI.skills.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 方块破坏挂机任务
 * <p>
 * 监听指定玩家破坏方块事件，当目标玩家破坏指定类型的方块时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-24
 */
public class BlockBreakWatchTask extends AbstractEventWatchTask {

    private final String targetPlayerName;
    private final String blockType;

    public BlockBreakWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.BLOCK_BREAK_WATCH, description, params);
        this.targetPlayerName = getParam("target_player", "");
        this.blockType = getParam("block_type", "");
    }

    @Override
    public void start() {
        if (getStatus() != AFKTaskStatus.PENDING) {
            return;
        }
        registerListener();
        markRunning();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        if (!event.getPlayer().getName().equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        String brokenType = event.getBlock().getType().name();
        if (blockType != null && !blockType.isEmpty() && !brokenType.equalsIgnoreCase(blockType)) {
            return;
        }

        if (!tryAcquireExecution()) {
            return;
        }

        if (hasCallback()) {
            String eventDesc = I18nService.tr("{} 破坏了方块 {}", describeTarget(targetPlayerName), brokenType);
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, brokenType));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("{} 破坏了方块 {}", describeTarget(targetPlayerName), brokenType));
            complete(I18nService.tr("{} 破坏了方块 {}", describeTarget(targetPlayerName), brokenType));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String brokenType) {
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{block_type}", brokenType).replace("{creator}", getPlayerName()).replace("{triggered_player}", targetPlayerName);
            });
        });
    }

    @Override
    public String getTaskDescription() {
        String btDesc = (blockType != null && !blockType.isEmpty()) ? I18nService.tr("，方块类型：{}", blockType) : "";
        String desc = I18nService.tr("监视玩家 {} 破坏方块{}", targetPlayerName, btDesc);
        if (hasCallback()) {
            int stepCount = getCallback().getCallbackTask().getSteps().size();
            String goal = getCallback().getCallbackTask().getGoal();
            if (goal != null && !goal.isEmpty()) {
                desc = I18nService.tr("监视玩家 {} 破坏方块{}，触发回调任务（{}步），目标：{}", targetPlayerName, btDesc, stepCount, goal);
            } else {
                desc = I18nService.tr("监视玩家 {} 破坏方块{}，触发回调任务（{}步）", targetPlayerName, btDesc, stepCount);
            }
        }
        return desc;
    }
}
