package com.zm.kilacraftAI.common.util;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * JSON 安全取值工具类
 *
 * <p>从 JsonObject 中安全获取格式化后的值，key 不存在或类型不匹配时返回 "N/A"。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-22
 */
public final class JsonSafeGetUtil {

    private JsonSafeGetUtil() {
    }

    /**
     * 安全获取 double 值并格式化为一位小数
     */
    public static String fmtDouble(JsonObject obj, String key) {
        if (!obj.has(key)) return "N/A";
        try {
            return String.format("%.1f", obj.get(key).getAsDouble());
        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * 安全获取 int 值
     */
    public static String fmtInt(JsonObject obj, String key) {
        if (!obj.has(key)) return "N/A";
        try {
            return String.valueOf(obj.get(key).getAsInt());
        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * 安全获取 long 值并格式化为 MB
     */
    public static String fmtMemLong(JsonObject obj, String key) {
        if (!obj.has(key)) return "N/A";
        try {
            return (obj.get(key).getAsLong() / 1024 / 1024) + "MB";
        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * 尝试修复 LLM 输出的常见 JSON 格式错误
     * <p>
     * 修复范围：
     * <ol>
     *   <li>尾部逗号：{@code {"a":1,}  → {"a":1}}</li>
     *   <li>缺失闭合 }：补全花括号</li>
     *   <li>缺失闭合 ]：补全方括号</li>
     * </ol>
     * </p>
     *
     * @param json 可能不完整的 JSON 字符串
     * @return 修复后的 JSON 字符串
     */
    public static String repairJsonBraces(String json) {
        if (json == null || json.isEmpty()) return json;

        // 步骤 1: 使用栈追踪开符号顺序，同时重建字符串
        // - 缺失的闭合符号：记录到栈中，最后补全
        // - 多余的闭合符号（栈为空时遇到的 } 或 ]）：过滤掉
        // - 交叉嵌套（闭合符号与栈顶不匹配）：放弃修复，只做尾部逗号清理
        StringBuilder rebuilt = new StringBuilder(json.length());
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                escape = false;
                rebuilt.append(c);
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                rebuilt.append(c);
                continue;
            }
            if (c == '"') {
                inString = !inString;
                rebuilt.append(c);
                continue;
            }
            if (inString) {
                rebuilt.append(c);
                continue;
            }
            // 非字符串内的结构字符
            if (c == '{' || c == '[') {
                stack.push(c);
                rebuilt.append(c);
            } else if (c == '}' || c == ']') {
                if (stack.isEmpty()) {
                    // 多余的闭合符号，跳过不追加（过滤尾部多余的 } 或 ]）
                    continue;
                }
                char expected = c == '}' ? '{' : '[';
                if (stack.peek() == expected) {
                    stack.pop();
                    rebuilt.append(c);
                } else {
                    // 栈顶不匹配 → 交叉嵌套，无法安全修复
                    // 直接对原始 json 做尾部逗号清理后返回
                    return removeTrailingCommas(json);
                }
            } else {
                rebuilt.append(c);
            }
        }

        // 步骤 2: 补全栈中残留的未闭合符号
        if (!stack.isEmpty()) {
            StringBuilder suffix = new StringBuilder(stack.size());
            while (!stack.isEmpty()) {
                char open = stack.pop();
                suffix.append(open == '{' ? '}' : ']');
            }
            rebuilt.append(suffix);
        }

        // 步骤 3: 修复尾部逗号（在括号处理之后，确保能检测到补全产生的 ,}）
        return removeTrailingCommas(rebuilt.toString());
    }

    /**
     * 移除 } 或 ] 前面的多余逗号（LLM 常见错误）
     * 逐字符扫描以跳过字符串内的逗号
     */
    private static String removeTrailingCommas(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                escape = false;
                sb.append(c);
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                sb.append(c);
                continue;
            }
            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }
            if (inString) {
                sb.append(c);
                continue;
            }
            // 检测尾部逗号：逗号后面只有空白，然后是 } 或 ]
            if (c == ',') {
                int j = i + 1;
                while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
                    j++;
                }
                if (j < json.length() && (json.charAt(j) == '}' || json.charAt(j) == ']')) {
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

}
