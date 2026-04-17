package com.zm.kilacraftAI.skills.afktask.impl;

import com.google.gson.Gson;
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
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 玩家世界切换挂机任务
 *
 * <p>监听指定玩家切换世界事件，当目标玩家切换世界时触发多步骤回调任务。</p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li>与 PlayerOnlineWatchTask 完全对称</li>
 *   <li>可获取来源世界和目标世界</li>
 *   <li>支持所有世界类型（原版世界和多世界插件创建的自定义世界）</li>
 *   <li>本任务只负责监视世界切换事件，回调逻辑与上线监视一致</li>
 * </ul>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>"帮我盯着 Steve，他去了下界告诉我"</li>
 *   <li>"监视玩家A，他切换世界后查询他的新位置"</li>
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
public class PlayerChangedWorldWatchTask extends AFKTask implements Listener {

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
     * 构造玩家世界切换挂机任务
     *
     * @param taskId      任务唯一ID
     * @param playerUUID  玩家UUID（谁创建的此任务）
     * @param playerName  玩家名称
     * @param description 任务描述
     * @param params      任务参数
     *                    必需：target_player
     *                    可选：callback（JSON格式）
     */
    public PlayerChangedWorldWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.PLAYER_CHANGED_WORLD_WATCH, description, params);
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
            PluginLogger.warn("挂机任务", "解析回调配置失败: " + e.getMessage(), e);
            return new AFKTaskCallback();
        }
    }

    @Override
    public void start() {
        if (targetPlayerName == null || targetPlayerName.isEmpty()) {
            failStart("缺少目标玩家名称参数");
            return;
        }

        // 回调配置可选：如果为空或无步骤，则为纯通知模式
        boolean hasCallback = callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty();

        // 注册事件监听器
        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
            markRunning();

            // 启动通知由上游 AIRequestHandler 通过 LLM 二次分析发送，此处不再重复通知玩家

            PluginLogger.debug("挂机任务", "已启动: " + getTaskId() + ", 目标: " + targetPlayerName + ", 模式: " + (hasCallback ? "回调(" + callback.getCallbackTask().getSteps().size() + "步)" : "纯通知"));
        } catch (Exception e) {
            failStart("监听器注册失败: " + e.getMessage());
        }
    }

    @Override
    protected void onStop() {
        // 注销事件监听器
        if (listenerRegistered) {
            try {
                HandlerList.unregisterAll(this);
                listenerRegistered = false;

                PluginLogger.debug("挂机任务", "已停止: " + getTaskId());
            } catch (Exception e) {
                PluginLogger.warn("挂机任务", "注销事件监听器失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 监听玩家世界切换事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        // 检查是否是目标玩家
        String changedPlayerName = event.getPlayer().getName();
        if (!changedPlayerName.equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        // 原子操作：只有第一个线程能执行回调，防止并发冲突
        if (!callbackExecuted.compareAndSet(false, true)) {
            return; // 已经被其他线程执行
        }

        // 目标玩家切换世界
        World fromWorld = event.getFrom();
        World toWorld = event.getPlayer().getWorld();
        boolean hasCallback = callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty();

        if (hasCallback) {
            // 先完成任务：立即注销事件监听器，防止异步回调期间新事件触发重复回调
            complete("目标玩家 " + changedPlayerName + " 切换世界（" + fromWorld.getName() + " → " + toWorld.getName() + "），开始执行回调。");
            executeCallback(changedPlayerName, fromWorld, toWorld);
        } else {
            // 纯通知模式：通过 LLM 二次分析通知
            notifyWithLLMAnalysis("目标玩家 " + changedPlayerName + " 切换世界（" + fromWorld.getName() + " → " + toWorld.getName() + "）");
            complete("目标玩家 " + changedPlayerName + " 切换世界（" + fromWorld.getName() + " → " + toWorld.getName() + "），挂机任务完成。");
        }
    }

    /**
     * 执行回调任务
     *
     * @param triggeredPlayerName 触发事件的玩家名称
     * @param fromWorld           来源世界
     * @param toWorld             目标世界
     */
    private void executeCallback(String triggeredPlayerName, World fromWorld, World toWorld) {
        try {
            // 1. 构建 TaskPlan
            TaskPlan plan = callback.getCallbackTask().toTaskPlan();
            replacePlaceholdersInTaskPlan(plan, triggeredPlayerName, fromWorld, toWorld);

            // 2. 获取任务创建者玩家对象
            Player creatorPlayer = Bukkit.getPlayer(getPlayerUUID());
            if (creatorPlayer == null || !creatorPlayer.isOnline()) {
                PluginLogger.warn("挂机任务", "任务创建者不在线，无法执行回调: " + getTaskId());
                notifyPlayer("§c任务创建者不在线，回调任务已取消。");
                return;
            }

            // 3. 构建执行上下文
            SkillContext context = new SkillContext(creatorPlayer, callback.getCallbackTask().getGoal(), Map.of());

            // 4. 延迟反馈优化：不传入对话历史
            Deque<ConversationManager.Message> history = new java.util.ArrayDeque<>();

            // 5. 执行多步骤任务（TaskExecutor 返回 AnalysisSummary）
            TaskExecutor executor = new TaskExecutor(plugin.getSkillManager());

            CompletableFuture<AnalysisSummary> future = executor.executeTask(plan, context, history, callback.getCallbackTask().getGoal());

            // 6. 处理执行结果：通过中间层进行LLM二次分析并输出
            future.thenAccept(summary -> {
                plugin.getLlmOutputCoordinator().outputAnalysisResult(
                        creatorPlayer, summary, context, history,
                        OutputScenario.AFK_CALLBACK,
                        false
                );
            }).exceptionally(ex -> {
                PluginLogger.error("挂机任务", "回调任务执行异常: " + ex.getMessage(), ex);
                Player errorPlayer = Bukkit.getPlayer(getPlayerUUID());
                if (errorPlayer != null && errorPlayer.isOnline()) {
                    plugin.getLlmOutputCoordinator().outputError(errorPlayer, "§c回调任务执行失败：" + ex.getMessage());
                }
                return null;
            });

        } catch (Exception e) {
            notifyPlayer("§c回调任务启动失败：" + e.getMessage());
            PluginLogger.error("挂机任务", "回调任务启动异常: " + e.getMessage(), e);
        }
    }

    /**
     * 替换 TaskPlan 中的占位符
     */
    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String triggeredPlayerName, World fromWorld, World toWorld) {
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value
                        .replace("{triggered_player}", triggeredPlayerName)
                        .replace("{creator}", getPlayerName())
                        .replace("{from_world}", fromWorld.getName())
                        .replace("{to_world}", toWorld.getName());
            });
        });
    }

    @Override
    public String getTaskDescription() {
        if (callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty()) {
            String goal = callback.getCallbackTask().getGoal();
            String goalDesc = (goal != null && !goal.isEmpty()) ? "，目标：" + goal : "";
            return "监视玩家 " + targetPlayerName + " 切换世界，触发回调任务（" + callback.getCallbackTask().getSteps().size() + "步）" + goalDesc;
        }
        return "监视玩家 " + targetPlayerName + " 切换世界，切换后通知创建者（纯通知）";
    }
}
