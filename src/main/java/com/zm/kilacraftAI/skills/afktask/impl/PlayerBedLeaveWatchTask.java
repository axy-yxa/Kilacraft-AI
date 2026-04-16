package com.zm.kilacraftAI.skills.afktask.impl;

import com.google.gson.Gson;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.enums.OutputScenario;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.skills.afktask.AFKTask;
import com.zm.kilacraftAI.skills.afktask.AFKTaskCallback;
import com.zm.kilacraftAI.skills.afktask.AFKTaskStatus;
import com.zm.kilacraftAI.skills.afktask.AFKTaskType;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.task.AnalysisSummary;
import com.zm.kilacraftAI.skills.framework.task.TaskExecutor;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 玩家离开床挂机任务
 *
 * <p>监听指定玩家离开床事件，当目标玩家离开床时触发多步骤回调任务。</p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li>与 PlayerOnlineWatchTask 完全对称</li>
 *   <li>可获取玩家离开床的位置坐标</li>
 *   <li>本任务只负责监视离开床事件，回调逻辑与上线监视一致</li>
 * </ul>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>"帮我盯着 Steve，他起床了告诉我"</li>
 *   <li>"监视玩家A，他起床后查询他的位置"</li>
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
public class PlayerBedLeaveWatchTask extends AFKTask implements Listener {

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
     * 回调执行标志（防止并发重复执行）
     */
    private final AtomicBoolean callbackExecuted = new AtomicBoolean(false);

    /**
     * 构造玩家离开床挂机任务
     *
     * @param taskId      任务唯一ID
     * @param playerUUID  玩家UUID（谁创建的此任务）
     * @param playerName  玩家名称
     * @param description 任务描述
     * @param params      任务参数
     *                    必需：target_player
     *                    可选：callback（JSON格式）
     */
    public PlayerBedLeaveWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.PLAYER_BED_LEAVE_WATCH, description, params);
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

        // 注册 PlayerBedLeaveEvent 监听器
        Bukkit.getPluginManager().registerEvents(this, KilacraftAI.getInstance());
        listenerRegistered = true;

        markRunning();
    }

    /**
     * 监听玩家离开床事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        String leavingPlayerName = event.getPlayer().getName();
        if (!leavingPlayerName.equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        // 原子操作：只有第一个线程能执行回调，防止并发冲突
        if (!callbackExecuted.compareAndSet(false, true)) {
            return; // 已经被其他线程执行
        }

        // 检查是否是目标玩家
        Player targetPlayer = event.getPlayer();
        Location bedLocation = event.getBed() != null ? event.getBed().getLocation() : targetPlayer.getLocation();

        // 判断是否有回调步骤
        boolean hasCallback = callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null &&
                !callback.getCallbackTask().getSteps().isEmpty();

        if (hasCallback) {
            // 先完成任务：立即注销事件监听器，防止异步回调期间新事件触发重复回调
            complete("目标玩家 " + leavingPlayerName + " 已离开床，开始执行回调。");
            executeCallback(leavingPlayerName, bedLocation);
        } else {
            // 纯通知模式：通过 LLM 二次分析通知
            notifyWithLLMAnalysis("目标玩家 " + leavingPlayerName + " 已离开床");
            complete("目标玩家 " + leavingPlayerName + " 已离开床，挂机任务完成。");
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

            // 5. 执行多步骤任务（TaskExecutor 返回 AnalysisSummary）
            TaskExecutor executor = new TaskExecutor(KilacraftAI.getInstance().getSkillManager());

            CompletableFuture<AnalysisSummary> future = executor.executeTask(plan, context, history, callback.getCallbackTask().getGoal());

            // 6. 处理执行结果：通过中间层进行LLM二次分析并输出
            future.thenAccept(summary -> {
                plugin.getLlmOutputCoordinator().outputAnalysisResult(
                    creatorPlayer, summary, context, history,
                    OutputScenario.AFK_CALLBACK,
                    false
                );
            }).exceptionally(ex -> {
                KilacraftAI.getInstance().getLogger().severe("[挂机任务] 回调任务执行异常: " + ex.getMessage());
                ex.printStackTrace();
                Player errorPlayer = Bukkit.getPlayer(getPlayerUUID());
                if (errorPlayer != null && errorPlayer.isOnline()) {
                    KilacraftAI.getInstance().getLlmOutputCoordinator().outputError(errorPlayer, "§c回调任务执行失败：" + ex.getMessage());
                }
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

    @Override
    public void onStop() {
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
    }

    @Override
    public String getTaskDescription() {
        String desc = "监视玩家 " + targetPlayerName + " 离开床";
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
