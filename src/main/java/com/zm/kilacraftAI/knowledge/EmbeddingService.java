package com.zm.kilacraftAI.knowledge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.util.PluginLogger;
import lombok.Getter;
import okhttp3.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Embedding API 封装服务
 *
 * <p>调用 OpenAI 兼容的 /v1/embeddings 端点获取文本向量，支持磁盘缓存与 BM25 降级。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-27
 */
public class EmbeddingService {

    private final ConfigManager configManager;
    private final OkHttpClient httpClient;
    private final EmbeddingCache cache;

    /**
     * API 可用性（volatile: 预计算线程写，检索线程读）
     */
    @Getter
    private volatile boolean available = false;

    private final String model;
    private final String apiUrl;
    private final String apiKey;

    public EmbeddingService(ConfigManager configManager, Path knowledgeDir) {
        this.configManager = configManager;
        this.cache = new EmbeddingCache(knowledgeDir);
        this.model = configManager.getEmbeddingModel();
        this.apiUrl = configManager.getEmbeddingApiUrl();
        this.apiKey = configManager.getEmbeddingApiKey();

        this.httpClient = new OkHttpClient.Builder().connectTimeout(configManager.getEmbeddingTimeoutSeconds(), TimeUnit.SECONDS).readTimeout(configManager.getEmbeddingTimeoutSeconds(), TimeUnit.SECONDS).writeTimeout(configManager.getEmbeddingTimeoutSeconds(), TimeUnit.SECONDS).retryOnConnectionFailure(true).build();

        // 检查必填配置，缺失则直接降级
        if (isBlank(apiUrl) || isBlank(apiKey) || isBlank(model)) {
            available = false;
            PluginLogger.warn("知识库", "Embedding 配置不完整（api_url / api_key / model 不能为空），已降级到 BM25");
        } else {
            PluginLogger.info("知识库", "Embedding API URL: {}", apiUrl);
            PluginLogger.info("知识库", "Embedding 模型: {}", model);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 获取单个文本的向量（缓存优先）
     */
    public float[] getEmbedding(String text) {
        if (text == null || text.isEmpty()) return null;

        float[] cached = cache.getVector(text);
        if (cached != null) {
            return cached;
        }

        float[] vector = callEmbeddingApi(text);
        if (vector != null) {
            cache.putVector(text, vector);
        }
        return vector;
    }

    /**
     * 全量预计算知识片段向量
     *
     * <p>收集未缓存片段，按批次调用 API（每批最多 64 条），失败时保存已计算部分并退出。</p>
     */
    public void precomputeAllChunks(Map<String, List<String>> allChunkCache) {
        long startTime = System.currentTimeMillis();

        if (configManager.isEmbeddingCacheEnabled()) {
            cache.load(model, configManager.getEmbeddingDimensions());
        }

        // 收集所有片段，统计缓存命中
        List<String> allChunks = new ArrayList<>();
        int cachedCount = 0;
        for (List<String> chunks : allChunkCache.values()) {
            for (String chunk : chunks) {
                if (cache.getVector(chunk) != null) {
                    cachedCount++;
                } else {
                    allChunks.add(chunk);
                }
            }
        }

        int totalChunks = allChunks.size() + cachedCount;
        int computedCount = 0;
        int batchSize = 64;

        // 分批调用 API
        for (int i = 0; i < allChunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allChunks.size());
            List<String> batch = allChunks.subList(i, end);

            List<float[]> vectors = callBatchEmbeddingApi(batch);
            if (vectors == null) {
                PluginLogger.warn("知识库", "Embedding 预计算失败，后续请求将走 BM25 降级");
                saveCacheIfNeeded();
                return;
            }

            for (int j = 0; j < vectors.size(); j++) {
                if (vectors.get(j) != null) {
                    cache.putVector(batch.get(j), vectors.get(j));
                    computedCount++;
                }
            }

            if (computedCount > 0) {
                PluginLogger.info("知识库", "Embedding 预计算进度：已计算 {} / {} 个片段", computedCount, totalChunks);
            }
        }

        saveCacheIfNeeded();
        available = true;

        long elapsed = System.currentTimeMillis() - startTime;
        PluginLogger.info("知识库", "Embedding 预计算完成：共 {} 个片段，缓存命中 {}，API 新计算 {}，耗时 {}ms", totalChunks, cachedCount, computedCount, elapsed);
    }

    /**
     * 调用 Embedding API 获取单个文本的向量（运行时查询使用）
     */
    private float[] callEmbeddingApi(String text) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("input", text);

            Request request = new Request.Builder().url(apiUrl).addHeader("Authorization", "Bearer " + apiKey).addHeader("Content-Type", "application/json").post(RequestBody.create(body.toString(), MediaType.parse("application/json"))).build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "no body";
                    PluginLogger.warn("知识库", "Embedding API 调用失败: HTTP {} - {}", response.code(), errorBody.length() > 200 ? errorBody.substring(0, 200) : errorBody);
                    return null;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                return parseEmbeddingResponse(responseBody);
            }
        } catch (IOException e) {
            PluginLogger.warn("知识库", "Embedding API 调用异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析单条 Embedding API 响应
     */
    private float[] parseEmbeddingResponse(String responseBody) {
        List<float[]> results = parseBatchEmbeddingResponse(responseBody);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 批量调用 Embedding API（预计算使用，最多 64 条/批）
     *
     * @return 向量列表（与输入顺序一致），失败返回 null
     */
    private List<float[]> callBatchEmbeddingApi(List<String> texts) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            JsonArray inputArr = new JsonArray();
            for (String text : texts) {
                inputArr.add(text);
            }
            body.add("input", inputArr);

            Request request = new Request.Builder().url(apiUrl).addHeader("Authorization", "Bearer " + apiKey).addHeader("Content-Type", "application/json").post(RequestBody.create(body.toString(), MediaType.parse("application/json"))).build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "no body";
                    PluginLogger.warn("知识库", "Embedding API 调用失败: HTTP {} - {}", response.code(), errorBody.length() > 200 ? errorBody.substring(0, 200) : errorBody);
                    return null;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                return parseBatchEmbeddingResponse(responseBody);
            }
        } catch (IOException e) {
            PluginLogger.warn("知识库", "Embedding API 调用异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析批量 Embedding API 响应：{ "data": [{ "embedding": [...], "index": 0 }, ...] }
     */
    private List<float[]> parseBatchEmbeddingResponse(String responseBody) {
        List<float[]> results = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray dataArr = root.has("data") ? root.getAsJsonArray("data") : new JsonArray();

            if (dataArr.isEmpty()) {
                PluginLogger.warn("知识库", "Embedding API 返回空 data 数组");
                return results;
            }

            // 按 index 排序确保顺序一致
            List<JsonObject> items = new ArrayList<>();
            for (int i = 0; i < dataArr.size(); i++) {
                items.add(dataArr.get(i).getAsJsonObject());
            }
            items.sort((a, b) -> {
                int ia = a.has("index") ? a.get("index").getAsInt() : 0;
                int ib = b.has("index") ? b.get("index").getAsInt() : 0;
                return Integer.compare(ia, ib);
            });

            for (JsonObject item : items) {
                JsonArray embeddingArr = item.has("embedding") ? item.getAsJsonArray("embedding") : new JsonArray();

                float[] vector = new float[embeddingArr.size()];
                for (int i = 0; i < embeddingArr.size(); i++) {
                    vector[i] = (float) embeddingArr.get(i).getAsDouble();
                }
                results.add(vector);
            }
        } catch (Exception e) {
            PluginLogger.warn("知识库", "解析 Embedding API 响应失败: {}", e.getMessage());
        }
        return results;
    }

    /**
     * 清除内存缓存、磁盘缓存，并重置可用性
     */
    public void clearCache() {
        cache.clear();
        cache.deleteDiskCache();
        available = false;
        PluginLogger.info("知识库", "Embedding 缓存已清除，需要重新预计算");
    }

    /**
     * 关闭 OkHttpClient（取消请求 → 关闭线程池 → 清空连接池）
     */
    public void shutdown() {
        try {
            httpClient.dispatcher().cancelAll();
            httpClient.dispatcher().executorService().shutdown();
            httpClient.dispatcher().executorService().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            httpClient.dispatcher().executorService().shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            PluginLogger.debug("知识库", "关闭 Embedding HTTP 客户端异常: {}", e.getMessage());
        }
        httpClient.connectionPool().evictAll();
    }

    private void saveCacheIfNeeded() {
        if (configManager.isEmbeddingCacheEnabled() && cache.size() > 0) {
            cache.save(model, configManager.getEmbeddingDimensions());
        }
    }
}
