package com.zm.kilacraftAI.service.afktask.impl;

import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.enums.AFKTaskStatusEnum;
import com.zm.kilacraftAI.common.enums.AFKTaskTypeEnum;
import com.zm.kilacraftAI.service.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skill.task.TaskPlan;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家物品损坏挂机任务
 * <p>
 * 监听指定玩家物品损坏事件，当目标玩家的物品损坏时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-10
 */
public class PlayerItemBreakWatchTask extends AbstractEventWatchTask {

    private final String targetPlayerName;

    public PlayerItemBreakWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskTypeEnum.PLAYER_ITEM_BREAK_WATCH, description, params);
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
    public void onPlayerItemBreak(PlayerItemBreakEvent event) {
        if (getStatus() != AFKTaskStatusEnum.RUNNING) {
            return;
        }

        String brokenItemPlayerName = event.getPlayer().getName();
        if (!brokenItemPlayerName.equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        if (!tryAcquireExecution()) {
            return;
        }

        ItemStack brokenItem = event.getBrokenItem();
        String itemName = getItemName(brokenItem);
        String itemType = brokenItem.getType().name();

        if (hasCallback()) {
            String eventDesc = I18nService.tr("{} 的物品 {} 已损坏", describeTarget(brokenItemPlayerName), itemName);
            complete(eventDesc);
            String finalItemType = itemType;
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, brokenItemPlayerName, itemName, finalItemType));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("{} 的物品 {} 已损坏", describeTarget(brokenItemPlayerName), itemName));
            complete(I18nService.tr("{} 的物品 {} 已损坏", describeTarget(brokenItemPlayerName), itemName));
        }
    }

    private String getItemName(ItemStack item) {
        if (item == null) {
            return "未知物品";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String triggeredPlayerName, String itemName, String itemType) {
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{triggered_player}", triggeredPlayerName).replace("{creator}", getPlayerName()).replace("{item_name}", itemName).replace("{item_type}", itemType);
            });
        });
    }

    @Override
    public String getTaskDescription() {
        String desc = I18nService.tr("监视玩家 {} 的物品损坏", targetPlayerName);
        if (hasCallback()) {
            int stepCount = getCallback().getCallbackTask().getSteps().size();
            String goal = getCallback().getCallbackTask().getGoal();
            if (goal != null && !goal.isEmpty()) {
                desc = I18nService.tr("监视玩家 {} 的物品损坏，触发回调任务（{}步），目标：{}", targetPlayerName, stepCount, goal);
            } else {
                desc = I18nService.tr("监视玩家 {} 的物品损坏，触发回调任务（{}步）", targetPlayerName, stepCount);
            }
        }
        return desc;
    }
}
