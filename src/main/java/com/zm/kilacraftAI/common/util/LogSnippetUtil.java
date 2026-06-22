package com.zm.kilacraftAI.common.util;

/**
 * 日志片段截断工具。
 *
 * <p>用于把（可能很长的、含换行的）LLM 响应/JSON 片段安全地拼进单行日志，
 * 统一各处「解析失败」WARN 的诊断输出格式，便于排查非预期 AI 输出。</p>
 *
 * @author Zm_Mmm
 * @since 2026-06-20
 */
public final class LogSnippetUtil {

    private LogSnippetUtil() {
    }

    /**
     * 把文本压成单行并截断到指定长度，超长附加 "..."。
     *
     * <p>空白（含换行）折叠为单个空格，避免日志被拆成多行难以检索。</p>
     *
     * @param text 原始文本（可为 null）
     * @param max  最大保留字符数
     * @return 单行截断后的文本；null 返回空串
     */
    public static String truncateForLog(String text, int max) {
        if (text == null) return "";
        String oneLine = text.replaceAll("\\s+", " ");
        return oneLine.length() > max ? oneLine.substring(0, max) + "..." : oneLine;
    }
}
