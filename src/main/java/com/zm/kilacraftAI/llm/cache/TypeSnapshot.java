package com.zm.kilacraftAI.llm.cache;

import com.zm.kilacraftAI.common.enums.CacheCallTypeEnum;
import org.jetbrains.annotations.Nullable;

/**
 * 单个 {@link CacheCallTypeEnum} 的缓存统计快照。
 * <p>
 * 包含该类型的请求计数、输入/输出/总计 token、缓存读取 token 及模型信息。
 *
 * @author Zm_Mmm
 * @since 2026-07-30
 */
public final class TypeSnapshot {
    public final CacheCallTypeEnum type;
    public final long requests;
    public final long inputTokens;
    public final long outputTokens;
    public final long totalTokens;
    public final long cacheReadTokens;
    public final boolean supported;
    @Nullable
    public final String modelName;

    TypeSnapshot(CacheCallTypeEnum type, long requests, long inputTokens, long outputTokens, long totalTokens, long cacheReadTokens, boolean supported, @Nullable String modelName) {
        this.type = type;
        this.requests = requests;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.supported = supported;
        this.modelName = modelName;
    }

    public double getHitRate() {
        return inputTokens > 0 ? (double) cacheReadTokens / inputTokens : 0.0;
    }
}
