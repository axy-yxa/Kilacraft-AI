package com.zm.kilacraftAI.skills.afktask;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 通用条件评估器
 *
 * <p>从Skill返回结果中提取指定字段，并进行数值比较。</p>
 *
 * <h3>核心流程：</h3>
 * <ol>
 *   <li>调用指定的Skill执行动作</li>
 *   <li>从返回结果中提取result_path字段</li>
 *   <li>将字段值转换为double</li>
 *   <li>与threshold进行比较</li>
 *   <li>返回比较结果（true=条件满足）</li>
 * </ol>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li>通用性：适用于任何返回数值型结果的Skill</li>
 *   <li>三态返回：MET（条件满足）、NOT_MET（正常评估但不满足）、FAILED（评估过程出错）</li>
 *   <li>安全性：执行超时保护（5秒）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-13
 */
public class ConditionEvaluator {

    /**
     * 条件评估结果（三态 + 实际值）
     * <ul>
     *   <li>MET：条件满足，应触发回调或通知</li>
     *   <li>NOT_MET：条件正常评估但不满足，继续轮询</li>
     *   <li>FAILED：评估过程出错（Skill找不到/超时/字段提取失败等），属于配置错误</li>
     * </ul>
     */
    public record EvaluationResult(Status status, Double actualValue) {

        public enum Status { MET, NOT_MET, FAILED }

        /** 兼容旧调用：仅关注状态 */
        public boolean isMet() { return status == Status.MET; }
        public boolean isFailed() { return status == Status.FAILED; }

        /** 快捷构造方法 */
        public static EvaluationResult met(double actualValue) { return new EvaluationResult(Status.MET, actualValue); }
        public static EvaluationResult notMet(double actualValue) { return new EvaluationResult(Status.NOT_MET, actualValue); }
        public static EvaluationResult failed() { return new EvaluationResult(Status.FAILED, null); }
    }

    private static final long EXECUTION_TIMEOUT_SECONDS = 5;

    /**
     * 评估条件是否满足
     *
     * @param conditionPlan 条件计划
     * @param player        玩家对象（用于构建SkillContext）
     * @return 评估结果：MET（条件满足）、NOT_MET（正常评估但不满足）、FAILED（评估过程出错）
     */
    public static EvaluationResult evaluate(ConditionPlan conditionPlan, Player player) {
        try {
            // 1. 获取Skill实例
            Skill skill = KilacraftAI.getInstance().getSkillManager().getSkill(conditionPlan.getConditionSkill());
            if (skill == null) {
                PluginLogger.warn("条件评估", "找不到Skill: " + conditionPlan.getConditionSkill());
                return EvaluationResult.failed();
            }

            // 2. 构建执行上下文（使用条件计划中的参数）
            SkillContext context = new SkillContext(player, conditionPlan.getConditionAction(), conditionPlan.getConditionParams());

            // 3. 执行Skill（带超时保护）
            CompletableFuture<SkillResult> future = skill.execute(context);
            SkillResult result;
            try {
                result = future.get(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                PluginLogger.warn("条件评估", "Skill执行超时: " + conditionPlan.getConditionSkill() + "." + conditionPlan.getConditionAction());
                return EvaluationResult.failed();
            } catch (InterruptedException | ExecutionException e) {
                PluginLogger.warn("条件评估", "Skill执行异常: " + e.getMessage(), e);
                return EvaluationResult.failed();
            }

            // 4. 检查执行结果
            if (!result.isSuccess()) {
                PluginLogger.debug("条件评估", "Skill执行失败: " + result.getMessage());
                return EvaluationResult.failed();
            }

            // 5. 提取字段值
            Double value = extractDoubleValue(result, conditionPlan.getResultPath());
            if (value == null) {
                PluginLogger.debug("条件评估", "无法提取字段: " + conditionPlan.getResultPath());
                return EvaluationResult.failed();
            }

            // 6. 执行比较
            boolean meetsCondition = compare(value, conditionPlan.getOperator(), conditionPlan.getThreshold());

//            PluginLogger.debug("条件评估", conditionPlan.getConditionSkill() + "." + conditionPlan.getConditionAction() + " -> " + conditionPlan.getResultPath() + "=" + value + " " + conditionPlan.getOperatorDescription() + " " + conditionPlan.getThreshold() + " ? " + meetsCondition);

            return meetsCondition ? EvaluationResult.met(value) : EvaluationResult.notMet(value);

        } catch (Exception e) {
            PluginLogger.error("条件评估", "评估异常: " + e.getMessage(), e);
            return EvaluationResult.failed();
        }
    }

    /**
     * 从SkillResult中提取double值
     *
     * @param result     Skill执行结果
     * @param resultPath 字段路径
     * @return 提取的double值，失败返回null
     */
    private static Double extractDoubleValue(SkillResult result, String resultPath) {
        if (resultPath == null || resultPath.isEmpty()) {
            return null;
        }

        try {
            // 从result.getDataMap()中获取字段
            Map<String, Object> data = result.getDataMap();
            if (data == null || !data.containsKey(resultPath)) {
                return null;
            }

            Object value = data.get(resultPath);
            return convertToDouble(value);

        } catch (Exception e) {
            PluginLogger.debug("条件评估", "提取字段失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 将对象转换为double
     *
     * @param value 对象
     * @return double值，转换失败返回null
     */
    private static Double convertToDouble(Object value) {
        if (value == null) {
            return null;
        }

        try {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value instanceof Boolean) {
                // 布尔值转换：true → 1.0, false → 0.0，与 ConditionPlan 中布尔阈值转换一致
                return ((Boolean) value) ? 1.0 : 0.0;
            } else if (value instanceof String) {
                return Double.parseDouble((String) value);
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 执行数值比较
     *
     * @param actualValue 实际值
     * @param operator    操作符
     * @param threshold   阈值
     * @return 比较结果
     */
    private static boolean compare(double actualValue, String operator, double threshold) {
        return switch (operator != null ? operator : "") {
            case "less_than" -> actualValue < threshold;
            case "less_than_or_equal" -> actualValue <= threshold;
            case "greater_than" -> actualValue > threshold;
            case "greater_than_or_equal" -> actualValue >= threshold;
            case "equal" -> Math.abs(actualValue - threshold) < 0.0001;
            case "not_equal" -> Math.abs(actualValue - threshold) >= 0.0001;
            default -> false;
        };
    }
}
