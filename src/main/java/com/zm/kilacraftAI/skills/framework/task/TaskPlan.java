package com.zm.kilacraftAI.skills.framework.task;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务计划 - LLM 将复杂任务分解为有序的步骤
 *
 * @author Zm_Mmm
 * @since 2026-04-02
 */
@Getter
public class TaskPlan {

    /**
     * 总体目标（从 LLM 识别）
     */
    private final String goal;

    /**
     * 有序的步骤列表
     */
    private final List<TaskStep> steps;

    /**
     * 执行上下文（用于在步骤间传递数据）
     */
    private final Map<String, Object> context;

    /**
     * 识别理由（LLM reasoning 字段原文）。明确说明任务某部分因系统无对应能力而未达成时，
     * 经 {@code AnalysisSummary} 注入二次分析提示词，供二次分析如实转达未执行的部分。
     */
    private final String reasoning;

    public TaskPlan(String goal) {
        this(goal, null);
    }

    public TaskPlan(String goal, String reasoning) {
        this.goal = goal;
        this.reasoning = reasoning;
        this.steps = new ArrayList<>();
        this.context = new HashMap<>();
    }

    /**
     * 添加步骤
     */
    public void addStep(TaskStep step) {
        this.steps.add(step);
    }

    /**
     * 获取步骤数量
     */
    public int getStepCount() {
        return this.steps.size();
    }

    /**
     * 判断是否是多步骤任务
     */
    public boolean isMultiStep() {
        return this.getStepCount() > 1;
    }

    /**
     * 检查是否有依赖关系
     */
    public boolean hasDependencies() {
        for (TaskStep step : this.steps) {
            if (!step.getDependsOn().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
