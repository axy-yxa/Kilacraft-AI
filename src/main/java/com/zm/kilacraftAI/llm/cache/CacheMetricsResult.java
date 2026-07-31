package com.zm.kilacraftAI.llm.cache;

import lombok.Getter;

/**
 * 缓存指标解析结果，由 {@link CacheMetricsParser} 产出。
 * supported 表示供应商是否报告了缓存 token 数据。
 *
 * @author Zm_Mmm
 * @since 2026-07-30
 */
@Getter
public final class CacheMetricsResult {

    private final boolean supported;
    private final long inputTokens;
    private final long outputTokens;
    private final long totalTokens;
    private final long cacheReadTokens;

    CacheMetricsResult(boolean supported, long inputTokens, long outputTokens, long totalTokens, long cacheReadTokens) {
        this.supported = supported;
        this.inputTokens = Math.max(0, inputTokens);
        this.outputTokens = Math.max(0, outputTokens);
        this.totalTokens = Math.max(0, totalTokens);
        this.cacheReadTokens = Math.max(0, cacheReadTokens);
    }

    static CacheMetricsResult supported(long inputTokens, long outputTokens, long totalTokens, long cacheReadTokens) {
        return new CacheMetricsResult(true, inputTokens, outputTokens, totalTokens, cacheReadTokens);
    }

    static CacheMetricsResult unreported(long inputTokens, long outputTokens, long totalTokens) {
        return new CacheMetricsResult(false, inputTokens, outputTokens, totalTokens, 0);
    }

    static CacheMetricsResult unsupported() {
        return unreported(0, 0, 0);
    }
}
