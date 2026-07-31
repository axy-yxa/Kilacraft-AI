package com.zm.kilacraftAI.llm.cache;

import lombok.Getter;

/**
 * 缓存指标解析结果，由 {@link CacheMetricsParser} 产出。
 * <p>
 * 包含是否支持缓存报告、命中/未命中 token 数。
 *
 * @author Zm_Mmm
 * @since 2026-07-30
 */
@Getter
public final class CacheMetricsResult {

    private final boolean supported;
    private final long hitTokens;
    private final long missTokens;

    CacheMetricsResult(boolean supported, long hitTokens, long missTokens) {
        this.supported = supported;
        this.hitTokens = hitTokens;
        this.missTokens = missTokens;
    }

    static CacheMetricsResult supported(long hitTokens, long missTokens) {
        return new CacheMetricsResult(true, hitTokens, missTokens);
    }

    static CacheMetricsResult unsupported() {
        return new CacheMetricsResult(false, 0, 0);
    }
}
