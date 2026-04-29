package com.zm.kilacraftAI.skills.afktask.impl;

import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.skills.afktask.AFKTaskStatus;
import com.zm.kilacraftAI.skills.afktask.AFKTaskType;
import com.zm.kilacraftAI.skills.afktask.AbstractEventWatchTask;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockGrowEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 作物/方块生长挂机任务
 * <p>
 * 监听创建者附近作物或方块生长事件，当半径内有作物成熟时触发多步骤回调任务
 *
 * @author Zm_Mmm
 * @since 2026-04-24
 */
public class BlockGrowWatchTask extends AbstractEventWatchTask {

    private static final double DEFAULT_RADIUS = 32.0;

    /**
     * 监控中心位置（任务创建时快照）
     */
    private final Location centerLocation;

    /**
     * 监控半径（格）
     */
    private final double radius;

    /**
     * 构造作物/方块生长挂机任务
     */
    public BlockGrowWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.BLOCK_GROW_WATCH, description, params);

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

    /**
     * 监听方块生长事件
     *
     * <p>使用 {@code event.getNewState()} 检查生长后的方块数据（而非 {@code getBlock().getBlockData()}），
     * 因为后者返回的是生长前的旧数据。</p>
     *
     * <p>对于 Ageable 作物（小麦、胡萝卜、马铃薯等），仅在即将成熟时触发
     * （新状态的 age == maxAge）。对于非 Ageable 方块（仙人掌、甘蔗、竹子等），任何生长都触发。</p>
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockGrow(BlockGrowEvent event) {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        // 空间过滤
        Location blockLoc = event.getBlock().getLocation();
        if (centerLocation.getWorld() != blockLoc.getWorld()) {
            return;
        }
        if (centerLocation.distance(blockLoc) > radius) {
            return;
        }

        // 成熟度检查：使用 getNewState() 获取生长后的数据
        BlockState newState = event.getNewState();
        BlockData newData = newState.getBlockData();
        if (newData instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) {
                // 新状态未达最大年龄，非成熟生长，跳过
                PluginLogger.debug("挂机任务", "作物 {} 生长中（新age={}/{}），跳过", newState.getType().name(), ageable.getAge(), ageable.getMaximumAge());
                return;
            }
        }
        // 非 Ageable 方块（仙人掌、甘蔗等）或 Ageable 成熟：触发任务

        if (!tryAcquireExecution()) {
            return;
        }

        String blockType = event.getBlock().getType().name();

        if (hasCallback()) {
            String eventDesc = I18nService.tr("附近的 {} 已成熟/生长完成", blockType);
            complete(eventDesc);
            executeCallback(eventDesc, plan -> replacePlaceholdersInTaskPlan(plan, blockType));
        } else {
            notifyWithLLMAnalysis(I18nService.tr("附近的 {} 已成熟/生长完成", blockType));
            complete(I18nService.tr("附近的 {} 已成熟/生长完成", blockType));
        }
    }

    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String blockType) {
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{block_type}", blockType).replace("{creator}", getPlayerName());
            });
        });
    }

    @Override
    public String getTaskDescription() {
        String desc = I18nService.tr("监视附近作物/方块生长{}，半径：{}格", "", (int) radius);
        if (hasCallback()) {
            int stepCount = getCallback().getCallbackTask().getSteps().size();
            String goal = getCallback().getCallbackTask().getGoal();
            if (goal != null && !goal.isEmpty()) {
                desc = I18nService.tr("监视附近作物/方块生长{}，半径：{}格，触发回调任务（{}步），目标：{}", "", (int) radius, stepCount, goal);
            } else {
                desc = I18nService.tr("监视附近作物/方块生长{}，半径：{}格，触发回调任务（{}步）", "", (int) radius, stepCount);
            }
        }
        return desc;
    }
}
