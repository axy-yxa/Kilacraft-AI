package com.zm.kilacraftAI.skills.framework.task;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillIntent;
import com.zm.kilacraftAI.skills.framework.SkillManager;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 任务执行器 - 按照任务计划的顺序执行每个步骤
 * <p>
 * 主要功能：
 * 1. 拓扑排序：根据依赖关系确定执行顺序
 * 2. 逐步执行：按顺序执行每个步骤
 * 3. 上下文传递：将前置步骤的结果传递给后续步骤
 * 4. 结果汇总：LLM 分析所有步骤的结果并给出综合性回复
 */
@RequiredArgsConstructor
public class TaskExecutor {

    private final SkillManager skillManager;
    private final LLMAnalysisService analysisService;

    private final KilacraftAI plugin = KilacraftAI.getInstance();

    /**
     * 执行任务计划
     *
     * @param plan        任务计划
     * @param baseContext 基础上下文
     * @param history     对话历史（用于二次分析时的上下文关联）
     * @param userMessage 用户的原始输入（用于构建统一的分析摘要）
     * @return 最终执行结果
     */
    public CompletableFuture<SkillResult> executeTask(TaskPlan plan, SkillContext baseContext, Deque<ConversationManager.Message> history, String userMessage) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 共 " + plan.getStepCount() + " 个步骤");
        }

        // 多步骤任务：先进行拓扑排序
        List<TaskStep> sortedSteps = topologicalSort(plan);
        if (sortedSteps == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("任务计划存在循环依赖，无法执行"));
        }

        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 执行顺序：" + sortedSteps.size() + " 步");
        }

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
     * - 最终汇总时，LLM 会基于所有成功步骤的结果尽可能回答用户问题
     *
     * @param plan        任务计划
     * @param sortedSteps 已排序的步骤列表
     * @param stepIndex   当前执行的步骤索引
     * @param baseContext 基础上下文
     * @param history     对话历史
     * @return 最终结果
     */
    private CompletableFuture<SkillResult> executeSteps(TaskPlan plan, List<TaskStep> sortedSteps, int stepIndex, SkillContext baseContext, Deque<ConversationManager.Message> history, String userMessage) {

        if (stepIndex >= sortedSteps.size()) {
            // 所有步骤执行完成，进行最终分析
            return synthesizeResults(plan, baseContext, history, userMessage);
        }

        TaskStep currentStep = sortedSteps.get(stepIndex);

        // 检查依赖是否已满足（包括依赖步骤是否执行成功）
        String dependencyError = checkDependencies(plan, currentStep);
        if (dependencyError != null) {
            // 依赖未满足，记录失败原因，跳过该步骤，继续执行
            plan.getContext().put(currentStep.getId(), SkillResult.failure("[依赖未满足] " + dependencyError));
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().warning("[DEBUG] 步骤 " + currentStep.getId() + " 因依赖未满足被跳过: " + dependencyError);
            }
            return executeSteps(plan, sortedSteps, stepIndex + 1, baseContext, history, userMessage);
        }

        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 执行步骤 [" + (stepIndex + 1) + "/" + sortedSteps.size() + "]: " + currentStep.getId() + " - " + currentStep.getAction());
        }

        // 创建技能意图
        SkillIntent intent = new SkillIntent(currentStep.getSkillName(), currentStep.getAction(), currentStep.getEntities(), 1.0, plan.getGoal());

        // 构建步骤上下文（可能失败，如占位符解析失败）
        BuildContextResult buildResult = buildStepContext(currentStep, plan, baseContext);
        if (buildResult.isFailed()) {
            // 占位符解析失败，记录失败原因，跳过该步骤，继续执行
            plan.getContext().put(currentStep.getId(), SkillResult.failure("[参数解析失败] " + buildResult.errorMessage));
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().warning("[DEBUG] 步骤 " + currentStep.getId() + " 因参数解析失败被跳过: " + buildResult.errorMessage);
            }
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
     * @return false 如果检测到循环依赖
     */
    private boolean visit(TaskStep step, Map<String, TaskStep> stepMap, Set<String> visited, Set<String> visiting, List<TaskStep> result) {
        String id = step.getId();

        // 如果当前节点正在访问中，说明存在循环依赖
        if (visiting.contains(id)) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().severe("[DEBUG] 检测到循环依赖：" + id);
            }
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
                String error = "依赖步骤 " + dependencyId + " 尚未执行";
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("[DEBUG] " + error);
                }
                return error;
            }

            // 依赖步骤执行失败
            if (result instanceof SkillResult skillResult && !skillResult.isSuccess()) {
                String error = "依赖步骤 " + dependencyId + " 执行失败";
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("[DEBUG] " + error);
                }
                return error;
            }
        }
        return null;
    }

    /**
     * 构建步骤上下文
     */
    private BuildContextResult buildStepContext(TaskStep step, TaskPlan plan, SkillContext baseContext) {
        // 解析 entities 中的占位符（如 {step_1.item_name}）
        Map<String, String> resolvedEntities = new HashMap<>();
        for (Map.Entry<String, String> entry : step.getEntities().entrySet()) {
            PlaceholderResolveResult result = resolvePlaceholders(entry.getValue(), plan);
            if (result.isFailed()) {
                // 占位符解析失败，终止执行
                String errorMsg = String.format("步骤 %s 的参数 '%s' 解析失败：找不到 %s", step.getId(), entry.getKey(), result.failedPlaceholder);
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("[DEBUG] " + errorMsg);
                }
                return new BuildContextResult(null, errorMsg);
            }
            resolvedEntities.put(entry.getKey(), result.resolvedValue);
        }
        return new BuildContextResult(new SkillContext(baseContext.getPlayer(), step.getAction(), resolvedEntities), null);
    }

    /**
     * 解析占位符（如 {step_1.item_name}）
     * 解析失败时返回失败信息，而不是保留原占位符
     */
    @SuppressWarnings("unchecked")
    private PlaceholderResolveResult resolvePlaceholders(String value, TaskPlan plan) {
        if (value == null || !value.contains("{")) {
            return new PlaceholderResolveResult(value, null);
        }

        // 匹配 {step_xxx.field} 格式的占位符
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{(step_\\w+)\\.(\\w+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(value);

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String stepId = matcher.group(1);
            String fieldName = matcher.group(2);
            String placeholder = matcher.group(0); // 完整的占位符，如 {step_1.item_name}

            // 从 plan.context 中获取步骤结果
            Object stepResult = plan.getContext().get(stepId);
            if (stepResult instanceof SkillResult skillResult && skillResult.getData() instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) skillResult.getData();
                Object fieldValue = data.get(fieldName);
                if (fieldValue != null) {
                    matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(fieldValue.toString()));
                    continue;
                }
            }
            // 占位符解析失败，返回失败信息
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().warning("[DEBUG] 占位符解析失败：" + placeholder + " (步骤 " + stepId + " 不存在或没有字段 " + fieldName + ")");
            }
            return new PlaceholderResolveResult(null, placeholder);
        }
        matcher.appendTail(sb);
        return new PlaceholderResolveResult(sb.toString(), null);
    }

    /**
     * 汇总所有步骤的结果，使用 LLMAnalysisService 进行最终分析
     * <p>
     * 结果格式结构化，明确标记每个步骤的执行状态（SUCCESS/FAILURE/SKIPPED）
     */
    private CompletableFuture<SkillResult> synthesizeResults(TaskPlan plan, SkillContext baseContext, Deque<ConversationManager.Message> history, String userMessage) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 所有步骤执行完成，开始分析结果...");
        }

        // 构建统一的分析摘要
        AnalysisSummary summary = new AnalysisSummary().userMessage(userMessage).taskGoal(plan.getGoal());

        for (Map.Entry<String, Object> entry : plan.getContext().entrySet()) {
            String stepId = entry.getKey();
            Object result = entry.getValue();

            if (result instanceof SkillResult skillResult) {
                if (skillResult.isSuccess()) {
                    summary.addResult(stepId, "SUCCESS", skillResult.getMessage());
                } else {
                    String msg = skillResult.getMessage();
                    if (msg != null && (msg.startsWith("[依赖未满足]") || msg.startsWith("[参数解析失败]"))) {
                        summary.addResult(stepId, "SKIPPED", msg);
                    } else {
                        summary.addResult(stepId, "FAILURE", msg);
                    }
                }
            } else {
                summary.addResult(stepId, "UNKNOWN", String.valueOf(result));
            }
        }

        // 统计
        int success = 0, failure = 0, skipped = 0;
        for (var r : summary.getResults()) {
            switch (r.status()) {
                case "SUCCESS" -> success++;
                case "SKIPPED" -> skipped++;
                default -> failure++;
            }
        }
        summary.statistics(success, failure, skipped);

        return analysisService.analyzeResult(summary, baseContext, history);
    }
}
