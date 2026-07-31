package com.zm.kilacraftAI.llm.cache;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 多大模型供应商缓存字段解析器。
 * <p>
 * 从 API 响应的 {@code usage} JSON 对象中提取缓存命中数据，
 * 按优先级尝试多种已知格式：DeepSeek → OpenAI 原生 → Anthropic 兼容 → 单字段回退。
 * 均不匹配时标记为 {@code unsupported}。
 *
 * @author Zm_Mmm
 * @since 2026-07-30
 */
public final class CacheMetricsParser {

    private CacheMetricsParser() {
    }

    /**
     * 从 usage JSON 和已知 prompt token 数中提取缓存命中数据。
     *
     * @param usage        API 响应中的 usage 对象（可能为 null）
     * @param promptTokens 本次请求的 prompt token 数（用于反推 miss）
     * @return 解析结果，若 usage 为 null 或无法识别则返回 unsupported
     */
    @NotNull
    public static CacheMetricsResult parse(@Nullable JsonObject usage, long promptTokens) {
        if (usage == null) {
            return CacheMetricsResult.unsupported();
        }

        // 优先级 1：DeepSeek 标准格式 — prompt_cache_hit_tokens + prompt_cache_miss_tokens
        if (hasBoth(usage, "prompt_cache_hit_tokens", "prompt_cache_miss_tokens")) {
            long hit = usage.get("prompt_cache_hit_tokens").getAsLong();
            long miss = usage.get("prompt_cache_miss_tokens").getAsLong();
            return CacheMetricsResult.supported(hit, miss);
        }

        // 优先级 2：OpenAI 原生格式 — prompt_tokens_details.cached_tokens
        if (usage.has("prompt_tokens_details") && !usage.get("prompt_tokens_details").isJsonNull()) {
            JsonObject details = usage.getAsJsonObject("prompt_tokens_details");
            if (details.has("cached_tokens") && !details.get("cached_tokens").isJsonNull()) {
                long cached = details.get("cached_tokens").getAsLong();
                long miss = Math.max(0, promptTokens - cached);
                return CacheMetricsResult.supported(cached, miss);
            }
        }

        // 优先级 3：Anthropic 兼容格式 — cache_read_input_tokens + cache_creation_input_tokens
        if (hasBoth(usage, "cache_read_input_tokens", "cache_creation_input_tokens")) {
            long read = usage.get("cache_read_input_tokens").getAsLong();
            long creation = usage.get("cache_creation_input_tokens").getAsLong();
            return CacheMetricsResult.supported(read, creation);
        }

        // 优先级 4：单字段回退 — 仅 prompt_cache_hit_tokens
        if (usage.has("prompt_cache_hit_tokens") && !usage.get("prompt_cache_hit_tokens").isJsonNull()) {
            long hit = usage.get("prompt_cache_hit_tokens").getAsLong();
            long miss = Math.max(0, promptTokens - hit);
            return CacheMetricsResult.supported(hit, miss);
        }

        return CacheMetricsResult.unsupported();
    }

    private static boolean hasBoth(JsonObject obj, String key1, String key2) {
        return obj.has(key1) && !obj.get(key1).isJsonNull() && obj.has(key2) && !obj.get(key2).isJsonNull();
    }
}
