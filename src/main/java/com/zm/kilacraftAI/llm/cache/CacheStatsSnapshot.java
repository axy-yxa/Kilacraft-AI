package com.zm.kilacraftAI.llm.cache;

import java.util.List;

/**
 * 缓存统计快照（不可变），由 {@link CacheMetricsCollector} 产出。
 *
 * @author Zm_Mmm
 * @since 2026-07-30
 */
public final class CacheStatsSnapshot {
    public final List<TypeSnapshot> types;
    public final long totalRequests;
    public final long totalInputTokens;
    public final long totalOutputTokens;
    public final long totalTokens;
    public final long totalCacheReadTokens;

    CacheStatsSnapshot(List<TypeSnapshot> types, long totalRequests, long totalInputTokens, long totalOutputTokens, long totalTokens, long totalCacheReadTokens) {
        this.types = types;
        this.totalRequests = totalRequests;
        this.totalInputTokens = totalInputTokens;
        this.totalOutputTokens = totalOutputTokens;
        this.totalTokens = totalTokens;
        this.totalCacheReadTokens = totalCacheReadTokens;
    }

    public double getGlobalHitRate() {
        return totalInputTokens > 0 ? (double) totalCacheReadTokens / totalInputTokens : 0.0;
    }
}
