package com.zm.kilacraftAI.knowledge;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.util.ConfigResourceUtil;
import com.zm.kilacraftAI.util.PluginLogger;
import lombok.Getter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    /**
     *  知识库目录路径
     */
    @Getter
    private final Path knowledgeDir;
    private Path effectiveDir;                           // 实际加载的知识库目录（根据语言动态选择）
    private final Map<String, String> knowledgeCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> chunkCache = new ConcurrentHashMap<>();

    public KnowledgeBaseManager(KilacraftAI plugin, String dataFolderPath) {
        this.plugin = plugin;
        this.knowledgeDir = Paths.get(dataFolderPath, "knowledge");

        updateEffectiveDir();
    }

    /**
     * 根据当前语言更新 effectiveDir，并拷贝对应语言的默认知识库资源
     */
    private void updateEffectiveDir() {
        String lang = plugin.getConfigManager().getLanguage();
        if ("zh".equals(lang)) {
            this.effectiveDir = knowledgeDir;
            ConfigResourceUtil.saveDefaultResourceDir(plugin, "knowledge", 1);
        } else {
            this.effectiveDir = knowledgeDir.resolve(lang);
            ConfigResourceUtil.saveDefaultResourceDir(plugin, "knowledge/" + lang);
        }
    }

    /**
     * 加载所有知识文件到缓存（同时清空分段缓存）
     */
    public void loadAllKnowledge() {
        knowledgeCache.clear();
        chunkCache.clear();

        if (!Files.exists(effectiveDir)) {
            // 目录不存在时静默创建空目录，允许服主自行添加知识库文件
            try {
                Files.createDirectories(effectiveDir);
            } catch (IOException e) {
                PluginLogger.warn("知识库", I18nService.tr("创建知识库目录失败: {}", e.getMessage()));
            }
            return;
        }

        try (var stream = Files.walk(effectiveDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".md") || path.toString().endsWith(".txt")).toList();

            for (Path file : files) {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    // 使用文件名作为 key
                    String fileName = file.getFileName().toString();
                    knowledgeCache.put(fileName, content);
                    PluginLogger.info("知识库", "已加载知识文件：{}", fileName);
                } catch (IOException e) {
                    PluginLogger.error("知识库", I18nService.tr("加载知识文件失败: {}", file + " - " + e.getMessage()), e);
                }
            }

            PluginLogger.info("知识库", "共加载 {} 个知识文件", knowledgeCache.size());
        } catch (IOException e) {
            PluginLogger.error("知识库", I18nService.tr("遍历知识库目录失败: {}", e.getMessage()), e);
        }
    }

    /**
     * 获取所有知识内容（返回副本）
     */
    public Map<String, String> getAllKnowledge() {
        return new HashMap<>(knowledgeCache);
    }

    /**
     * 获取所有分段缓存（返回副本，用于 Embedding 预计算）
     */
    public Map<String, List<String>> getAllChunkCache() {
        return new HashMap<>(chunkCache);
    }

    /**
     * 设置分段缓存
     */
    public void setChunkCache(String fileName, List<String> chunks) {
        chunkCache.put(fileName, chunks);
    }

    /**
     * 获取分段缓存
     */
    public List<String> getChunkCache(String fileName) {
        return chunkCache.get(fileName);
    }

    /**
     * 重新加载知识库（语言变更时同步切换目录）
     */
    public void reload() {
        PluginLogger.info("知识库", "正在重新加载知识库...");
        updateEffectiveDir();
        loadAllKnowledge();
        PluginLogger.info("知识库", "知识库加载完成");
    }

    /**
     * 获取知识库统计信息
     */
    public String getStatistics() {
        int fileCount = knowledgeCache.size();
        int totalChars = knowledgeCache.values().stream().mapToInt(String::length).sum();

        return String.format("知识库：%d 个文件，共 %d 字符", fileCount, totalChars);
    }
}
