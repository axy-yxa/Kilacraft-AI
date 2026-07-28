package com.zm.kilacraftAI.service.watch;

import java.util.Objects;

/**
 * 条件判定器：按 {@link ProbeValue.Type} 分流到对应操作符求值。
 *
 * <p>操作符语义：
 * <ul>
 *   <li>NUMBER：{@code greater_than} / {@code greater_than_or_equal} / {@code less_than} /
 *       {@code less_than_or_equal} / {@code equal} / {@code not_equal}</li>
 *   <li>BOOLEAN：只认 true（false 永不触发——监听 boolean 的意义就是等它变 true）</li>
 *   <li>STRING：{@code equal} / {@code not_equal} / {@code contains}</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-07-22
 */
public final class ConditionEvaluator {

    public static final String OP_GREATER_THAN = "greater_than";
    public static final String OP_GREATER_OR_EQUAL = "greater_than_or_equal";
    public static final String OP_LESS_THAN = "less_than";
    public static final String OP_LESS_OR_EQUAL = "less_than_or_equal";
    public static final String OP_EQUAL = "equal";
    public static final String OP_NOT_EQUAL = "not_equal";
    public static final String OP_CONTAINS = "contains";

    private ConditionEvaluator() {
    }

    /**
     * 判定监听取值是否满足条件。
     *
     * @param value     监听采样值
     * @param operator  操作符
     * @param threshold 阈值（由 WatchSkill 从 LLM 填入的字符串解析为对应类型）
     * @return true = 条件满足（应触发通知）
     */
    public static boolean test(ProbeValue value, String operator, String threshold) {
        Objects.requireNonNull(value, "value");
        return switch (value.type()) {
            case NUMBER -> testNumber(value.asNumber(), operator, parseDouble(threshold));
            case BOOLEAN -> value.asBoolean();
            case STRING -> testString(value.asString(), operator, threshold);
        };
    }

    private static boolean testNumber(double actual, String operator, double threshold) {
        return switch (operator != null ? operator : "") {
            case OP_GREATER_THAN -> actual > threshold;
            case OP_GREATER_OR_EQUAL -> actual >= threshold;
            case OP_LESS_THAN -> actual < threshold;
            case OP_LESS_OR_EQUAL -> actual <= threshold;
            case OP_EQUAL -> Math.abs(actual - threshold) < 0.0001;
            case OP_NOT_EQUAL -> Math.abs(actual - threshold) >= 0.0001;
            default -> false;
        };
    }

    private static boolean testString(String actual, String operator, String threshold) {
        return switch (operator != null ? operator : "") {
            case OP_EQUAL -> actual.equalsIgnoreCase(threshold);
            case OP_NOT_EQUAL -> !actual.equalsIgnoreCase(threshold);
            case OP_CONTAINS -> actual.toLowerCase().contains(threshold.toLowerCase());
            default -> false;
        };
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
