package com.zm.kilacraftAI.llm.cache;

import com.zm.kilacraftAI.common.enums.CacheCallTypeEnum;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * 线程安全的内存态缓存命中率累加器（单例）。
 * <p>
 * 每次大模型调用后记录一次 {@link #record}，数据不持久化，重启后清零。
 * 按 {@link CacheCallTypeEnum} 分桶统计，同时维护全局汇总。
 *
 * @author Zm_Mmm
 * @since 2026-07-30
 */
public final class CacheMetricsCollector {

    private static final CacheMetricsCollector INSTANCE = new CacheMetricsCollector();

    private final ConcurrentHashMap<CacheCallTypeEnum, TypeMetrics> metrics = new ConcurrentHashMap<>();

    private CacheMetricsCollector() {
    }

    @NotNull
    public static CacheMetricsCollector getInstance() {
        return INSTANCE;
    }

    /**
     * 记录一次大模型调用的缓存命中数据。
     *
     * @param type         调用类型
     * @param modelName    使用的模型名
     * @param result       缓存解析结果
     * @param promptTokens 本次请求的 prompt token 总数（usage.prompt_tokens）
     */
    public void record(@NotNull CacheCallTypeEnum type, @NotNull String modelName, @NotNull CacheMetricsResult result, long promptTokens) {
        TypeMetrics tm = metrics.computeIfAbsent(type, k -> new TypeMetrics());
        tm.modelName.compareAndSet(null, modelName);
        tm.requestCount.increment();
        if (promptTokens > 0) {
            tm.totalPromptTokens.add(promptTokens);
        }
        if (result.isSupported()) {
            tm.cacheHitTokens.add(result.getHitTokens());
            tm.cacheMissTokens.add(result.getMissTokens());
            tm.supported.compareAndSet(false, true);
        }
    }

    /**
     * 获取当前统计快照。
     */
    @NotNull
    public CacheStatsSnapshot getSnapshot() {
        List<TypeSnapshot> types = new ArrayList<>();
        long totalRequests = 0;
        long totalPrompt = 0;
        long totalHit = 0;
        long totalMiss = 0;

        for (Map.Entry<CacheCallTypeEnum, TypeMetrics> entry : metrics.entrySet()) {
            CacheCallTypeEnum type = entry.getKey();
            TypeMetrics tm = entry.getValue();
            long requests = tm.requestCount.sum();
            long prompt = tm.totalPromptTokens.sum();
            long hit = tm.cacheHitTokens.sum();
            long miss = tm.cacheMissTokens.sum();
            boolean supported = tm.supported.get();
            String model = tm.modelName.get();

            totalRequests += requests;
            totalPrompt += prompt;
            totalHit += hit;
            totalMiss += miss;

            types.add(new TypeSnapshot(type, type.getDisplayName(), requests, prompt, hit, miss, supported, model));
        }

        types.sort(Comparator.comparingInt(a -> a.type.ordinal()));

        return new CacheStatsSnapshot(types, totalRequests, totalPrompt, totalHit, totalMiss);
    }

    /**
     * 全局命中率（0.0 ~ 1.0），无数据时返回 0。
     */
    public double getGlobalHitRate() {
        long hit = 0;
        long miss = 0;
        for (TypeMetrics tm : metrics.values()) {
            hit += tm.cacheHitTokens.sum();
            miss += tm.cacheMissTokens.sum();
        }
        long total = hit + miss;
        return total > 0 ? (double) hit / total : 0.0;
    }

    /**
     * 总请求数。
     */
    public long getTotalRequests() {
        long total = 0;
        for (TypeMetrics tm : metrics.values()) {
            total += tm.requestCount.sum();
        }
        return total;
    }

    /**
     * 是否有任何类型成功解析到缓存数据。
     */
    public boolean isAnyTypeSupported() {
        for (TypeMetrics tm : metrics.values()) {
            if (tm.supported.get()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 已支持缓存报告的类型数。
     */
    public int getSupportedTypeCount() {
        int count = 0;
        for (TypeMetrics tm : metrics.values()) {
            if (tm.supported.get()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 总类型数。
     */
    public int getTotalTypeCount() {
        return metrics.size();
    }

    /**
     * 重置全部统计数据。
     */
    public void reset() {
        metrics.clear();
    }

    private static class TypeMetrics {
        final LongAdder requestCount = new LongAdder();
        final LongAdder totalPromptTokens = new LongAdder();
        final LongAdder cacheHitTokens = new LongAdder();
        final LongAdder cacheMissTokens = new LongAdder();
        final AtomicReference<String> modelName = new AtomicReference<>();
        final AtomicBoolean supported = new AtomicBoolean(false);
    }
}
