package com.zm.kilacraftAI.common.util;

import com.google.gson.JsonObject;

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
}
