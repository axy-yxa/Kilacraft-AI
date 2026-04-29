package com.zm.kilacraftAI.skills.afktask.impl;

import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.skills.afktask.AFKTaskStatus;
import com.zm.kilacraftAI.skills.afktask.AFKTaskType;
import com.zm.kilacraftAI.skills.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 实体爆炸挂机任务
 * <p>
 * 监听创建者附近实体爆炸事件（如苦力怕、TNT等），当半径内发生爆炸时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-24
 */
public class EntityExplodeWatchTask extends AbstractEventWatchTask {

    private static final double DEFAULT_RADIUS = 64.0;

    private final Location centerLocation;
    private final double radius;

    public EntityExplodeWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.ENTITY_EXPLODE_WATCH, description, params);

        Player creator = Bukkit.getPlayer(playerUUID);
        if (creator != null && creator.isOnline()) {
            this.centerLocation = creator.getLocation().clone();
        } else {
            this.centerLocation = null;
        }

        this.radius = getParamDouble("radius", DEFAULT_RADIUS);
    }

    @Override
    public void start() {
        if (getStatus() != AFKTaskStatus.PENDING) {
            return;
        }
        if (centerLocation == null) {
            failStart(I18nService.tr("无法获取任务创建者位置"));
            return;
        }
        registerListener();
        markRunning();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        // 某些爆炸源（如下界床爆炸）可能没有关联实体
        if (event.getEntity() == null) {
            return;
        }

        if (centerLocation.getWorld() != event.getEntity().getWorld()) {
            return;
        }
        if (centerLocation.distance(event.getEntity().getLocation()) > radius) {
            return;
        }

        if (!tryAcquireExecution()) {
            return;
        }

        // event.getEntity() 非null已确认
        String entityName = event.getEntity().getName();
        int blockCount = event.blockList().size();

        if (hasCallback()) {
            String eventDesc = I18nService.tr("实体 {} 在附近爆炸，影响了 {} 个方块", entityName, blockCount);
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, entityName, blockCount));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("实体 {} 在附近爆炸，影响了 {} 个方块", entityName, blockCount));
            complete(I18nService.tr("实体 {} 在附近爆炸，影响了 {} 个方块", entityName, blockCount));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String entityName, int blockCount) {
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{entity_name}", entityName).replace("{block_count}", String.valueOf(blockCount)).replace("{creator}", getPlayerName());
            });
        });
    }

    @Override
    public String getTaskDescription() {
        String desc = I18nService.tr("监视附近爆炸事件，半径：{}格", (int) radius);
        if (hasCallback()) {
            int stepCount = getCallback().getCallbackTask().getSteps().size();
            String goal = getCallback().getCallbackTask().getGoal();
            if (goal != null && !goal.isEmpty()) {
                desc = I18nService.tr("监视附近爆炸事件，半径：{}格，触发回调任务（{}步），目标：{}", (int) radius, stepCount, goal);
            } else {
                desc = I18nService.tr("监视附近爆炸事件，半径：{}格，触发回调任务（{}步）", (int) radius, stepCount);
            }
        }
        return desc;
    }
}
