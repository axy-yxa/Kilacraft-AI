package com.zm.kilacraftAI.skills.framework.task;

import com.zm.kilacraftAI.config.I18nService;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 二次分析结果摘要 - 统一单意图和多步骤任务的结构化格式
 *
 * <p>确保不同入口（单意图、多步骤任务）产生的分析摘要格式一致，
 * 便于 LLM 理解和知识库关键词提取</p>
 *
 * <h3>统一格式示例：</h3>
 * <pre>
 * [用户输入]
 * 我手上的东西全球市场有人卖吗？多少钱？
 *
 * [执行结果]
 * - step_1: [SUCCESS] 物品：钻石
 * - step_2: [SUCCESS] 钻石有在售，库存2个
 *
 * [统计] 成功: 2, 失败: 0, 跳过: 0
 * </pre>
 *
 * @author Zm_Mmm
 * @since 2026-04-08
 */
public class AnalysisSummary {

    public static final String MARKER_USER_INPUT = "[用户输入]";
    public static final String MARKER_RESULTS = "[执行结果]";
    public static final String MARKER_STATS = "[统计]";
    public static final String MARKER_TASK_GOAL = "[任务目标]";

    private String userMessage;
    private String taskGoal;
    @Getter
    private final List<StepResult> results = new ArrayList<>();
    private int successCount;
    private int failureCount;
    private int skippedCount;

    public AnalysisSummary userMessage(String userMessage) {
        this.userMessage = userMessage;
        return this;
    }

    public AnalysisSummary taskGoal(String taskGoal) {
        this.taskGoal = taskGoal;
        return this;
    }

    /**
     * 添加步骤结果（多步骤任务场景）
     */
    public AnalysisSummary addResult(String stepId, String status, String message) {
        this.results.add(new StepResult(stepId, status, message));
        return this;
    }

    /**
     * 添加结果（单意图场景，无步骤ID）
     */
    public AnalysisSummary addResult(String status, String message) {
        return addResult(null, status, message);
    }

    public AnalysisSummary statistics(int success, int failure, int skipped) {
        this.successCount = success;
        this.failureCount = failure;
        this.skippedCount = skipped;
        return this;
    }

    /**
     * 构建统一的提示词格式（发送给 LLM 二次分析）
     */
    public String buildPrompt() {
        StringBuilder sb = new StringBuilder();

        if (userMessage != null && !userMessage.isEmpty()) {
            sb.append(I18nService.tr(MARKER_USER_INPUT)).append("\n");
            sb.append(userMessage).append("\n\n");
        }

        if (taskGoal != null && !taskGoal.isEmpty()) {
            sb.append(I18nService.tr(MARKER_TASK_GOAL)).append("\n");
            sb.append(taskGoal).append("\n\n");
        }

        sb.append(I18nService.tr(MARKER_RESULTS)).append("\n");
        for (StepResult result : results) {
            sb.append("- ");
            if (result.stepId() != null) {
                sb.append(result.stepId()).append(": ");
            }
            sb.append("[").append(result.status()).append("] ");
            sb.append(result.message()).append("\n");
        }

        sb.append("\n").append(I18nService.tr(MARKER_STATS)).append(" ").append(I18nService.tr("成功")).append(": ").append(successCount).append(", ").append(I18nService.tr("失败")).append(": ").append(failureCount).append(", ").append(I18nService.tr("跳过")).append(": ").append(skippedCount);

        return sb.toString();
    }

    /**
     * 步骤结果
     */
    public record StepResult(String stepId, String status, String message) {
    }
}
