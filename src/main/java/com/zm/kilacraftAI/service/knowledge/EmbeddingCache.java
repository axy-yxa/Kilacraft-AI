package com.zm.kilacraftAI.service.knowledge;

import com.google.gson.*;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedding 向量缓存持久化
 *
 * <p>将文本向量缓存到磁盘文件，避免每次启动重新调用 Embedding API。</p>
 * <p>缓存格式：JSON，以内容 SHA-256 前 8 位 hash 作为 key。</p>
 * <p>当模型名或维度变更时，缓存自动失效并重新计算。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-27
 */
public class EmbeddingCache {

    private static final String CACHE_FILE = ".embedding_cache";
    private static final int HASH_PREFIX_LENGTH = 8;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path cachePath;
    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();

    /**
     * 缓存条目。
     *
     * @param norm 向量的 L2 范数（预计算，供余弦相似度分母复用，避免每次查询重复开方求和）
     */
    public record CacheEntry(String contentPreview, float[] vector, double norm) {
        /**
         * 兼容旧代码：无 norm 时自动计算
         */
        public CacheEntry(String contentPreview, float[] vector) {
            this(contentPreview, vector, computeNorm(vector));
        }

        private static double computeNorm(float[] vector) {
            double sumSq = 0.0;
            for (float v : vector) sumSq += (double) v * v;
            return Math.sqrt(sumSq);
        }
    }

    public EmbeddingCache(Path knowledgeDir) {
        this.cachePath = knowledgeDir.resolve(CACHE_FILE);
    }

    /**
     * 从磁盘加载缓存
     *
     * @param model      当前 Embedding 模型名
     * @param dimensions 当前向量维度
     * @return true=缓存有效（模型/维度匹配），false=缓存无效或不存在
     */
    public synchronized boolean load(String model, int dimensions) {
        entries.clear();

        if (!Files.exists(cachePath)) {
            PluginLoggerUtil.info("知识库", "Embedding 缓存文件不存在，将全量计算", cachePath);
            return false;
        }

        try {
            String json = Files.readString(cachePath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            String fileModel = root.has("model") ? root.get("model").getAsString() : "";
            int fileDimensions = root.has("dimensions") ? root.get("dimensions").getAsInt() : 0;

            // 模型或维度变更 → 缓存失效
            if (!model.equals(fileModel) || dimensions != fileDimensions) {
                PluginLoggerUtil.info("知识库", "Embedding 缓存失效（模型或维度变更: {} → {}），将重新计算", fileModel + "/" + fileDimensions, model + "/" + dimensions);
                return false;
            }

            JsonObject entriesObj = root.has("entries") ? root.getAsJsonObject("entries") : new JsonObject();
            for (Map.Entry<String, JsonElement> entry : entriesObj.entrySet()) {
                JsonObject obj = entry.getValue().getAsJsonObject();
                String preview = obj.has("content_preview") ? obj.get("content_preview").getAsString() : "";
                JsonArray vecArr = obj.has("vector") ? obj.getAsJsonArray("vector") : new JsonArray();

                float[] vector = new float[vecArr.size()];
                for (int i = 0; i < vecArr.size(); i++) {
                    vector[i] = (float) vecArr.get(i).getAsDouble();
                }

                // 兼容旧缓存（无 norm 字段）：用向量重新计算 norm
                double norm = obj.has("norm") ? obj.get("norm").getAsDouble() : CacheEntry.computeNorm(vector);

                entries.put(entry.getKey(), new CacheEntry(preview, vector, norm));
            }

            PluginLoggerUtil.info("知识库", "已加载 Embedding 缓存（{} 条，模型：{}）", entries.size(), model);
            return true;

        } catch (Exception e) {
            PluginLoggerUtil.warn("知识库", "加载 Embedding 缓存失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 保存缓存到磁盘
     *
     * @param model      当前 Embedding 模型名
     * @param dimensions 当前向量维度
     */
    public synchronized void save(String model, int dimensions) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("model", model);
            root.addProperty("dimensions", dimensions);
            root.addProperty("created_at", System.currentTimeMillis());

            JsonObject entriesObj = new JsonObject();
            for (Map.Entry<String, CacheEntry> entry : entries.entrySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("content_preview", entry.getValue().contentPreview());

                JsonArray vecArr = new JsonArray();
                for (float v : entry.getValue().vector()) {
                    vecArr.add(v);
                }
                obj.add("vector", vecArr);
                obj.addProperty("norm", entry.getValue().norm());

                entriesObj.add(entry.getKey(), obj);
            }
            root.add("entries", entriesObj);

            Files.writeString(cachePath, GSON.toJson(root), StandardCharsets.UTF_8);
            PluginLoggerUtil.info("知识库", "Embedding 缓存已保存（{} 条）", entries.size());

        } catch (IOException e) {
            PluginLoggerUtil.warn("知识库", "保存 Embedding 缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 获取缓存的向量
     *
     * @param text 文本内容
     * @return 向量，不存在返回 null
     */
    public float[] getVector(String text) {
        if (text == null) return null;
        String hash = contentHash(text);
        CacheEntry entry = entries.get(hash);
        return entry != null ? entry.vector : null;
    }

    /**
     * 存储向量到缓存
     *
     * @param text   文本内容
     * @param vector 向量
     */
    public void putVector(String text, float[] vector) {
        if (text == null) return;
        String hash = contentHash(text);
        String preview = text.length() > 100 ? text.substring(0, 100) + "..." : text;
        entries.put(hash, new CacheEntry(preview, vector));
    }

    /**
     * 获取预计算的向量 L2 范数（避免每次余弦相似度计算时重算 normB）。
     *
     * @param text 文本内容
     * @return 向量的 L2 范数，不存在返回 1.0（不会导致除零；查不到 norm 意味着向量不在缓存里，调用方不应走到这步）
     */
    public double getNorm(String text) {
        if (text == null) return 1.0;
        String hash = contentHash(text);
        CacheEntry entry = entries.get(hash);
        return entry != null ? entry.norm() : 1.0;
    }

    /**
     * 清除所有缓存条目
     */
    public synchronized void clear() {
        entries.clear();
        PluginLoggerUtil.info("知识库", "Embedding 缓存已清除");
    }

    /**
     * 获取缓存条目数
     */
    public int size() {
        return entries.size();
    }

    /**
     * 删除磁盘缓存文件
     *
     * @return true=删除成功或文件不存在，false=删除失败
     */
    public synchronized boolean deleteDiskCache() {
        try {
            if (Files.exists(cachePath)) {
                Files.delete(cachePath);
                PluginLoggerUtil.debug("知识库", "已删除 Embedding 磁盘缓存: {}", cachePath);
            }
            return true;
        } catch (IOException e) {
            PluginLoggerUtil.warn("知识库", "删除 Embedding 磁盘缓存失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 计算文本内容的 SHA-256 前 8 位 hash
     */
    private static String contentHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, HASH_PREFIX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 在所有 JDK 中都可用，此处仅做安全兜底
            return String.valueOf(text.hashCode());
        }
    }
}
