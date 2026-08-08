package com.zm.kilacraftAI.common.util;

/**
 * LLM 错误/正常响应的统一区分工具。
 *
 * <h3>必须遵守的不变量</h3>
 * <p>所有 LLM 错误返回<b>必须</b>经 {@link #errorResponse(String)} 构造（统一以 {@code §c} 开头）。
 * 正常 LLM 自然语言回复不会以 {@code §c} 开头。</p>
 * <p>违反此不变量会导致：历史污染（错误串被当作 AI 回复写进对话历史）、误导性诊断日志（把错误串当 JSON 解析）。</p>
 * <p><b>新增任何 LLM 错误返回路径时，务必使用 {@link #errorResponse(String)}。</b></p>
 *
 * @author Zm_Mmm
 * @since 2026-06-20
 */
public final class LLMResponseUtil {

    public static final String ERROR_PREFIX = "§c";

    private LLMResponseUtil() {
    }

    /**
     * 构造 §c 前缀错误字符串；所有 LLM 错误返回必须经此方法。
     */
    public static String errorResponse(String message) {
        return ERROR_PREFIX + message;
    }

    /**
     * null 安全的 §c 前缀判定。
     */
    public static boolean isErrorResponse(String response) {
        return response != null && response.startsWith(ERROR_PREFIX);
    }
}
