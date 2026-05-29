package com.zm.kilacraftAI.service.afktask.impl;

import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.enums.AFKTaskStatusEnum;
import com.zm.kilacraftAI.common.enums.AFKTaskTypeEnum;
import com.zm.kilacraftAI.service.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.FurnaceSmeltEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 熔炉烧炼挂机任务
 * <p>
 * 监听创建者附近熔炉烧炼完成事件，当半径内有熔炉完成烧炼时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-24
 */
public class FurnaceSmeltWatchTask extends AbstractEventWatchTask {

    private static final double DEFAULT_RADIUS = 16.0;

    private final Location centerLocation;
    private final double radius;
    private final String resultType;

    public FurnaceSmeltWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskTypeEnum.FURNACE_SMELT_WATCH, description, params);

        Player creator = Bukkit.getPlayer(playerUUID);
        if (creator != null && creator.isOnline()) {
            this.centerLocation = creator.getLocation().clone();
        } else {
            this.centerLocation = null;
        }

        this.radius = getParamDouble("radius", DEFAULT_RADIUS);
        this.resultType = getParam("result_type", "");
    }

    @Override
    public void start() {
        if (getStatus() != AFKTaskStatusEnum.PENDING) {
            return;
        }
        if (centerLocation == null) {
            failStart(I18nService.tr("无法获取任务创建者位置"));
            return;
        }
        registerListener();
        markRunning();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (getStatus() != AFKTaskStatusEnum.RUNNING) {
            return;
        }

        String smeltedType = event.getResult().getType().name();
        if (resultType != null && !resultType.isEmpty() && !smeltedType.equalsIgnoreCase(resultType)) {
            return;
        }

        Location furnaceLoc = event.getBlock().getLocation();
        if (centerLocation.getWorld() != furnaceLoc.getWorld()) {
            return;
        }
        if (centerLocation.distance(furnaceLoc) > radius) {
            return;
        }

        // 仅当烧炼源物品为最后一个时触发（source.amount <= 1 表示这是最后一次烧炼）
        if (event.getSource().getAmount() > 1) {
            return;
        }

        if (!tryAcquireExecution()) {
            return;
        }

        int resultAmount = event.getResult().getAmount();

        if (hasCallback()) {
            String eventDesc = I18nService.tr("附近的熔炉已完成烧炼：{} x{}", smeltedType, resultAmount);
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, smeltedType, resultAmount));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("附近的熔炉已完成烧炼：{} x{}", smeltedType, resultAmount));
            complete(I18nService.tr("附近的熔炉已完成烧炼：{} x{}", smeltedType, resultAmount));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String resultItem, int resultAmount) {
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{result_item}", resultItem).replace("{result_amount}", String.valueOf(resultAmount)).replace("{creator}", getPlayerName());
            });
        });
    }

    @Override
    public String getTaskDescription() {
        String rtDesc = (resultType != null && !resultType.isEmpty()) ? I18nService.tr("，产物类型：{}", resultType) : "";
        String desc = I18nService.tr("监视附近熔炉烧炼{}，半径：{}格", rtDesc, (int) radius);
        if (hasCallback()) {
            int stepCount = getCallback().getCallbackTask().getSteps().size();
            String goal = getCallback().getCallbackTask().getGoal();
            if (goal != null && !goal.isEmpty()) {
                desc = I18nService.tr("监视附近熔炉烧炼{}，半径：{}格，触发回调任务（{}步），目标：{}", rtDesc, (int) radius, stepCount, goal);
            } else {
                desc = I18nService.tr("监视附近熔炉烧炼{}，半径：{}格，触发回调任务（{}步）", rtDesc, (int) radius, stepCount);
            }
        }
        return desc;
    }
}
