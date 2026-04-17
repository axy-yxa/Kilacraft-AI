package com.zm.kilacraftAI.skills.framework.task;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务计划 - LLM 将复杂任务分解为有序的步骤
 * <p>
 * 使用示例（玩家输入："帮我看看我的余额是多少，够不够去市场买两个钻石"）：
 * LLM 返回的 JSON：
 * {
 * "goal": "检查余额是否足够购买 2 个钻石",
 * "steps": [
 * {
 * "id": "step_1",
 * "skill_name": "MarketQuerySkill",
 * "action": "query_balance",
 * "entities": {},
 * "depends_on": []
 * },
 * {
 * "id": "step_2",
 * "skill_name": "MarketQuerySkill",
 * "action": "query_price",
 * "entities": {"amount": "2", "item": "钻石"},
 * "depends_on": []
 * },
 * {
 * "id": "step_3",
 * "skill_name": "MarketQuerySkill",
 * "action": "analyze_affordability",
 * "entities": {},
 * "depends_on": ["step_1", "step_2"]
 * }
 * ]
 * }
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

    public TaskPlan(String goal) {
        this.goal = goal;
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
