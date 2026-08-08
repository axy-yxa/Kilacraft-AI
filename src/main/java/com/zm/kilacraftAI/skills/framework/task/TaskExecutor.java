package com.zm.kilacraftAI.skills.framework.task;

import com.zm.kilacraftAI.common.util.ArithmeticUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.skills.framework.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任务执行器 - 按照任务计划的顺序执行每个步骤
 * <p>
 * 主要功能：
 * 1. 拓扑排序：根据依赖关系确定执行顺序
 * 2. 逐步执行：按顺序执行每个步骤
 * 3. 上下文传递：将前置步骤的结果传递给后续步骤
 * 4. 结果汇总：返回 AnalysisSummary，由调用方通过 LLMOutputCoordinator 进行 LLM 二次分析
 *
 * @author Zm_Mmm
 * @since 2026-04-02
 */
public class TaskExecutor {

    private final SkillManager skillManager;

    public TaskExecutor(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    /**
     * 执行任务计划
     *
     * @param plan        任务计划
     * @param baseContext 基础上下文
     * @param history     对话历史（用于二次分析时的上下文关联）
     * @param userMessage 用户的原始输入（用于构建统一的分析摘要）
     * @return AnalysisSummary（由调用方通过 LLMOutputCoordinator 进行 LLM 二次分析）
     */
    public CompletableFuture<AnalysisSummary> executeTask(TaskPlan plan, SkillContext baseContext, Deque<ConversationManager.Message> history, String userMessage) {
        PluginLoggerUtil.debug("任务执行", "共 {} 个步骤", plan.getStepCount());

        // 多步骤任务：先进行拓扑排序
        List<TaskStep> sortedSteps = topologicalSort(plan);
        if (sortedSteps == null) {
            // 返回失败的 AnalysisSummary
            AnalysisSummary errorSummary = new AnalysisSummary().userMessage(userMessage).taskGoal(plan.getGoal()).addResult("INIT", "FAILURE", I18nService.tr("任务计划存在循环依赖，无法执行")).statistics(0, 1, 0, 0);
            return CompletableFuture.completedFuture(errorSummary);
        }

        PluginLoggerUtil.debug("任务执行", "执行顺序：{} 步", sortedSteps.size());

        // 递归执行所有步骤
        return executeSteps(plan, sortedSteps, 0, baseContext, history, userMessage);
    }

    /**
     * 递归执行步骤列表
     * <p>
     * 错误保障机制：即使部分步骤失败，也不会中断整个流程
     * - 依赖未满足：记录失败原因，跳过该步骤，继续执行
     * - 占位符解析失败：记录失败原因，跳过该步骤，继续执行
     * - 技能执行失败：记录失败原因，继续执行下一步
     * - 最终汇总时，返回 AnalysisSummary 由调用方进行 LLM 二次分析
     *
     * @param plan        任务计划
     * @param sortedSteps 已排序的步骤列表
     * @param stepIndex   当前执行的步骤索引
     * @param baseContext 基础上下文
     * @param history     对话历史
     * @return AnalysisSummary
     */
    private CompletableFuture<AnalysisSummary> executeSteps(TaskPlan plan, List<TaskStep> sortedSteps, int stepIndex, SkillContext baseContext, Deque<ConversationManager.Message> history, String userMessage) {

        if (stepIndex >= sortedSteps.size()) {
            // 所有步骤执行完成，进行最终分析
            return synthesizeResults(plan, baseContext, userMessage);
        }

        TaskStep currentStep = sortedSteps.get(stepIndex);

        // 检查依赖是否已满足（包括依赖步骤是否执行成功）
        String dependencyError = checkDependencies(plan, currentStep);
        if (dependencyError != null) {
            // 依赖未满足，记录失败原因，跳过该步骤，继续执行
            plan.getContext().put(currentStep.getId(), SkillResultFormatter.skipped(I18nService.tr("依赖未满足：{}", dependencyError)));
            PluginLoggerUtil.debug("任务执行", "步骤 {} 因依赖未满足被跳过: {}", currentStep.getId(), dependencyError);
            return executeSteps(plan, sortedSteps, stepIndex + 1, baseContext, history, userMessage);
        }

        PluginLoggerUtil.debug("任务执行", "执行步骤 [{}/{}]: {} - {}", stepIndex + 1, sortedSteps.size(), currentStep.getId(), currentStep.getAction());

        // 创建技能意图
        SkillIntent intent = new SkillIntent(currentStep.getSkillName(), currentStep.getAction(), currentStep.getEntities(), 1.0, plan.getGoal());

        // 构建步骤上下文（可能失败，如占位符解析失败）
        BuildContextResult buildResult = buildStepContext(currentStep, plan, baseContext);
        if (buildResult.isFailed()) {
            // 占位符解析失败，记录失败原因，跳过该步骤，继续执行
            plan.getContext().put(currentStep.getId(), SkillResultFormatter.skipped(I18nService.tr("参数解析失败：{}", buildResult.errorMessage)));
            PluginLoggerUtil.debug("任务执行", "步骤 {} 因参数解析失败被跳过: {}", currentStep.getId(), buildResult.errorMessage);
            return executeSteps(plan, sortedSteps, stepIndex + 1, baseContext, history, userMessage);
        }
        SkillContext stepContext = buildResult.context;

        // 执行技能
        return skillManager.executeSkillByIntent(intent, stepContext).thenCompose(result -> {
            // 保存结果到上下文（无论成功失败）
            plan.getContext().put(currentStep.getId(), result);

            // 继续执行下一步
            return executeSteps(plan, sortedSteps, stepIndex + 1, baseContext, history, userMessage);
        });
    }

    /**
     * 拓扑排序 - 根据依赖关系确定执行顺序
     * 使用 DFS 实现
     *
     * @return 排序后的步骤列表，如果存在循环依赖则返回 null
     */
    private List<TaskStep> topologicalSort(TaskPlan plan) {
        Map<String, TaskStep> stepMap = new HashMap<>();
        for (TaskStep step : plan.getSteps()) {
            stepMap.put(step.getId(), step);
        }

        List<TaskStep> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>(); // 用于检测循环依赖

        // DFS 遍历所有节点
        for (TaskStep step : plan.getSteps()) {
            if (!visited.contains(step.getId())) {
                if (visit(step, stepMap, visited, visiting, result)) {
                    return null; // 存在循环依赖
                }
            }
        }

        return result;
    }

    /**
     * DFS 访问单个节点
     *
     * @return true 如果检测到循环依赖
     */
    private boolean visit(TaskStep step, Map<String, TaskStep> stepMap, Set<String> visited, Set<String> visiting, List<TaskStep> result) {
        String id = step.getId();

        // 如果当前节点正在访问中，说明存在循环依赖
        if (visiting.contains(id)) {
            PluginLoggerUtil.error("任务执行", "检测到循环依赖：{}", id);
            return true;
        }

        // 如果已经访问过，跳过
        if (visited.contains(id)) {
            return false;
        }

        // 标记为正在访问
        visiting.add(id);

        // 递归访问所有依赖的前置步骤
        for (String dependencyId : step.getDependsOn()) {
            TaskStep dependency = stepMap.get(dependencyId);
            if (dependency != null) {
                if (visit(dependency, stepMap, visited, visiting, result)) {
                    return true;
                }
            }
        }

        // 标记为已访问，并加入结果集
        visiting.remove(id);
        visited.add(id);
        result.add(step);
        return false;
    }

    /**
     * 检查步骤依赖是否已满足
     * <p>
     * 不仅检查依赖步骤是否存在，还检查依赖步骤是否执行成功
     *
     * @return null 表示依赖满足，否则返回失败原因
     */
    private String checkDependencies(TaskPlan plan, TaskStep step) {
        for (String dependencyId : step.getDependsOn()) {
            Object result = plan.getContext().get(dependencyId);

            // 依赖步骤不存在
            if (result == null) {
                String error = I18nService.tr("依赖步骤 {} 尚未执行", dependencyId);
                PluginLoggerUtil.debug("任务执行", error);
                return error;
            }

            // 依赖步骤执行失败
            if (result instanceof SkillResult skillResult && !skillResult.isSuccess()) {
                String error = I18nService.tr("依赖步骤 {} 执行失败", dependencyId);
                PluginLoggerUtil.debug("任务执行", error);
                return error;
            }
        }
        return null;
    }

    /**
     * 构建步骤上下文：解析 entities 中的占位符（如 {step_1.item_name}）。
     */
    private BuildContextResult buildStepContext(TaskStep step, TaskPlan plan, SkillContext baseContext) {
        Map<String, String> resolvedEntities = new HashMap<>();
        for (Map.Entry<String, String> entry : step.getEntities().entrySet()) {
            String key = entry.getKey();
            PlaceholderResolveResult result = resolvePlaceholders(entry.getValue(), plan);
            if (result.isFailed()) {
                // 占位符解析失败，终止执行
                String errorMsg = I18nService.tr("步骤 {} 的参数 '{}' 解析失败：找不到 {}", step.getId(), key, result.failedPlaceholder);
                PluginLoggerUtil.debug("任务执行", errorMsg);
                return new BuildContextResult(null, errorMsg);
            }
            resolvedEntities.put(key, result.resolvedValue);
        }
        return new BuildContextResult(new SkillContext(baseContext.getPlayer(), step.getAction(), resolvedEntities), null);
    }

    /**
     * 解析占位符（如 {step_1.item_name} 或 {step_1.warps[0].warp_name}）。
     * 占位符无法解析即视为失败（多步骤任务的占位符必须指向已执行的前置步骤）。
     *
     * @param value 可能包含占位符的字符串
     * @param plan  任务计划（用于查找步骤结果）
     */
    @SuppressWarnings("unchecked")
    private PlaceholderResolveResult resolvePlaceholders(String value, TaskPlan plan) {
        if (value == null || !value.contains("{")) {
            return new PlaceholderResolveResult(value, null);
        }

        // 匹配 {step_xxx.field} 或 {step_xxx.field[0].subfield} 格式的占位符
        Pattern pattern = Pattern.compile("\\{(step_\\w+)\\.([\\w\\[\\].]+)\\}");
        Matcher matcher = pattern.matcher(value);

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String stepId = matcher.group(1);
            String fieldPath = matcher.group(2); // 可能是 "warp_name" 或 "warps[0].warp_name"
            String placeholder = matcher.group(0); // 完整的占位符

            // 从 plan.context 中获取步骤结果
            Object stepResult = plan.getContext().get(stepId);
            if (stepResult instanceof SkillResult skillResult && skillResult.getData() instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) skillResult.getData();
                Object fieldValue = resolveFieldPath(data, fieldPath);
                if (fieldValue != null) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(fieldValue.toString()));
                    continue;
                }
            }
            // 占位符解析失败，返回失败信息
            PluginLoggerUtil.debug("任务执行", "占位符解析失败：{} (步骤 {} 不存在或路径 {} 无效)", placeholder, stepId, fieldPath);
            return new PlaceholderResolveResult(null, placeholder);
        }
        matcher.appendTail(sb);
        String resolved = sb.toString();

        // 占位符替换后可能残留运算表达式（如 {step_0.balance}/2 → 2623.25/2），求值固化
        resolved = ArithmeticUtil.evalAndFormat(resolved);

        return new PlaceholderResolveResult(resolved, null);
    }

    /**
     * 解析字段路径（支持数组访问，如 "warps[0].warp_name"）
     */
    @SuppressWarnings("unchecked")
    private Object resolveFieldPath(Map<String, Object> data, String fieldPath) {
        // 解析路径：warps[0].warp_name -> ["warps[0]", "warp_name"]
        String[] parts = fieldPath.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (current == null) return null;

            // 检查是否是数组访问：warps[0]
            Matcher arrayMatcher = Pattern.compile("^(\\w+)\\[(\\d+)\\]$").matcher(part);
            if (arrayMatcher.find()) {
                String arrayKey = arrayMatcher.group(1);
                int index = Integer.parseInt(arrayMatcher.group(2));

                if (!(current instanceof Map)) return null;
                Map<String, Object> map = (Map<String, Object>) current;
                Object arrayObj = map.get(arrayKey);

                if (!(arrayObj instanceof List<?> list)) return null;

                if (index < 0 || index >= list.size()) return null;
                current = list.get(index);
            } else {
                // 普通字段访问
                if (!(current instanceof Map)) return null;
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(part);
            }
        }

        return current;
    }

    /**
     * 汇总所有步骤的结果，返回 AnalysisSummary
     * <p>
     * 结果格式结构化，明确标记每个步骤的执行状态（SUCCESS/FAILURE/SKIPPED）
     * 注意：不再直接调用 LLM 分析，而是返回 AnalysisSummary 由调用方处理
     */
    public CompletableFuture<AnalysisSummary> synthesizeResults(TaskPlan plan, SkillContext baseContext, String userMessage) {
        PluginLoggerUtil.debug("任务执行", "所有步骤执行完成，开始汇总结果...");

        // 构建统一的分析摘要
        AnalysisSummary summary = new AnalysisSummary().userMessage(userMessage).taskGoal(plan.getGoal()).reasoning(plan.getReasoning());

        for (Map.Entry<String, Object> entry : plan.getContext().entrySet()) {
            String stepId = entry.getKey();
            Object result = entry.getValue();

            if (result instanceof SkillResult skillResult) {
                summary.addResult(stepId, skillResult.getStatus().name(), skillResult.getMessage());
            } else {
                summary.addResult(stepId, "UNKNOWN", String.valueOf(result));
            }
        }

        // 统计：NEED_INFO 单列为 needInfo（非 failure），避免多步骤某步暂停等待玩家输入时
        // 统计行误导 LLM 二次分析为"任务失败"
        int success = 0, failure = 0, skipped = 0, needInfo = 0;
        for (var r : summary.getResults()) {
            switch (r.status()) {
                case "SUCCESS" -> success++;
                case "SKIPPED" -> skipped++;
                case "NEED_INFO" -> needInfo++;
                default -> failure++;
            }
        }
        summary.statistics(success, failure, skipped, needInfo);

        return CompletableFuture.completedFuture(summary);
    }
}
