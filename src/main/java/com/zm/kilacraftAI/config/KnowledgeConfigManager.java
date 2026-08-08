package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识库配置管理器
 *
 * <p>管理独立的 knowledge.yml 配置文件，支持热重载。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-07
 */
public class KnowledgeConfigManager {

    private static final String CONFIG_FILE = "knowledge.yml";

    private final KilacraftAI plugin;
    private File configFile;

    // 知识库基础配置
    @Getter
    private boolean enabled;
    @Getter
    private int maxRelevantChunks;

    // 分段配置
    @Getter
    private int maxChunkSize;
    @Getter
    private int minChunkSize;
    @Getter
    private int chunkOverlap;

    // 关键词提取配置
    @Getter
    private int keywordTopK;

    // BM25 配置
    @Getter
    private double bm25K1;
    @Getter
    private double bm25B;

    // Embedding 语义检索配置
    @Getter
    private boolean embeddingEnabled;
    @Getter
    private String embeddingModel;
    @Getter
    private String embeddingApiUrl;
    @Getter
    private String embeddingApiKey;
    @Getter
    private int embeddingDimensions;
    @Getter
    private double embeddingMinSimilarity;
    @Getter
    private int embeddingTimeoutSeconds;
    @Getter
    private boolean embeddingCacheEnabled;

    // 检索结果过滤（软阈值）与融合
    @Getter
    private double retrievalNoiseFloor;
    @Getter
    private double retrievalRelativeThreshold;
    @Getter
    private double retrievalRrfK;

    // BM25 长度归一化
    @Getter
    private int bm25AvgDocLength;

    // 自定义词典配置
    @Getter
    private boolean customDictionaryEnabled;
    @Getter
    private List<String> customDictionaryWords;

    public KnowledgeConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
        // 不在此加载配置：ConfigManager.loadConfig() 会在获知语言后调用 loadConfig(language)
    }

    public void loadConfig() {
        String language = plugin.getConfigManager() != null ? plugin.getConfigManager().getLanguage() : "zh";
        loadConfig(language);
    }

    /**
     * 带语言参数加载配置（由 ConfigManager 在知道语言后调用）
     */
    public void loadConfig(String language) {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        // 知识库基础配置
        this.enabled = yaml.getBoolean("knowledge.enabled", true);
        this.maxRelevantChunks = yaml.getInt("knowledge.max_relevant_chunks", 3);

        // 分段配置
        this.maxChunkSize = yaml.getInt("knowledge.segment.max_size", 500);
        this.minChunkSize = yaml.getInt("knowledge.segment.min_size", 20);
        this.chunkOverlap = yaml.getInt("knowledge.segment.overlap", 30);

        // 关键词提取配置
        this.keywordTopK = yaml.getInt("knowledge.keywords.top_k", 10);

        // BM25 配置
        this.bm25K1 = yaml.getDouble("knowledge.bm25.k1", 1.5);
        this.bm25B = yaml.getDouble("knowledge.bm25.b", 0.75);
        this.bm25AvgDocLength = yaml.getInt("knowledge.bm25.avg_doc_length", 0);

        // 检索结果过滤（软阈值）与融合
        this.retrievalNoiseFloor = yaml.getDouble("knowledge.retrieval.noise_floor", 25.0);
        this.retrievalRelativeThreshold = yaml.getDouble("knowledge.retrieval.relative_threshold", 0.3);
        this.retrievalRrfK = yaml.getDouble("knowledge.retrieval.rrf_k", 60.0);

        // Embedding 配置
        this.embeddingEnabled = yaml.getBoolean("knowledge.embedding.enabled", false);
        this.embeddingModel = yaml.getString("knowledge.embedding.model", "");
        this.embeddingApiUrl = yaml.getString("knowledge.embedding.api_url", "");
        this.embeddingApiKey = yaml.getString("knowledge.embedding.api_key", "");
        this.embeddingDimensions = yaml.getInt("knowledge.embedding.dimensions", 1024);
        this.embeddingMinSimilarity = yaml.getDouble("knowledge.embedding.min_similarity", 0.5);
        this.embeddingTimeoutSeconds = yaml.getInt("knowledge.embedding.timeout_seconds", 10);
        this.embeddingCacheEnabled = yaml.getBoolean("knowledge.embedding.cache_enabled", true);

        // 自定义词典配置（按语言选择词汇列表）
        this.customDictionaryEnabled = yaml.getBoolean("knowledge.custom_dictionary.enabled", true);
        if ("en".equals(language)) {
            List<String> enWords = yaml.getStringList("knowledge.custom_dictionary.words_en");
            this.customDictionaryWords = !enWords.isEmpty() ? enWords : yaml.getStringList("knowledge.custom_dictionary.words");
        } else {
            this.customDictionaryWords = yaml.getStringList("knowledge.custom_dictionary.words");
        }
    }

    /**
     * 热重载配置
     */
    public void reload() {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        loadConfig();
    }

    /**
     * 合并内置词汇和自定义词汇（去重）
     *
     * @param internalWords 内置词汇列表
     * @return 合并后的词汇列表
     */
    public List<String> mergeDictionaryWords(List<String> internalWords) {
        Set<String> allWords = new LinkedHashSet<>(internalWords);
        if (customDictionaryWords != null) {
            allWords.addAll(customDictionaryWords);
        }
        return new ArrayList<>(allWords);
    }
}
