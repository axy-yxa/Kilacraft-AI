package com.zm.kilacraftAI.skills.afktask;

import com.google.gson.annotations.SerializedName;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.skills.framework.task.TaskStep;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 挂机任务回调配置
 *
 * <p>定义挂机任务触发后需要执行的动作配置。</p>
 * <p><b>核心设计</b>：挂机任务触发后，执行的是一个完整的"多步骤任务链"，而非单个 Skill。</p>
 *
 * <h3>架构设计：</h3>
 * <pre>
 * 挂机任务触发 → 读取回调配置 → 构建 TaskPlan → TaskExecutor 执行 → LLM 二次分析 → 通知玩家
 * </pre>
 *
 * <h3>使用示例 1：单步骤回调</h3>
 * <pre>
 * {
 *   "callback_task": {
 *     "goal": "查询钻石价格",
 *     "steps": [
 *       {
 *         "id": "step_1",
 *         "skill_name": "MarketQuerySkill",
 *         "action": "query_price",
 *         "entities": {"item": "钻石"}
 *       }
 *     ]
 *   },
 *   "notify_target": "{creator}"
 * }
 * </pre>
 *
 * <h3>使用示例 2：多步骤回调（带依赖关系）</h3>
 * <pre>
 * {
 *   "callback_task": {
 *     "goal": "玩家A上线后购买钻石剑并传送",
 *     "steps": [
 *       {
 *         "id": "step_1",
 *         "skill_name": "MarketQuerySkill",
 *         "action": "buy_item",
 *         "entities": {"item": "钻石剑", "quantity": "1"}
 *       },
 *       {
 *         "id": "step_2",
 *         "skill_name": "MythicMobsSkill",
 *         "action": "check_health",
 *         "entities": {"player": "{creator}", "min_health": "20.0"}
 *       },
 *       {
 *         "id": "step_3",
 *         "skill_name": "MythicMobsSkill",
 *         "action": "teleport",
 *         "entities": {"player": "{creator}", "target": "{triggered_player}"},
 *         "depends_on": ["step_1", "step_2"]
 *       }
 *     ]
 *   },
 *   "notify_target": "{creator}"
 * }
 * </pre>
 *
 * @author Zm_Mmm
 * @since 2026-04-09
 */
@Setter
@Getter
public class AFKTaskCallback {

    /**
     * 回调任务配置（多步骤任务定义）
     * <p>当挂机任务触发时，会构建一个 TaskPlan 并交给 TaskExecutor 执行。</p>
     */
    @SerializedName("callback_task")
    private CallbackTask callbackTask;

    /**
     * 通知目标玩家（"{creator}" 表示任务创建者，或其他玩家名称）
     */
    @SerializedName("notify_target")
    private String notifyTarget;

    public AFKTaskCallback() {
    }

    /**
     * 回调任务配置
     * <p>本质上是一个简化版的 TaskPlan，用于挂机任务触发时构建完整的任务计划。</p>
     */
    @Setter
    @Getter
    public static class CallbackTask {

        /**
         * 任务目标（用于 LLM 二次分析）
         */
        private String goal;

        /**
         * 任务步骤列表
         */
        private List<CallbackStep> steps = new ArrayList<>();

        /**
         * 转换为完整的 TaskPlan 对象
         *
         * @return TaskPlan 实例，可直接交给 TaskExecutor 执行
         */
        public TaskPlan toTaskPlan() {
            TaskPlan plan = new TaskPlan(goal);

            for (CallbackStep step : steps) {
                TaskStep taskStep = new TaskStep(step.getId(), step.getSkillName(), step.getAction(), step.getEntities() != null ? step.getEntities() : new HashMap<>(), step.getDependsOn() != null ? step.getDependsOn() : new ArrayList<>());
                plan.addStep(taskStep);
            }

            return plan;
        }
    }

    /**
     * 回调步骤配置
     * <p>对应 TaskStep，用于定义回调任务中的每个步骤。</p>
     */
    @Setter
    @Getter
    public static class CallbackStep {

        /**
         * 步骤 ID（如 "step_1"）
         */
        private String id;

        /**
         * Skill 名称（如 "MarketQuerySkill"）
         */
        @SerializedName("skill_name")
        private String skillName;

        /**
         * Action 名称（如 "query_price"）
         */
        private String action;

        /**
         * 步骤参数
         */
        private Map<String, String> entities = new HashMap<>();

        /**
         * 依赖的步骤 ID 列表
         */
        @SerializedName("depends_on")
        private List<String> dependsOn = new ArrayList<>();

    }

    @Override
    public String toString() {
        return "AFKTaskCallback{" + "callbackTask=" + callbackTask + ", notifyTarget='" + notifyTarget + '\'' + '}';
    }
}
