package com.zm.kilacraftAI.knowledge;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.util.ConfigResourceUtil;
import com.zm.kilacraftAI.util.PluginLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地知识库管理器
 *
 * <p>负责加载和管理本地知识文件</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
public class KnowledgeBaseManager {

    private final KilacraftAI plugin;
    private final Path knowledgeDir;
    private final Map<String, String> knowledgeCache;           // 原始文件内容缓存
    private final Map<String, List<String>> chunkCache;         // 分段后内容缓存

    public KnowledgeBaseManager(KilacraftAI plugin, String dataFolderPath) {
        this.plugin = plugin;
        this.knowledgeDir = Paths.get(dataFolderPath, "knowledge");
        this.knowledgeCache = new HashMap<>();
        this.chunkCache = new HashMap<>();

        // 复制默认配置
        ConfigResourceUtil.saveDefaultResource(plugin, "knowledge/sounds_particles.md", "知识库");
    }

    /**
     * 加载所有知识文件到缓存（同时清空分段缓存）
     */
    public void loadAllKnowledge() {
        knowledgeCache.clear();
        chunkCache.clear();

        if (!Files.exists(knowledgeDir)) {
            PluginLogger.warn("知识库", "知识库目录不存在：" + knowledgeDir);
            return;
        }

        try (var stream = Files.walk(knowledgeDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".md") || path.toString().endsWith(".txt")).toList();

            for (Path file : files) {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    // 使用文件名作为 key
                    String fileName = file.getFileName().toString();
                    knowledgeCache.put(fileName, content);
                    PluginLogger.info("知识库", "已加载知识文件：" + fileName);
                } catch (IOException e) {
                    PluginLogger.error("知识库", "加载知识文件失败: " + file + " - " + e.getMessage(), e);
                }
            }

            PluginLogger.info("知识库", "共加载 " + knowledgeCache.size() + " 个知识文件");
        } catch (IOException e) {
            PluginLogger.error("知识库", "遍历知识库目录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取所有知识内容（用于检索）
     *
     * @return 所有知识的 Map（文件名 -> 内容）
     */
    public Map<String, String> getAllKnowledge() {
        return new HashMap<>(knowledgeCache);
    }

    /**
     * 获取或创建分段缓存
     *
     * @param fileName 文件名
     * @param chunks   分段列表
     */
    public void setChunkCache(String fileName, List<String> chunks) {
        chunkCache.put(fileName, chunks);
    }

    /**
     * 获取分段缓存
     *
     * @param fileName 文件名
     * @return 分段列表，如果不存在则返回 null
     */
    public List<String> getChunkCache(String fileName) {
        return chunkCache.get(fileName);
    }

    /**
     * 重新加载知识库
     */
    public void reload() {
        PluginLogger.info("知识库", "正在重新加载知识库...");
        loadAllKnowledge();
        PluginLogger.info("知识库", "知识库加载完成");
    }

    /**
     * 获取知识库统计信息
     *
     * @return 统计信息字符串
     */
    public String getStatistics() {
        int fileCount = knowledgeCache.size();
        int totalChars = knowledgeCache.values().stream().mapToInt(String::length).sum();

        return String.format("知识库：%d 个文件，共 %d 字符", fileCount, totalChars);
    }
}
