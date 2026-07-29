package com.zm.kilacraftAI.skills.framework;

import java.util.Map;

/**
 * Skill 参数提取与类型转换工具。
 *
 * <p>所有方法纯静态、无状态、并发安全，可直接被内置 skill 与第三方 SPI skill 复用。
 * 设计准则：</p>
 * <ul>
 *   <li><b>只做提取 + 转换，不做 failure/needInfo 决策</b>——参数缺失返回 null（{@code getString*}
 *       系列）或默认值（类型转换系列），由调用方按业务语义决定返回 {@link SkillResult#failure}
 *       还是 {@link SkillResult#needInfo}。</li>
 *   <li><b>绝不抛异常</b>：解析失败一律返回默认值，避免 skill 内散落 try-catch。</li>
 *   <li>提供 {@link SkillContext} 与 {@code Map<String,String>} 两套重载——前者用于直接持有
 *       context 的入口，后者用于已把 entities 传给内部 handler 的场景。</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-07-28
 */
public final class SkillEntityHelper {

    private SkillEntityHelper() {
    }

    /**
     * 从 context 取原始字符串参数，缺失或空白返回 null。
     *
     * <p>调用方据此判定 failure（结构性必需参数）或 needInfo（需用户补值）。</p>
     *
     * @param context 执行上下文
     * @param key     参数键
     * @return 参数值；不存在或空白返回 null
     */
    public static String getString(SkillContext context, String key) {
        return getString(context.getEntities(), key);
    }

    /**
     * 从 entities 取原始字符串参数，缺失或空白返回 null。
     *
     * @param entities 参数 map
     * @param key      参数键
     * @return 参数值；不存在或空白返回 null
     */
    public static String getString(Map<String, String> entities, String key) {
        String v = entities.get(key);
        return (v == null || v.isBlank()) ? null : v;
    }

    /**
     * 从 context 取字符串参数，缺失返回默认值（不区分空白）。
     *
     * @param context      执行上下文
     * @param key          参数键
     * @param defaultValue 缺失时的默认值
     * @return 参数值；不存在返回 defaultValue
     */
    public static String getString(SkillContext context, String key, String defaultValue) {
        return getString(context.getEntities(), key, defaultValue);
    }

    /**
     * 从 entities 取字符串参数，缺失返回默认值（不区分空白）。
     *
     * @param entities     参数 map
     * @param key          参数键
     * @param defaultValue 缺失时的默认值
     * @return 参数值；不存在返回 defaultValue
     */
    public static String getString(Map<String, String> entities, String key, String defaultValue) {
        String v = entities.get(key);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    /**
     * 从 context 取 int 参数。
     *
     * @param context      执行上下文
     * @param key          参数键
     * @param defaultValue 缺失或解析失败时的默认值
     * @return 解析后的 int；缺失或格式非法返回 defaultValue
     */
    public static int getInt(SkillContext context, String key, int defaultValue) {
        return getInt(context.getEntities(), key, defaultValue);
    }

    /**
     * 从 entities 取 int 参数。
     *
     * @param entities     参数 map
     * @param key          参数键
     * @param defaultValue 缺失或解析失败时的默认值
     * @return 解析后的 int；缺失或格式非法返回 defaultValue
     */
    public static int getInt(Map<String, String> entities, String key, int defaultValue) {
        String v = entities.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 从 context 取 int 参数并裁剪到 [min, max] 区间。
     *
     * <p>适用于数值（整数）类参数的合法范围约束（如数量 1-100、秒数 1-60）。</p>
     *
     * @param context      执行上下文
     * @param key          参数键
     * @param defaultValue 缺失或解析失败时的默认值
     * @param min          最小值（含）
     * @param max          最大值（含）
     * @return 解析后裁剪到区间的 int；缺失/非法返回 defaultValue（不裁剪）
     */
    public static int getIntClamped(SkillContext context, String key, int defaultValue, int min, int max) {
        return getIntClamped(context.getEntities(), key, defaultValue, min, max);
    }

    /**
     * 从 entities 取 int 参数并裁剪到 [min, max] 区间。
     *
     * @param entities     参数 map
     * @param key          参数键
     * @param defaultValue 缺失或解析失败时的默认值
     * @param min          最小值（含）
     * @param max          最大值（含）
     * @return 解析后裁剪到区间的 int；缺失/非法返回 defaultValue（不裁剪）
     */
    public static int getIntClamped(Map<String, String> entities, String key, int defaultValue, int min, int max) {
        String v = entities.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(v.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 从 context 取 long 参数。
     *
     * @param context      执行上下文
     * @param key          参数键
     * @param defaultValue 缺失或解析失败时的默认值
     * @return 解析后的 long；缺失或格式非法返回 defaultValue
     */
    public static long getLong(SkillContext context, String key, long defaultValue) {
        return getLong(context.getEntities(), key, defaultValue);
    }

    /**
     * 从 entities 取 long 参数。
     *
     * @param entities     参数 map
     * @param key          参数键
     * @param defaultValue 缺失或解析失败时的默认值
     * @return 解析后的 long；缺失或格式非法返回 defaultValue
     */
    public static long getLong(Map<String, String> entities, String key, long defaultValue) {
        String v = entities.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 从 context 取 double 参数。
     *
     * @param context      执行上下文
     * @param key          参数键
     * @param defaultValue 缺失或解析失败时的默认值
     * @return 解析后的 double；缺失或格式非法返回 defaultValue
     */
    public static double getDouble(SkillContext context, String key, double defaultValue) {
        return getDouble(context.getEntities(), key, defaultValue);
    }

    /**
     * 从 entities 取 double 参数。
     *
     * @param entities     参数 map
     * @param key          参数键
     * @param defaultValue 缺失或解析失败时的默认值
     * @return 解析后的 double；缺失或格式非法返回 defaultValue
     */
    public static double getDouble(Map<String, String> entities, String key, double defaultValue) {
        String v = entities.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 从 context 取 boolean 参数，宽松解析。
     *
     * <p>识别 {@code true/false}（大小写不敏感）、{@code 1/0}、{@code yes/no}、{@code on/off}。
     * 其它值或缺失返回 defaultValue。</p>
     *
     * @param context      执行上下文
     * @param key          参数键
     * @param defaultValue 缺失或无法识别时的默认值
     * @return 解析后的 boolean
     */
    public static boolean getBoolean(SkillContext context, String key, boolean defaultValue) {
        return getBoolean(context.getEntities(), key, defaultValue);
    }

    /**
     * 从 entities 取 boolean 参数，宽松解析。
     *
     * <p>识别 {@code true/false}（大小写不敏感）、{@code 1/0}、{@code yes/no}、{@code on/off}。
     * 其它值或缺失返回 defaultValue。</p>
     *
     * @param entities     参数 map
     * @param key          参数键
     * @param defaultValue 缺失或无法识别时的默认值
     * @return 解析后的 boolean
     */
    public static boolean getBoolean(Map<String, String> entities, String key, boolean defaultValue) {
        String v = entities.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        String s = v.trim().toLowerCase();
        return switch (s) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> defaultValue;
        };
    }
}
