package com.zm.kilacraftAI.service.afktask.impl;

import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.enums.AFKTaskStatusEnum;
import com.zm.kilacraftAI.common.enums.AFKTaskTypeEnum;
import com.zm.kilacraftAI.service.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skill.task.TaskPlan;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 实体死亡挂机任务
 * <p>
 * 监听创建者附近实体死亡事件，当半径内有实体死亡时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-24
 */
public class EntityDeathWatchTask extends AbstractEventWatchTask {

    private static final double DEFAULT_RADIUS = 64.0;

    private final Location centerLocation;
    private final double radius;
    private final String entityType;

    public EntityDeathWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskTypeEnum.ENTITY_DEATH_WATCH, description, params);

        Player creator = Bukkit.getPlayer(playerUUID);
        if (creator != null && creator.isOnline()) {
            this.centerLocation = creator.getLocation().clone();
        } else {
            this.centerLocation = null;
        }

        this.radius = getParamDouble("radius", DEFAULT_RADIUS);
        this.entityType = getParam("entity_type", "");
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
    public void onEntityDeath(EntityDeathEvent event) {
        if (getStatus() != AFKTaskStatusEnum.RUNNING) {
            return;
        }

        String diedType = event.getEntityType().name();
        if (entityType != null && !entityType.isEmpty() && !diedType.equalsIgnoreCase(entityType)) {
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

        String entityName = event.getEntity().getName();

        if (hasCallback()) {
            String eventDesc = I18nService.tr("实体 {} 在附近死亡", entityName);
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, entityName, diedType));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("实体 {} 在附近死亡", entityName));
            complete(I18nService.tr("实体 {} 在附近死亡", entityName));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String entityName, String entityTypeStr) {
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{entity_name}", entityName).replace("{entity_type}", entityTypeStr).replace("{creator}", getPlayerName());
            });
        });
    }

    @Override
    public String getTaskDescription() {
        String etDesc = (entityType != null && !entityType.isEmpty()) ? I18nService.tr("，实体类型：{}", entityType) : I18nService.tr("（所有实体）");
        String desc = I18nService.tr("监视附近实体死亡{}，半径：{}格", etDesc, (int) radius);
        if (hasCallback()) {
            int stepCount = getCallback().getCallbackTask().getSteps().size();
            String goal = getCallback().getCallbackTask().getGoal();
            if (goal != null && !goal.isEmpty()) {
                desc = I18nService.tr("监视附近实体死亡{}，半径：{}格，触发回调任务（{}步），目标：{}", etDesc, (int) radius, stepCount, goal);
            } else {
                desc = I18nService.tr("监视附近实体死亡{}，半径：{}格，触发回调任务（{}步）", etDesc, (int) radius, stepCount);
            }
        }
        return desc;
    }
}
