package com.zm.kilacraftAI.skills.afktask.impl;

import com.google.gson.Gson;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 玩家上线挂机任务
 *
 * <p>监听指定玩家加入服务器的事件，当目标玩家上线时触发多步骤回调任务。</p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li>本任务只负责监视事件，不直接执行业务逻辑</li>
 *   <li>监视完成后，通过回调配置构建完整的 TaskPlan</li>
 *   <li>TaskPlan 交给 TaskExecutor 执行（与用户直接触发的多步骤任务完全相同）</li>
 *   <li>执行结果经过 LLM 二次分析后，生成自然语言通知玩家</li>
 * </ul>
 *
 * <h3>回调架构：</h3>
 * <pre>
 * 挂机任务（异步）                          回调任务（多步骤同步执行）
 * ┌──────────────────┐                   ┌──────────────────────────┐
 * │ 监视 Steve 上线   │ ─触发─→ TaskPlan → │ step_1: MarketQuerySkill │
 * │                  │                   │ step_2: MythicMobsSkill  │
 * │                  │                   │ step_3: ...              │
 * └──────────────────┘                   └──────────┬───────────────┘
 *                                                   │
 *                                            LLM 二次分析
 *                                                   │
 *                                            通知玩家结果
 * </pre>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>"帮我盯着玩家Steve，他上线了就帮我查市场钻石价格"</li>
 *   <li>"监视玩家A上线，先帮我买钻石剑，确认血量后传送我过去"</li>
 * </ul>
 *
 * <h3>必需参数：</h3>
 * <ul>
 *   <li>target_player: 目标玩家名称（被监视的玩家）</li>
 *   <li>callback: 回调配置（AFKTaskCallback JSON 格式）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-09
 */
public class PlayerOnlineWatchTask extends AFKTask implements Listener {

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
     * 构造玩家上线挂机任务
     *
     * @param taskId      任务唯一ID
     * @param playerUUID  玩家UUID（谁创建的此任务）
     * @param playerName  玩家名称
     * @param description 任务描述
     * @param params      任务参数
     *                    必需：target_player, callback（JSON格式）
     */
    public PlayerOnlineWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.PLAYER_ONLINE_WATCH, description, params);
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

        // 回调配置可选：如果为空或无步骤，则为纯通知模式（只通知上线，不执行回调）
        boolean hasCallback = callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty();

        // 注意：目标玩家在线检查已在上游 AFKTaskSkill.handleCreateTask() 中完成
        // 到达此处时，目标玩家必定不在线，可以直接注册监听器

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
     * 监听玩家加入服务器事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        // 检查是否是目标玩家
        String joinedPlayerName = event.getPlayer().getName();
        if (!joinedPlayerName.equalsIgnoreCase(targetPlayerName)) {
            return;
        }

        // 延迟执行：PlayerJoinEvent 触发时玩家尚未完全进入游戏世界，
        // 立即执行回调/通知可能导致操作失败（如传送、发送消息给目标玩家）
        // 40 ticks ≈ 2 秒，是 Minecraft 中玩家完全进入游戏的安全延迟
        FoliaCompat.runTaskLater(plugin, () -> handleTargetPlayerOnline(joinedPlayerName), 40L);
    }

    /**
     * 处理目标玩家上线（延迟执行，确保玩家已完全进入游戏）
     */
    private void handleTargetPlayerOnline(String joinedPlayerName) {
        // 再次检查状态：延迟期间任务可能已被取消
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        // 原子操作：只有第一个线程能执行回调，防止并发冲突
        if (!callbackExecuted.compareAndSet(false, true)) {
            return; // 已经被其他线程执行
        }

        boolean hasCallback = callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty();

        if (hasCallback) {
            // 先完成任务：立即注销事件监听器，防止异步回调期间新事件触发重复回调
            // complete() → onStop() → HandlerList.unregisterAll()，之后不会再有事件进入
            // 异步回调持有 this 引用，不会被 GC 回收，回调完成后仅做通知，不再调用 complete()
            complete("目标玩家 " + joinedPlayerName + " 已上线，开始执行回调。");
            executeCallback(joinedPlayerName);
        } else {
            // 纯通知模式：通过 LLM 二次分析通知
            notifyWithLLMAnalysis("目标玩家 " + joinedPlayerName + " 已上线");
            complete("目标玩家 " + joinedPlayerName + " 已上线，挂机任务完成。");
        }
    }

    /**
     * 执行回调任务
     *
     * <p>核心流程：</p>
     * <ol>
     *   <li>读取回调配置，构建 TaskPlan</li>
     *   <li>TaskExecutor 执行多步骤任务</li>
     *   <li>LLM 二次分析结果</li>
     *   <li>通知玩家</li>
     * </ol>
     *
     * @param triggeredPlayerName 触发事件的玩家名称
     */
    private void executeCallback(String triggeredPlayerName) {
        try {
            // 1. 构建 TaskPlan
            TaskPlan plan = callback.getCallbackTask().toTaskPlan();
            replacePlaceholdersInTaskPlan(plan, triggeredPlayerName);

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
            //
            // 原因：挂机任务是延迟反馈场景，事件触发时可能已经过去几分钟甚至几小时
            // 此时的对话历史充斥着后续的无关对话，原始任务上下文已被淹没
            // 注入这些过期的历史只会产生噪音、浪费 Token、降低分析质量
            //
            // AnalysisSummary 本身已经包含：用户原始输入 + 任务目标 + 各步骤执行结果
            // 这些信息完全自包含，足以让 LLM 生成高质量的分析结果
            Deque<ConversationManager.Message> history = new java.util.ArrayDeque<>();

            // 5. 执行多步骤任务（TaskExecutor 返回 AnalysisSummary）
            TaskExecutor executor = new TaskExecutor(plugin.getSkillManager());

            CompletableFuture<AnalysisSummary> future = executor.executeTask(plan, context, history, callback.getCallbackTask().getGoal());

            // 6. 处理执行结果：通过中间层进行 LLM 二次分析并输出
            future.thenAccept(summary -> {
                // 通过中间层输出（不显示占位符）
                plugin.getLlmOutputCoordinator().outputAnalysisResult(
                        creatorPlayer, summary, context, history,
                        OutputScenario.AFK_CALLBACK,
                        false  // 挂机任务回调不显示占位符
                );
            }).exceptionally(ex -> {
                PluginLogger.error("挂机任务", "回调任务执行异常: " + ex.getMessage(), ex);
                plugin.getLlmOutputCoordinator().outputError(creatorPlayer, "§c回调任务执行失败：" + ex.getMessage());
                return null;
            });

        } catch (Exception e) {
            Player errorPlayer = Bukkit.getPlayer(getPlayerUUID());
            if (errorPlayer != null && errorPlayer.isOnline()) {
                plugin.getLlmOutputCoordinator().outputError(errorPlayer, "§c回调任务启动失败：" + e.getMessage());
            }
            PluginLogger.error("挂机任务", "回调任务启动异常: " + e.getMessage(), e);
        }
    }

    /**
     * 替换 TaskPlan 中的占位符
     */
    private void replacePlaceholdersInTaskPlan(TaskPlan plan, String triggeredPlayerName) {
        // 这里可以替换 steps 中 entities 的占位符
        // 例如：{triggered_player} → 实际玩家名称
        //       {creator} → 任务创建者名称
        // 由于 TaskPlan 的 entities 是 Map<String, String>，需要遍历替换
        plan.getSteps().forEach(step -> {
            step.getEntities().replaceAll((key, value) -> {
                if (value == null) return null;
                return value.replace("{triggered_player}", triggeredPlayerName).replace("{creator}", getPlayerName());
            });
        });
    }

    @Override
    public String getTaskDescription() {
        if (callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty()) {
            String goal = callback.getCallbackTask().getGoal();
            String goalDesc = (goal != null && !goal.isEmpty()) ? "，目标：" + goal : "";
            return "监视玩家 " + targetPlayerName + " 上线，触发回调任务（" + callback.getCallbackTask().getSteps().size() + "步）" + goalDesc;
        }
        return "监视玩家 " + targetPlayerName + " 上线，上线后通知创建者（纯通知）";
    }
}
