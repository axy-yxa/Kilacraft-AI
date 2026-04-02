package com.zm.kilacraftAI.skills.framework.task;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.handler.AIResponseHandler;
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

    private final KilacraftAI plugin = KilacraftAI.getInstance();

    /**
     * 执行任务计划
     *
     * @param plan        任务计划
     * @param baseContext 基础上下文
     * @return 最终执行结果
     */
    public CompletableFuture<SkillResult> executeTask(TaskPlan plan, SkillContext baseContext) {
        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 共 " + plan.getStepCount() + " 个步骤");
        }

        // 多步骤任务：先进行拓扑排序
        List<TaskPlan.TaskStep> sortedSteps = topologicalSort(plan);
        if (sortedSteps == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("任务计划存在循环依赖，无法执行"));
        }

        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 执行顺序：" + sortedSteps.size() + " 步");
        }

        // 递归执行所有步骤
        return executeSteps(plan, sortedSteps, 0, baseContext);
    }


    /**
     * 递归执行步骤列表
     *
     * @param plan        任务计划
     * @param sortedSteps 已排序的步骤列表
     * @param stepIndex   当前执行的步骤索引
     * @param baseContext 基础上下文
     * @return 最终结果
     */
    private CompletableFuture<SkillResult> executeSteps(TaskPlan plan, List<TaskPlan.TaskStep> sortedSteps, int stepIndex, SkillContext baseContext) {

        if (stepIndex >= sortedSteps.size()) {
            // 所有步骤执行完成，进行最终分析
            return synthesizeResults(plan, baseContext);
        }

        TaskPlan.TaskStep currentStep = sortedSteps.get(stepIndex);

        // 检查依赖是否已满足
        if (!checkDependencies(plan, currentStep)) {
            return CompletableFuture.completedFuture(SkillResult.failure("步骤依赖未满足：" + currentStep.getId()));
        }

        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 执行步骤 [" + (stepIndex + 1) + "/" + sortedSteps.size() + "]: " + currentStep.getId() + " - " + currentStep.getAction());
        }

        // 创建技能意图
        SkillIntent intent = new SkillIntent(currentStep.getSkillName(), currentStep.getAction(), currentStep.getEntities(), 1.0, plan.getGoal());

        // 构建步骤上下文
        SkillContext stepContext = buildStepContext(currentStep, baseContext);

        // 执行技能
        return skillManager.executeSkillByIntent(intent, stepContext).thenCompose(result -> {
            // 保存结果到上下文
            plan.getContext().put(currentStep.getId(), result);

            // 继续执行下一步
            return executeSteps(plan, sortedSteps, stepIndex + 1, baseContext);
        });
    }

    /**
     * 拓扑排序 - 根据依赖关系确定执行顺序
     * 使用 DFS 实现
     *
     * @return 排序后的步骤列表，如果存在循环依赖则返回 null
     */
    private List<TaskPlan.TaskStep> topologicalSort(TaskPlan plan) {
        Map<String, TaskPlan.TaskStep> stepMap = new HashMap<>();
        for (TaskPlan.TaskStep step : plan.getSteps()) {
            stepMap.put(step.getId(), step);
        }

        List<TaskPlan.TaskStep> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>(); // 用于检测循环依赖

        // DFS 遍历所有节点
        for (TaskPlan.TaskStep step : plan.getSteps()) {
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
    private boolean visit(TaskPlan.TaskStep step, Map<String, TaskPlan.TaskStep> stepMap, Set<String> visited, Set<String> visiting, List<TaskPlan.TaskStep> result) {
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
            TaskPlan.TaskStep dependency = stepMap.get(dependencyId);
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
     */
    private boolean checkDependencies(TaskPlan plan, TaskPlan.TaskStep step) {
        for (String dependencyId : step.getDependsOn()) {
            if (!plan.getContext().containsKey(dependencyId)) {
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("[DEBUG] 依赖未满足：" + dependencyId);
                }
                return false;
            }
        }
        return true;
    }

    /**
     * 构建步骤上下文
     */
    private SkillContext buildStepContext(TaskPlan.TaskStep step, SkillContext baseContext) {
        // 直接使用步骤的 action 和 entities
        return new SkillContext(baseContext.getPlayer(), step.getAction(), step.getEntities());
    }

    /**
     * 汇总所有步骤的结果，使用 LLM 进行最终分析
     */
    private CompletableFuture<SkillResult> synthesizeResults(TaskPlan plan, SkillContext baseContext) {
        // 构建结果摘要
        StringBuilder summary = new StringBuilder();
        summary.append("任务目标：").append(plan.getGoal()).append("\n\n");
        summary.append("执行结果：\n");

        for (Map.Entry<String, Object> entry : plan.getContext().entrySet()) {
            String stepId = entry.getKey();
            Object result = entry.getValue();

            summary.append("- ").append(stepId).append(": ");
            if (result instanceof SkillResult) {
                summary.append(((SkillResult) result).getMessage());
            } else {
                summary.append(result);
            }
            summary.append("\n");
        }

        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("[DEBUG] 所有步骤执行完成，开始分析结果...");
        }

        // 使用 LLM 分析结果
        return analyzeWithLLM(summary.toString(), baseContext);
    }

    /**
     * 使用 LLM 分析执行结果并生成最终回复
     */
    private CompletableFuture<SkillResult> analyzeWithLLM(String resultsSummary, SkillContext baseContext) {
        KilacraftAI plugin = KilacraftAI.getInstance();
        var deepSeekAPI = plugin.getDeepSeekAPI();

        // 创建一个简单的 Handler 来捕获响应
        String playerName = baseContext.getPlayer() != null ? baseContext.getPlayer().getName() : "Console";
        java.util.concurrent.CompletableFuture<String> responseFuture = new java.util.concurrent.CompletableFuture<>();

        AIResponseHandler handler = new AIResponseHandler() {
            @Override
            public UUID getPlayerId() {
                return null;
            }

            @Override
            public String getPlayerName() {
                return playerName;
            }

            @Override
            public void showResponse(String response) {
                responseFuture.complete(response);
            }

            @Override
            public void showStreamChunk(String chunk, String currentMessage) {
            }

            @Override
            public void handleError(String errorMessage) {
                responseFuture.completeExceptionally(new RuntimeException(errorMessage));
            }

            @Override
            public boolean isStreamOutputEnabled() {
                return false;
            }
        };

        // 使用配置文件中的提示词
        String baseAnalysisPrompt = plugin.getConfigManager().getAgentAnalysisPrompt();
        String systemPrompt = plugin.getConfigManager().getAgentSystemPrompt();
        
        // 替换占位符 {results}
        String analysisPrompt = baseAnalysisPrompt.replace("{results}", resultsSummary);
        
        deepSeekAPI.processRequestWithCustomSystemPrompt(analysisPrompt, playerName, null, handler, systemPrompt, false, false);
        return responseFuture.thenApply(SkillResult::success);
    }
}
