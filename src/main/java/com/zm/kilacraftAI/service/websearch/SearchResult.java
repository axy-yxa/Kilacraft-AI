package com.zm.kilacraftAI.service.websearch;

/**
 * 统一搜索结果记录
 *
 * <p>各供应商的 JSON 响应经各自的 Provider 实现映射为此 record，
 * 屏蔽上游字段差异（如智谱用 content、Tavily 用 content、Brave 用 description）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public record SearchResult(String title, String url, String snippet) {
    /**
     * 截取安全长度的摘要，避免单条结果过长占用 LLM 上下文
     */
    public SearchResult withTruncatedSnippet(int maxLen) {
        if (snippet == null || snippet.length() <= maxLen) {
            return this;
        }
        return new SearchResult(title, url, snippet.substring(0, maxLen) + "...");
    }
}
