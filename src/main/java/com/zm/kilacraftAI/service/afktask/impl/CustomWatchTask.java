package com.zm.kilacraftAI.service.afktask.impl;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.zm.kilacraftAI.common.enums.AFKTaskStatusEnum;
import com.zm.kilacraftAI.common.enums.AFKTaskTypeEnum;
import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.common.util.ArithmeticUtil;
import com.zm.kilacraftAI.common.util.JsonSafeGetUtil;
import com.zm.kilacraftAI.common.util.LogSnippetUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.model.afktask.AFKTask;
import com.zm.kilacraftAI.model.afktask.AFKTaskCallback;
import com.zm.kilacraftAI.model.afktask.ConditionPlan;
import com.zm.kilacraftAI.service.afktask.ConditionEvaluator;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.task.AnalysisSummary;
import com.zm.kilacraftAI.skills.framework.task.TaskExecutor;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
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
        super(taskId, playerUUID, playerName, AFKTaskTypeEnum.CUSTOM, description, params);
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
            if (obj == null) {
                obj = GSON.fromJson(JsonSafeGetUtil.repairJsonBraces(json), JsonObject.class);
            }
            return parseConditionPlanFields(obj);
        } catch (Exception e) {
            // 尝试自动修复不完整的 JSON
            String repaired = JsonSafeGetUtil.repairJsonBraces(json);
            if (!repaired.equals(json)) {
                try {
                    PluginLoggerUtil.debug("挂机任务", I18nService.tr("JSON 自动修复成功"));
                    return parseConditionPlanFields(GSON.fromJson(repaired, JsonObject.class));
                } catch (Exception ignored) {
                    // 修复后仍然失败
                }
            }
            PluginLoggerUtil.warn("挂机任务", I18nService.tr("解析condition_plan JSON失败: {}。原始内容: {}", e.getMessage(), LogSnippetUtil.truncateForLog(json, 200)), e);
            return null;
        }
    }

    /**
     * 从 JsonObject 中解析 ConditionPlan 字段
     *
     * <p>threshold 解析顺序：</p>
     * <ol>
     *   <li>布尔 → 1.0 / 0.0</li>
     *   <li>数值 → 直接取值</li>
     *   <li>字符串 → 尝试算术求值（占位符已被外层 TaskExecutor 替换，如 "20-5"）；
     *       再尝试纯数字字符串（如 "15"）；都不是则视为真正的字符串阈值（如方块类型 "GRASS_BLOCK"）</li>
     * </ol>
     * <p>算术支持使得相对阈值（"{step_0.health}-5"）可用：外层替换占位符为具体值后，
     * 此处求值固化。仅支持单次二元运算（+ - * /）。</p>
     */
    private ConditionPlan parseConditionPlanFields(JsonObject obj) {
        JsonElement thresholdEl = obj.get("threshold");
        double thresholdValue = 0;
        String thresholdStr = null;
        if (thresholdEl == null) {
            // threshold 缺失，保持默认值 0
        } else if (thresholdEl.isJsonPrimitive() && thresholdEl.getAsJsonPrimitive().isBoolean()) {
            thresholdValue = thresholdEl.getAsBoolean() ? 1.0 : 0.0;
        } else if (thresholdEl.isJsonPrimitive() && thresholdEl.getAsJsonPrimitive().isNumber()) {
            thresholdValue = thresholdEl.getAsDouble();
        } else {
            // 字符串阈值：可能是算术表达式（占位符已被外层替换，如 "20-5"）、纯数字字符串（如 "15"）、或真正的字符串阈值（如 "GRASS_BLOCK"）
            String s = thresholdEl.getAsString();
            // 优先尝试算术求值（单次二元运算），让相对阈值（如 {step_0.health}-5）可用
            Double computed = ArithmeticUtil.tryEvalBinary(s);
            if (computed != null) {
                thresholdValue = computed;
            } else {
                try {
                    // 纯数字字符串（如 "15"）按数值处理
                    thresholdValue = Double.parseDouble(s);
                } catch (NumberFormatException nfe) {
                    // 真正的字符串阈值（如方块类型 GRASS_BLOCK），仅用于 equal/not_equal
                    thresholdStr = s;
                }
            }
        }
        // 解析 condition_params（条件技能的执行参数，如 item=经验瓶）
        Map<String, String> conditionParams = Collections.emptyMap();
        JsonElement paramsEl = obj.get("condition_params");
        if (paramsEl != null && paramsEl.isJsonObject()) {
            conditionParams = GSON.fromJson(paramsEl, new TypeToken<Map<String, String>>() {
            }.getType());
        }
        return new ConditionPlan(obj.get("condition_skill").getAsString(), obj.get("condition_action").getAsString(), obj.get("result_path").getAsString(), obj.get("operator").getAsString(), thresholdValue, thresholdStr, conditionParams);
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
            // 尝试自动修复不完整的 JSON
            String repaired = JsonSafeGetUtil.repairJsonBraces(json);
            if (!repaired.equals(json)) {
                try {
                    PluginLoggerUtil.debug("挂机任务", I18nService.tr("回调 JSON 自动修复成功"));
                    return GSON.fromJson(repaired, AFKTaskCallback.class);
                } catch (Exception ignored) {
                    // 修复后仍然失败
                }
            }
            PluginLoggerUtil.warn("挂机任务", I18nService.tr("解析回调配置失败: {}。原始内容: {}。任务将以仅通知模式运行（回调动作无法执行）", e.getMessage(), LogSnippetUtil.truncateForLog(json, 200)), e);
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
            failStart(I18nService.tr("无效的比较操作符: {}。支持的操作符：less_than, less_than_or_equal, greater_than, greater_than_or_equal, equal, not_equal", conditionPlan.getOperator()));
            return;
        }

        // 启动定时轮询
        try {
            // 使用配置文件中的轮询间隔（check_interval_ticks）
            int intervalTicks = plugin.getConfigManager().getAfkTaskCheckIntervalTicks();
            pollTask = FoliaCompat.runAsyncTimer(plugin, this::pollCondition, 0L, intervalTicks);

            markRunning();

            PluginLoggerUtil.debug("挂机任务", "CUSTOM任务已启动: {}, 条件: {}", getTaskId(), conditionPlan);
        } catch (Exception e) {
            failStart(I18nService.tr("轮询任务启动失败: {}", e.getMessage()));
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
                PluginLoggerUtil.warn("挂机任务", I18nService.tr("取消轮询任务失败: {}", e.getMessage()), e);
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
        if (getStatus() != AFKTaskStatusEnum.RUNNING) {
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
                complete(I18nService.tr("任务创建者不在线，任务已自动取消。"));
                return;
            }

            // 评估条件（三态返回：MET/NOT_MET/FAILED + 实际值）
            ConditionEvaluator.EvaluationResult evalResult = ConditionEvaluator.evaluate(conditionPlan, creatorPlayer);

            if (evalResult.isFailed()) {
                // 评估过程出错（Skill找不到/超时/字段提取失败）—— 配置错误，累加失败计数
                int failures = consecutiveFailures.incrementAndGet();
                int maxFailures = plugin.getConfigManager().getAfkTaskMaxConsecutiveFailures();
                if (failures >= maxFailures) {
                    notifyPlayer(I18nService.tr("§c§l挂机任务已自动取消\n\n§f连续 {} 次条件评估失败（无法提取字段 {}），任务可能配置有误，请重新创建。", maxFailures, conditionPlan.getResultPath()));
                    complete(I18nService.tr("连续条件评估失败，任务自动取消。"));
                }
                // 未达上限，继续下次轮询
            } else {
                // 评估正常（MET 或 NOT_MET），重置失败计数
                consecutiveFailures.set(0);

                if (evalResult.isMet()) {
                    // 先停止轮询，防止新的调度
                    stopPolling();

                    boolean hasCallback = callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty();

                    if (hasCallback) {
                        enteredCallback = true;
                        String currentDisplay = evalResult.actualValueStr() != null ? evalResult.actualValueStr() : (evalResult.actualValue() != null ? String.valueOf(evalResult.actualValue()) : I18nService.tr("未知"));
                        String conditionDesc = I18nService.tr("挂机任务条件满足：") + conditionPlan.getConditionSkill() + "." + conditionPlan.getConditionAction() + " " + conditionPlan.getOperatorDescription() + " " + (conditionPlan.getThresholdStr() != null ? conditionPlan.getThresholdStr() : String.valueOf(conditionPlan.getThreshold())) + I18nService.tr("（当前值：{}）", currentDisplay);
                        executeCallback(creatorPlayer, conditionDesc);
                    } else {
                        notifyConditionMet(evalResult);
                        complete(I18nService.tr("条件满足，挂机任务完成。"));
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
     * @param conditionDesc 条件满足的事件描述，注入到 LLM 二次分析的上下文中
     */
    private void executeCallback(Player creatorPlayer, String conditionDesc) {
        try {
            // 1. 构建 TaskPlan
            TaskPlan plan = callback.getCallbackTask().toTaskPlan();
            // 过滤末尾的 notify_player 步骤（与 AFK_CALLBACK 自动总结重复）
            stripTrailingNotifyPlayer(plan);

            // 2. 构建执行上下文
            SkillContext context = new SkillContext(creatorPlayer, callback.getCallbackTask().getGoal(), Map.of());

            // 3. 延迟反馈优化：不传入对话历史
            Deque<ConversationManager.Message> history = new ArrayDeque<>();

            // 4. 执行多步骤任务（TaskExecutor 返回 AnalysisSummary）
            TaskExecutor executor = new TaskExecutor(plugin.getSkillManager());

            // userMessage 仅保留 goal，事件描述通过 injectEventTrigger 注入到 [执行结果] 区域
            String goal = callback.getCallbackTask().getGoal();

            CompletableFuture<AnalysisSummary> future = executor.executeTask(plan, context, history, goal);

            // 5. 处理执行结果：注入事件触发描述 + 通过中间层进行LLM二次分析并输出
            future.thenAccept(summary -> {
                summary.injectEventTrigger(conditionDesc);
                plugin.getLlmOutputCoordinator().outputAnalysisResult(creatorPlayer, summary, context, history, OutputScenarioEnum.AFK_CALLBACK, false);

                // 完成任务
                complete(I18nService.tr("条件满足，回调任务已执行。"));
            }).exceptionally(ex -> {
                PluginLoggerUtil.error("挂机任务", I18nService.tr("回调任务执行异常: {}", ex.getMessage()), ex);
                Player errorPlayer = Bukkit.getPlayer(getPlayerUUID());
                if (errorPlayer != null && errorPlayer.isOnline()) {
                    plugin.getLlmOutputCoordinator().outputError(errorPlayer, I18nService.tr("§c回调任务执行失败：{}", ex.getMessage()));
                }
                complete(I18nService.tr("回调任务执行异常。"));
                return null;
            });

        } catch (Exception e) {
            notifyPlayer(I18nService.tr("§c回调任务启动失败：{}", e.getMessage()));
            PluginLoggerUtil.error("挂机任务", I18nService.tr("回调任务启动异常: {}", e.getMessage()), e);
            complete(I18nService.tr("回调任务启动异常。"));
        }
    }

    /**
     * 通知条件满足（纯通知模式）
     *
     * @param evalResult 条件评估结果（含真实当前值：数值条件用 actualValue，字符串条件用 actualValueStr）
     */
    private void notifyConditionMet(ConditionEvaluator.EvaluationResult evalResult) {
        // 构建丰富的条件描述（面向 LLM 二次分析，需包含足够上下文让 LLM 生成友好的通知）
        // 当前值：优先字符串值（如方块类型 DIRT），其次数值
        String currentValueStr;
        if (evalResult.actualValueStr() != null) {
            currentValueStr = evalResult.actualValueStr();
        } else if (evalResult.actualValue() != null) {
            currentValueStr = String.valueOf(evalResult.actualValue());
        } else {
            currentValueStr = I18nService.tr("未知");
        }
        String thresholdDisplay = conditionPlan.getThresholdStr() != null ? conditionPlan.getThresholdStr() : String.valueOf(conditionPlan.getThreshold());
        StringBuilder eventDesc = new StringBuilder();
        eventDesc.append(I18nService.tr("挂机任务条件满足："));
        eventDesc.append(conditionPlan.getConditionSkill()).append(".").append(conditionPlan.getConditionAction());
        eventDesc.append(" ").append(I18nService.tr("返回的")).append(" ").append(conditionPlan.getResultPath());
        eventDesc.append(" ").append(conditionPlan.getOperatorDescription()).append(" ").append(thresholdDisplay);
        eventDesc.append(I18nService.tr("（当前值：{}）", currentValueStr));
        // 附加条件参数，让 LLM 知道监控的具体对象
        if (!conditionPlan.getConditionParams().isEmpty()) {
            eventDesc.append(I18nService.tr("，监控参数：{}", conditionPlan.getConditionParams()));
        }
        notifyWithLLMAnalysis(eventDesc.toString());
    }

    @Override
    public String getTaskDescription() {
        if (conditionPlan == null) {
            return I18nService.tr("自定义条件挂机任务");
        }

        if (callback != null && callback.getCallbackTask() != null && callback.getCallbackTask().getSteps() != null && !callback.getCallbackTask().getSteps().isEmpty()) {
            String goal = callback.getCallbackTask().getGoal();
            String goalDesc = (goal != null && !goal.isEmpty()) ? I18nService.tr("，目标：{}", goal) : "";
            return I18nService.tr("监视条件：{}，触发回调任务（{}步）{}", conditionPlan, callback.getCallbackTask().getSteps().size(), goalDesc);
        }

        return I18nService.tr("监视条件：{}，满足后通知创建者（纯通知）", conditionPlan);
    }
}
