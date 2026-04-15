package com.zm.kilacraftAI.skills.afktask.impl;

import com.google.gson.Gson;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.skills.afktask.AFKTask;
import com.zm.kilacraftAI.skills.afktask.AFKTaskCallback;
import com.zm.kilacraftAI.skills.afktask.AFKTaskStatus;
import com.zm.kilacraftAI.skills.afktask.AFKTaskType;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.skills.framework.task.LLMAnalysisService;
import com.zm.kilacraftAI.skills.framework.task.TaskExecutor;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 玩家进入床挂机任务
 *
 * <p>监听指定玩家进入床（睡觉）事件，当目标玩家进入床时触发多步骤回调任务。</p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li>与 PlayerOnlineWatchTask 完全对称</li>
 *   <li>可获取玩家进入床的位置坐标</li>
 *   <li>本任务只负责监视进入床事件，回调逻辑与上线监视一致</li>
 * </ul>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>"帮我盯着 Steve，他睡觉了告诉我"</li>
 *   <li>"监视玩家A，他睡觉后查询他的位置"</li>
 * </ul>
 *
 * <h3>必需参数：</h3>
 * <ul>
 *   <li>target_player: 目标玩家名称（被监视的玩家）</li>
 *   <li>callback: 回调配置（AFKTaskCallback JSON 格式，可选）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-10
 */
public class PlayerBedEnterWatchTask extends AFKTask implements Listener {

    private static final Gson GSON = new Gson();

    /**
     * 目标玩家名称（被监视的玩家）
     */
    private final String targetPlayerName;

    /**
     * 回调配置（多步骤任务定义）
     */
    private final AFKTaskCallback callback;

    /**
     * 是否已注册事件监听器
     */
    private boolean listenerRegistered = false;

    /**
     * 构造玩家进入床挂机任务
     *
     * @param taskId      任务唯一ID
     * @param playerUUID  玩家UUID（谁创建的此任务）
     * @param playerName  玩家名称
     * @param description 任务描述
     * @param params      任务参数
     *                    必需：target_player
     *                    可选：callback（JSON格式）
     */
    public PlayerBedEnterWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.PLAYER_BED_ENTER_WATCH, description, params);
        this.targetPlayerName = getParam("target_player", "");
        this.callback = parseCallback(getParam("callback", ""));
    }

    /**
     * 解析回调配置 JSON
     */
    private AFKTaskCallback parseCallback(String json) {
        if (json == null || json.isEmpty()) {
            return new AFKTaskCallback();
        }
        try {
            return GSON.fromJson(json, AFKTaskCallback.class);
        } catch (Exception e) {
            KilacraftAI.getInstance().getLogger().warning(
                    "[DEBUG] [挂机任务] 解析回调配置失败: " + e.getMessage());
            return new AFKTaskCallback();
        }
    }

    @Override
    public void start() {
        if (getStatus() != AFKTaskStatus.PENDING) {
            return;
        }

        // 注册 PlayerBedEnterEvent 监听器
        Bukkit.getPluginManager().registerEvents(this, KilacraftAI.getInstance());
        listenerRegistered = true;

        markRunning();
    }

    /**
     * 监听玩家进入床事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        String sleepingPlayerName = event.getPlayer().getName();
        if (!sleepingPlayerName.equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        // 检查是否是目标玩家
        Player targetPlayer = event.getPlayer();
        Location bedLocation = event.getBed() != null ? event.getBed().getLocation() : targetPlayer.getLocation();

        // 判断是否有回调步骤
        if (callback.getCallbackTask() == null || callback.getCallbackTask().getSteps() == null ||
                callback.getCallbackTask().getSteps().isEmpty()) {
            // 纯通知模式：直接通知玩家
            String notifyMessage = "🔔 挂机任务完成\n\n玩家 {triggered_player} 已进入床睡觉。\n\n坐标：{x}, {y}, {z}\n世界：{world}";

            // 替换占位符
            notifyMessage = notifyMessage
                    .replace("{triggered_player}", sleepingPlayerName)
                    .replace("{creator}", getPlayerName())
                    .replace("{x}", String.valueOf(bedLocation.getBlockX()))
                    .replace("{y}", String.valueOf(bedLocation.getBlockY()))
                    .replace("{z}", String.valueOf(bedLocation.getBlockZ()))
                    .replace("{world}", bedLocation.getWorld().getName());

            notifyPlayer(notifyMessage);
            complete("目标玩家 " + sleepingPlayerName + " 已进入床睡觉。");
        } else {
            // 回调模式：先完成任务，再执行回调
            complete("目标玩家 " + sleepingPlayerName + " 已进入床睡觉，开始执行回调。");
            executeCallback(sleepingPlayerName, bedLocation);
        }
    }

    /**
     * 执行回调任务（带坐标信息）
     */
    private void executeCallback(String triggeredPlayerName, Location bedLocation) {
        try {
            // 1. 构建 TaskPlan
            TaskPlan plan = callback.getCallbackTask().toTaskPlan();
            replacePlaceholdersInTaskPlan(plan, triggeredPlayerName, bedLocation);

            // 2. 获取任务创建者玩家对象
            Player creatorPlayer = Bukkit.getPlayer(getPlayerUUID());
            if (creatorPlayer == null || !creatorPlayer.isOnline()) {
                KilacraftAI.getInstance().getLogger().warning("[挂机任务] 任务创建者不在线，无法执行回调: " + getTaskId());
                notifyPlayer("§c任务创建者不在线，回调任务已取消。");
                return;
            }

            // 3. 构建执行上下文
            SkillContext context = new SkillContext(creatorPlayer, callback.getCallbackTask().getGoal(), Map.of());

            // 4. 延迟反馈优化：不传入对话历史
            Deque<ConversationManager.Message> history = new java.util.ArrayDeque<>();

            // 5. 执行多步骤任务
            TaskExecutor executor = new TaskExecutor(KilacraftAI.getInstance().getSkillManager(), new LLMAnalysisService());

            CompletableFuture<SkillResult> future = executor.executeTask(plan, context, history, callback.getCallbackTask().getGoal());

            // 注意：任务已在调用方通过 complete() 完成，此处仅做通知
            future.thenAccept(result -> {
                // 通知玩家
                notifyCallbackResult(triggeredPlayerName, result);
            }).exceptionally(ex -> {
                KilacraftAI.getInstance().getLogger().severe("[挂机任务] 回调任务执行异常: " + ex.getMessage());
                ex.printStackTrace();
                notifyPlayer("§c回调任务执行失败：" + ex.getMessage());
                return null;
            });
        } catch (Exception e) {
            KilacraftAI.getInstance().getLogger().severe("[挂机任务] 构建回调任务失败: " + e.getMessage());
            e.printStackTrace();
            notifyPlayer("§c回调任务构建失败：" + e.getMessage());
        }
    }

    /**
     * 替换 TaskPlan 中的占位符
     */
    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String triggeredPlayerName, Location bedLocation) {
        String worldName = bedLocation.getWorld().getName();
        String x = String.valueOf(bedLocation.getBlockX());
        String y = String.valueOf(bedLocation.getBlockY());
        String z = String.valueOf(bedLocation.getBlockZ());

        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value
                        .replace("{triggered_player}", triggeredPlayerName)
                        .replace("{creator}", getPlayerName())
                        .replace("{x}", x)
                        .replace("{y}", y)
                        .replace("{z}", z)
                        .replace("{world}", worldName);
            });
        });
    }

    /**
     * 通知回调结果
     */
    private void notifyCallbackResult(String triggeredPlayerName, SkillResult result) {
        String notificationMessage;
        if (result.isSuccess()) {
            notificationMessage = "🔔 挂机任务提醒\n\n" +
                    MessageUtil.convertMarkdownToMinecraft(result.getMessage());
        } else {
            notificationMessage = "⚠️ 挂机任务提醒\n\n" +
                    "挂机任务触发，但回调执行失败：" + result.getMessage();
        }

        // 判断通知目标
        String notifyTarget = callback.getNotifyTarget();
        if (notifyTarget == null || notifyTarget.isEmpty() || "{creator}".equalsIgnoreCase(notifyTarget)) {
            // 通知创建者
            notifyPlayer(notificationMessage);
        } else {
            // 通知指定玩家
            Player targetPlayer = Bukkit.getPlayerExact(notifyTarget);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(notificationMessage);
            } else {
                notifyPlayer("⚠️ 挂机任务提醒\n\n通知目标玩家 " + notifyTarget + " 不在线。");
            }
        }
    }

    @Override
    public void onStop() {
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
    }

    @Override
    public String getTaskDescription() {
        String desc = "监视玩家 " + targetPlayerName + " 进入床";
        if (callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null &&
                !callback.getCallbackTask().getSteps().isEmpty()) {
            int stepCount = callback.getCallbackTask().getSteps().size();
            String goal = callback.getCallbackTask().getGoal();
            if (goal != null && !goal.isEmpty()) {
                desc += "，触发回调任务（" + stepCount + "步），目标：" + goal;
            } else {
                desc += "，触发回调任务（" + stepCount + "步）";
            }
        }
        return desc;
    }
}
