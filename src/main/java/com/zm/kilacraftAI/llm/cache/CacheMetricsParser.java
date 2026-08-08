package com.zm.kilacraftAI.llm.cache;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 多种大模型供应商缓存字段解析器。
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

        long input = nonNegative(readLong(usage, "prompt_tokens", promptTokens));
        long output = nonNegative(readLong(usage, "completion_tokens", 0));
        long total = nonNegative(readLong(usage, "total_tokens", input + output));

        try {
            // DeepSeek：缓存默认开启，usage 直接报告命中 token。
            if (hasBoth(usage, "prompt_cache_hit_tokens", "prompt_cache_miss_tokens")) {
                return CacheMetricsResult.supported(input, output, total, readLong(usage, "prompt_cache_hit_tokens", 0));
            }

            // OpenAI Chat Completions：cached_tokens 位于 prompt_tokens_details。
            if (usage.has("prompt_tokens_details") && usage.get("prompt_tokens_details").isJsonObject()) {
                JsonObject details = usage.getAsJsonObject("prompt_tokens_details");
                if (details.has("cached_tokens") && !details.get("cached_tokens").isJsonNull()) {
                    return CacheMetricsResult.supported(input, output, total, readLong(details, "cached_tokens", 0));
                }
            }

            // 兼容 Anthropic 字段：读取量作为 cacheReadTokens。
            if (usage.has("cache_read_input_tokens") && !usage.get("cache_read_input_tokens").isJsonNull()) {
                return CacheMetricsResult.supported(input, output, total, readLong(usage, "cache_read_input_tokens", 0));
            }

            // 仅有 DeepSeek 命中字段时仍可安全推导输入总量。
            if (usage.has("prompt_cache_hit_tokens") && !usage.get("prompt_cache_hit_tokens").isJsonNull()) {
                long hit = readLong(usage, "prompt_cache_hit_tokens", 0);
                long inferredInput = input > 0 ? input : Math.max(promptTokens, hit);
                return CacheMetricsResult.supported(inferredInput, output, total > 0 ? total : inferredInput + output, hit);
            }
        } catch (RuntimeException ignored) {
            // usage 解析属于旁路统计，畸形供应商字段不能影响已完成的 AI 响应。
        }

        return CacheMetricsResult.unreported(input, output, total);
    }

    private static boolean hasBoth(JsonObject obj, String key1, String key2) {
        return obj.has(key1) && !obj.get(key1).isJsonNull() && obj.has(key2) && !obj.get(key2).isJsonNull();
    }

    private static long readLong(JsonObject obj, String key, long fallback) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        return obj.get(key).getAsLong();
    }

    private static long nonNegative(long value) {
        return Math.max(0, value);
    }
}
