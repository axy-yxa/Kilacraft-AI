package com.zm.kilacraftAI.llm.cache;

import com.zm.kilacraftAI.common.enums.CacheCallTypeEnum;
import org.jetbrains.annotations.Nullable;

/**
 * 单个 {@link CacheCallTypeEnum} 的缓存命中率快照。
 * <p>
 * 包含该类型的请求计数、总消耗 token、缓存命中/未命中 token 及模型信息。
 *
 * @author Zm_Mmm
 * @since 2026-07-30
 */
public final class TypeSnapshot {
    public final CacheCallTypeEnum type;
    public final String displayName;
    public final long requests;
    public final long totalPromptTokens;
    public final long hitTokens;
    public final long missTokens;
    public final boolean supported;
    @Nullable
    public final String modelName;

    TypeSnapshot(CacheCallTypeEnum type, String displayName, long requests, long totalPromptTokens, long hitTokens, long missTokens, boolean supported, @Nullable String modelName) {
        this.type = type;
        this.displayName = displayName;
        this.requests = requests;
        this.totalPromptTokens = totalPromptTokens;
        this.hitTokens = hitTokens;
        this.missTokens = missTokens;
        this.supported = supported;
        this.modelName = modelName;
    }

    public double getHitRate() {
        long total = hitTokens + missTokens;
        return total > 0 ? (double) hitTokens / total : 0.0;
    }

    public long getSavedTokens() {
        return hitTokens;
    }
}
