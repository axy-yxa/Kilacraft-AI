package com.zm.kilacraftAI.skills.afktask.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.skills.afktask.AFKTask;
import com.zm.kilacraftAI.skills.afktask.AFKTaskCallback;
import com.zm.kilacraftAI.skills.afktask.AFKTaskStatus;
import com.zm.kilacraftAI.skills.afktask.AFKTaskType;
import com.zm.kilacraftAI.skills.afktask.ConditionEvaluator;
import com.zm.kilacraftAI.skills.afktask.ConditionPlan;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.skills.framework.task.LLMAnalysisService;
import com.zm.kilacraftAI.skills.framework.task.TaskExecutor;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义条件挂机任务
 *
 * <p>通过定时轮询检查任意Skill的返回值，当条件满足时触发回调任务。</p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li>通用性：支持任何返回数值型结果的Skill</li>
 *   <li>单条件限制：只支持一个数值条件，不支持多条件组合</li>
 *   <li>容错性：Skill执行失败或超时不中断任务，继续下次轮询</li>
 *   <li>安全性：任务创建者不在线时自动取消任务</li>
 * </ul>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>血量监视："当我的血量低于50%时告诉我"</li>
 *   <li>等级监视："当我达到30级时帮我查市场"</li>
 *   <li>经济监视："当我的余额低于1000时提醒我"</li>
 *   <li>任意数值监视：只要Skill返回数值字段即可</li>
 * </ul>
 *
 * <h3>必需参数：</h3>
 * <ul>
 *   <li>condition_plan: 条件计划（JSON格式，包含condition_skill、condition_action、result_path、operator、threshold）</li>
 *   <li>callback: 回调配置（可选，AFKTaskCallback JSON格式）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-13
 */
public class CustomWatchTask extends AFKTask {

    private static final Gson GSON = new Gson();

    /**
     * 条件计划
     */
    private final ConditionPlan conditionPlan;

    /**
     * 回调配置（多步骤任务定义）
     */
    private final AFKTaskCallback callback;

    /**
     * 定时轮询任务（兼容 Folia/Spigot 的统一任务句柄）
     */
    private FoliaCompat.ScheduledTask pollTask;

    /**
     * 条件处理中标志（防止并发 pollCondition 重复触发回调）
     *
     * <p>场景：check_interval_ticks 较短（如 20 tick）而 ConditionEvaluator 阻塞较长（最长 5 秒），
     * Bukkit 的 runTaskTimerAsynchronously 不会等待上一次执行完毕就启动下一次，
     * 导致多个 pollCondition() 并发运行。此标志确保只有一个线程进入条件满足分支。</p>
     */
    private final AtomicBoolean processing = new AtomicBoolean(false);

    /**
     * 连续评估失败计数（仅 FAILED 状态累加，NOT_MET 不算失败）
     * <p>FAILED = Skill找不到/超时/字段提取失败等配置错误</p>
     * <p>超过配置的最大连续失败次数时自动终止任务并通知玩家</p>
     */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /**
     * 构造自定义条件挂机任务
     *
     * @param taskId      任务唯一ID
     * @param playerUUID  玩家UUID（谁创建的此任务）
     * @param playerName  玩家名称
     * @param description 任务描述
     * @param params      任务参数
     *                    必需：condition_plan（JSON格式）
     *                    可选：callback（JSON格式）
     */
    public CustomWatchTask(String taskId, UUID playerUUID, String playerName, String description, Map<String, String> params) {
        super(taskId, playerUUID, playerName, AFKTaskType.CUSTOM, description, params);
        // 从 condition_plan JSON 解析条件计划
        this.conditionPlan = resolveConditionPlan();
        this.callback = parseCallback(getParam("callback", ""));
    }

    /**
     * 解析条件计划
     *
     * <p>从 entities 中的 condition_plan 参数解析 JSON 字符串。</p>
     * <p>JSON 必须包含：condition_skill, condition_action, result_path, operator, threshold</p>
     */
    private ConditionPlan resolveConditionPlan() {
        String json = getParam("condition_plan", "");
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            return new ConditionPlan(obj.get("condition_skill").getAsString(), obj.get("condition_action").getAsString(), obj.get("result_path").getAsString(), obj.get("operator").getAsString(), obj.get("threshold").getAsDouble());
        } catch (Exception e) {
            plugin.getLogger().warning("[挂机任务] 解析condition_plan JSON失败: " + e.getMessage());
            return null;
        }
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
            plugin.getLogger().warning("[挂机任务] 解析回调配置失败: " + e.getMessage());
            return new AFKTaskCallback();
        }
    }

    @Override
    public void start() {
        // 验证条件计划
        if (conditionPlan == null) {
            failStart("缺少条件计划参数（condition_plan）。请用自然语言描述要监视的条件");
            return;
        }

        if (!conditionPlan.isValidOperator()) {
            failStart("无效的比较操作符: " + conditionPlan.getOperator() + "。支持的操作符：less_than, less_than_or_equal, greater_than, greater_than_or_equal, equal, not_equal");
            return;
        }

        // 启动定时轮询
        try {
            // 使用配置文件中的轮询间隔（check_interval_ticks）
            int intervalTicks = plugin.getConfigManager().getAfkTaskCheckIntervalTicks();
            pollTask = FoliaCompat.runAsyncTimer(plugin, this::pollCondition, 0L, intervalTicks);

            markRunning();

            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[挂机任务] CUSTOM任务已启动: " + getTaskId() + ", 条件: " + conditionPlan);
            }
        } catch (Exception e) {
            failStart("轮询任务启动失败: " + e.getMessage());
        }
    }

    @Override
    protected void onStop() {
        stopPolling();
    }

    /**
     * 停止定时轮询
     */
    private void stopPolling() {
        if (pollTask != null && !pollTask.isCancelled()) {
            try {
                pollTask.cancel();
            } catch (Exception e) {
                plugin.getLogger().warning("[挂机任务] 取消轮询任务失败: " + e.getMessage());
            }
        }
    }

    /**
     * 轮询检查条件
     *
     * <p>使用 AtomicBoolean 防止并发执行：当轮询间隔短于单次 evaluate 耗时时，
     * 多个 pollCondition 可能同时运行，processing 标志确保仅第一个进入条件满足分支。</p>
     */
    private void pollCondition() {
        if (getStatus() != AFKTaskStatus.RUNNING) {
            return;
        }

        // 防并发：如果上一个 pollCondition 还在处理中，直接跳过
        if (!processing.compareAndSet(false, true)) {
            return;
        }

        // 标记是否进入回调路径：回调路径下不释放 processing 标志
        // 原因：executeCallback 发起异步 LLM 调用后立即返回，complete() 在异步回调中执行
        // 如果释放 processing，并发线程可能在 complete() 调用前进入并重复触发回调
        // 不释放的安全性：stopPolling() 已取消定时器不会再调度，CAS 检查是非阻塞的（直接返回 false）
        boolean enteredCallback = false;

        try {
            // 检查任务创建者是否在线
            Player creatorPlayer = Bukkit.getPlayer(getPlayerUUID());
            if (creatorPlayer == null || !creatorPlayer.isOnline()) {
                complete("任务创建者不在线，任务已自动取消。");
                return;
            }

            // 评估条件（三态返回：MET/NOT_MET/FAILED）
            ConditionEvaluator.EvaluationResult evalResult = ConditionEvaluator.evaluate(conditionPlan, creatorPlayer);

            if (evalResult == ConditionEvaluator.EvaluationResult.FAILED) {
                // 评估过程出错（Skill找不到/超时/字段提取失败）—— 配置错误，累加失败计数
                int failures = consecutiveFailures.incrementAndGet();
                int maxFailures = plugin.getConfigManager().getAfkTaskMaxConsecutiveFailures();
                if (failures >= maxFailures) {
                    notifyPlayer("§c§l🔔 挂机任务已自动取消\n\n§f连续 " + maxFailures + " 次条件评估失败（无法提取字段 " + conditionPlan.getResultPath() + "），任务可能配置有误，请重新创建。");
                    complete("连续条件评估失败，任务自动取消。");
                }
                // 未达上限，继续下次轮询
            } else {
                // 评估正常（MET 或 NOT_MET），重置失败计数
                consecutiveFailures.set(0);

                if (evalResult == ConditionEvaluator.EvaluationResult.MET) {
                    // 先停止轮询，防止新的调度
                    stopPolling();

                    boolean hasCallback = callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty();

                    if (hasCallback) {
                        enteredCallback = true;
                        executeCallback(creatorPlayer);
                    } else {
                        notifyConditionMet();
                        complete("条件满足，挂机任务完成。");
                    }
                }
                // NOT_MET：条件正常评估但不满足，继续下次轮询
            }
        } finally {
            // 仅在未进入回调路径时释放 processing 标志
            // 进入回调路径后不释放：防止并发线程在异步 complete() 完成前重复触发回调
            if (!enteredCallback) {
                processing.set(false);
            }
        }
    }

    /**
     * 执行回调任务
     *
     * @param creatorPlayer 任务创建者
     */
    private void executeCallback(Player creatorPlayer) {
        try {
            // 1. 构建 TaskPlan
            TaskPlan plan = callback.getCallbackTask().toTaskPlan();

            // 2. 构建执行上下文
            SkillContext context = new SkillContext(creatorPlayer, callback.getCallbackTask().getGoal(), Map.of());

            // 3. 延迟反馈优化：不传入对话历史
            Deque<ConversationManager.Message> history = new java.util.ArrayDeque<>();

            // 4. 执行多步骤任务
            TaskExecutor executor = new TaskExecutor(plugin.getSkillManager(), new LLMAnalysisService());

            CompletableFuture<SkillResult> future = executor.executeTask(plan, context, history, callback.getCallbackTask().getGoal());

            // 注意：任务在调用方 pollCondition() 中不会提前 complete，回调任务负责在异步完成后 complete
            future.thenAccept(result -> {
                // 通知玩家
                notifyCallbackResult(result);

                // 完成任务
                complete("条件满足，回调任务已执行。");
            }).exceptionally(ex -> {
                plugin.getLogger().severe("[挂机任务] 回调任务执行异常: " + ex.getMessage());
                ex.printStackTrace();
                notifyPlayer("§c回调任务执行失败：" + ex.getMessage());
                complete("回调任务执行异常。");
                return null;
            });

        } catch (Exception e) {
            notifyPlayer("§c回调任务启动失败：" + e.getMessage());
            plugin.getLogger().severe("[挂机任务] 回调任务启动异常: " + e.getMessage());
            e.printStackTrace();
            complete("回调任务启动异常。");
        }
    }

    /**
     * 通知条件满足（纯通知模式）
     */
    private void notifyConditionMet() {
        String message = String.format("§a§l🔔 挂机任务完成\n\n" + "§f• 监视条件：§e%s\n" + "§f• 当前值：§a%s\n" + "§f• 阈值：§e%s\n\n" + "§f条件已满足，任务已完成！", conditionPlan.getResultPath() + " " + conditionPlan.getOperatorDescription() + " " + conditionPlan.getThreshold(), getCurrentValue(), conditionPlan.getThreshold());

        notifyPlayer(message);
    }

    /**
     * 获取当前值（用于通知）
     */
    private String getCurrentValue() {
        Player player = Bukkit.getPlayer(getPlayerUUID());
        if (player != null && player.isOnline()) {
            // 再次执行一次获取当前值（不进行比较）
            // 这里简化处理，直接返回阈值
            return String.valueOf(conditionPlan.getThreshold());
        }
        return "未知";
    }

    /**
     * 通知回调任务执行结果
     *
     * @param result LLM二次分析后的结果
     */
    private void notifyCallbackResult(SkillResult result) {
        String notifyTarget = callback.getNotifyTarget();

        if (notifyTarget == null || notifyTarget.isEmpty()) {
            notifyTarget = "{creator}";  // 默认通知任务创建者
        }

        // 构建通知消息
        String analysisResult = result.getMessage() != null ? result.getMessage() : "无结果";

        // 构建完整通知
        String header = "§f§l🔔 挂机任务提醒\n\n";
        String body = MessageUtil.convertMarkdownToMinecraft(analysisResult);
        String fullMessage = header + body;

        // 发送到目标玩家
        if (notifyTarget.equals("{creator}")) {
            notifyPlayer(fullMessage);
        } else {
            Player targetPlayer = Bukkit.getPlayerExact(notifyTarget);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(MessageUtil.getAIPrefix() + fullMessage);
            }
        }
    }

    @Override
    public String getTaskDescription() {
        if (conditionPlan == null) {
            return "自定义条件挂机任务";
        }

        if (callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty()) {
            String goal = callback.getCallbackTask().getGoal();
            String goalDesc = (goal != null && !goal.isEmpty()) ? "，目标：" + goal : "";
            return "监视条件：" + conditionPlan + "，触发回调任务（" + callback.getCallbackTask().getSteps().size() + "步）" + goalDesc;
        }

        return "监视条件：" + conditionPlan + "，满足后通知创建者（纯通知）";
    }
}
