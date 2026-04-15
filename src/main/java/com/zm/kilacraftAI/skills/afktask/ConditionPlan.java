package com.zm.kilacraftAI.skills.afktask;

import lombok.Getter;

/**
 * 条件计划数据结构
 *
 * <p>用于定义CUSTOM挂机任务的条件评估规则。</p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *   <li>单条件限制：只支持一个数值条件，不支持多条件组合（AND/OR）</li>
 *   <li>通用性：通过Skill返回结果 + 字段提取 + 数值比较实现</li>
 *   <li>类型安全：所有数值统一使用double进行比较</li>
 * </ul>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>血量监视：当血量低于50%时通知</li>
 *   <li>等级监视：当等级达到30时通知</li>
 *   <li>经济余额监视：当余额低于1000时通知</li>
 *   <li>任意Skill返回值监视：只要返回值包含数值字段</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-13
 */
@Getter
public class ConditionPlan {

    /**
     * 条件技能名称（来自可用技能列表）
     */
    private final String conditionSkill;

    /**
     * 条件动作名称（该技能的查询动作）
     */
    private final String conditionAction;

    /**
     * 结果字段路径（从Skill返回结果中提取的字段名）
     *
     * <p>必须是该动作实际返回的数据字段名，参考动作描述中的 data 字段说明。</p>
     */
    private final String resultPath;

    /**
     * 比较操作符
     *
     * <p>支持的操作符：</p>
     * <ul>
     *   <li>less_than - 小于 (&lt;)</li>
     *   <li>less_than_or_equal - 小于等于 (&lt;=)</li>
     *   <li>greater_than - 大于 (&gt;)</li>
     *   <li>greater_than_or_equal - 大于等于 (&gt;=)</li>
     *   <li>equal - 等于 (==)</li>
     *   <li>not_equal - 不等于 (!=)</li>
     * </ul>
     */
    private final String operator;

    /**
     * 阈值（用于比较的数值）
     */
    private final double threshold;

    /**
     * 构造条件计划
     *
     * @param conditionSkill  条件技能名称
     * @param conditionAction 条件动作名称
     * @param resultPath      结果字段路径
     * @param operator        比较操作符
     * @param threshold       阈值
     */
    public ConditionPlan(String conditionSkill, String conditionAction, String resultPath, String operator, double threshold) {
        this.conditionSkill = conditionSkill;
        this.conditionAction = conditionAction;
        this.resultPath = resultPath;
        this.operator = operator;
        this.threshold = threshold;
    }

    /**
     * 验证操作符是否合法
     *
     * @return true 如果操作符合法
     */
    public boolean isValidOperator() {
        return operator != null && switch (operator) {
            case "less_than", "less_than_or_equal", "greater_than", "greater_than_or_equal", "equal", "not_equal" ->
                    true;
            default -> false;
        };
    }

    /**
     * 获取操作符的可读描述
     *
     * @return 操作符的中文描述
     */
    public String getOperatorDescription() {
        return switch (operator != null ? operator : "") {
            case "less_than" -> "小于";
            case "less_than_or_equal" -> "小于等于";
            case "greater_than" -> "大于";
            case "greater_than_or_equal" -> "大于等于";
            case "equal" -> "等于";
            case "not_equal" -> "不等于";
            default -> "未知";
        };
    }

    @Override
    public String toString() {
        return String.format("%s.%s → %s %s %.1f", conditionSkill, conditionAction, resultPath, getOperatorDescription(), threshold);
    }
}
