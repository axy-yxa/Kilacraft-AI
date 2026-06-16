package com.zm.kilacraftAI.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 算术占位符求值工具
 *
 * <p>用于多步骤任务中"基于前置步骤返回值的派生参数"求值，例如：</p>
 * <ul>
 *   <li>"转三分之一余额" → amount = "{step_0.balance}/3" → 执行期求值为具体金额</li>
 *   <li>"血量比现在少5" → threshold = "{step_0.health}-5" → 创建任务时求值固化</li>
 * </ul>
 *
 * <h3>支持范围</h3>
 * <ul>
 *   <li>仅支持<b>单次二元运算</b>（+ - * /），含小数与负数</li>
 *   <li>不支持：复合表达式（如 {@code /2+100}）、括号、百分号、文本混算术</li>
 *   <li>安全性：纯正则 + 算术运算，不使用脚本引擎，无注入风险</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-06-15
 */
public final class ArithmeticUtil {

    private ArithmeticUtil() {
    }

    /**
     * 单次二元算术表达式正则：数字 运算符 数字（允许前后空白、小数、负数）
     */
    private static final Pattern BINARY = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");

    /**
     * 尝试对单次二元算术表达式求值。
     *
     * <p>调用时机：占位符（如 {@code {step_0.health}}）已被外层替换为具体数字之后。
     * 因此传入的字符串要么是裸算术（"20-5"），要么是非算术内容（纯数字 "15"、方块类型 "GRASS_BLOCK"、
     * 未解析的占位符 "{step_0.health}-5" 等）。</p>
     *
     * @param expr 待求值的字符串（占位符已替换）
     * @return 求值结果；表达式不含单次二元运算、含文字/未解析占位符/复合运算/除零时返回 null
     */
    public static Double tryEvalBinary(String expr) {
        if (expr == null || expr.isEmpty()) {
            return null;
        }
        Matcher m = BINARY.matcher(expr);
        if (!m.find()) {
            return null;
        }
        try {
            double a = Double.parseDouble(m.group(1));
            String op = m.group(2);
            double b = Double.parseDouble(m.group(3));
            // 除零保护：返回 null 让调用方按原值处理
            if ("/".equals(op) && b == 0) {
                return null;
            }
            return switch (op) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> a / b;
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 求值并格式化为字符串（最多 2 位小数、去尾零），不匹配时原样返回。
     *
     * <p>供 {@code TaskExecutor} 在占位符替换后对字段值做算术求值使用，
     * 保持与历史行为完全一致。</p>
     *
     * @param value 可能包含算术表达式的字符串（占位符已替换）
     * @return 求值后的格式化字符串；非算术表达式时原样返回
     */
    public static String evalAndFormat(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Double result = tryEvalBinary(value);
        if (result == null) {
            return value;
        }
        // 统一处理 ±0（含 -0.0）：result == 0.0 对 -0.0 同样成立，
        // 避免 "-0.00" 经去尾零后残留孤立的 "-"。
        if (result == 0.0) {
            return "0";
        }
        String formatted = String.format("%.2f", result);
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("\\.?0+$", "");
            if (formatted.isEmpty()) {
                formatted = "0";
            }
        }
        return formatted;
    }
}
