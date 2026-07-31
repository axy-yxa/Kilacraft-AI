package com.zm.kilacraftAI.llm.cache;

import java.util.List;

/**
 * 缓存命中率统计快照（不可变），由 {@link CacheMetricsCollector} 产出。
 *
 * @author Zm_Mmm
 * @since 2026-07-30
 */
public final class CacheStatsSnapshot {
    public final List<TypeSnapshot> types;
    public final long totalRequests;
    public final long totalPromptTokens;
    public final long totalHitTokens;
    public final long totalMissTokens;

    CacheStatsSnapshot(List<TypeSnapshot> types, long totalRequests, long totalPromptTokens, long totalHitTokens, long totalMissTokens) {
        this.types = types;
        this.totalRequests = totalRequests;
        this.totalPromptTokens = totalPromptTokens;
        this.totalHitTokens = totalHitTokens;
        this.totalMissTokens = totalMissTokens;
    }

    public double getGlobalHitRate() {
        long total = totalHitTokens + totalMissTokens;
        return total > 0 ? (double) totalHitTokens / total : 0.0;
    }

    /**
     * 节省率 = 命中 token / 总 prompt token（比命中率更保守）
     */
    public double getGlobalSaveRate() {
        return totalPromptTokens > 0 ? (double) totalHitTokens / totalPromptTokens : 0.0;
    }
}
