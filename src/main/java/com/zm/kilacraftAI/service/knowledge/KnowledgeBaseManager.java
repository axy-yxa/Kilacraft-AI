package com.zm.kilacraftAI.service.knowledge;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import lombok.Getter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * 知识库目录路径
     */
    @Getter
    private final Path knowledgeDir;
    private final Map<String, String> knowledgeCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> chunkCache = new ConcurrentHashMap<>();

    // 语料播种词典候选词正则
    /**
     * 斜杠命令名：/back → back（发现服务器专有命令）
     */
    private static final Pattern CORPUS_COMMAND_PATTERN = Pattern.compile("/([a-zA-Z][a-zA-Z0-9_-]{1,20})");
    /**
     * 连字符/下划线标识符：mob-farm、ender-dragon、bge-reranker-v2（HanLP 默认会在 -/_ 处切开，入库可保持整体）
     */
    private static final Pattern CORPUS_IDENTIFIER_PATTERN = Pattern.compile("(?<![a-zA-Z0-9])([a-zA-Z]{2,}(?:[-_][a-zA-Z0-9]+){1,4})(?![a-zA-Z0-9])");

    public KnowledgeBaseManager(KilacraftAI plugin, String dataFolderPath) {
        this.plugin = plugin;
        this.knowledgeDir = Paths.get(dataFolderPath, "knowledge");
    }

    /**
     * 加载所有知识文件到缓存（同时清空分段缓存）
     */
    public void loadAllKnowledge() {
        knowledgeCache.clear();
        chunkCache.clear();

        if (!Files.exists(knowledgeDir)) {
            // 目录不存在时静默创建空目录，允许服主自行添加知识库文件
            try {
                Files.createDirectories(knowledgeDir);
            } catch (IOException e) {
                PluginLoggerUtil.warn("知识库", I18nService.tr("创建知识库目录失败: {}", e.getMessage()));
            }
            return;
        }

        try (var stream = Files.walk(knowledgeDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".md") || path.toString().endsWith(".txt")).toList();

            for (Path file : files) {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    // 用相对 knowledge/ 根的路径做 key，避免子目录归类时同名文件互相覆盖；正斜杠规范化保证跨平台一致
                    String key = knowledgeDir.relativize(file).toString().replace('\\', '/');
                    knowledgeCache.put(key, content);
                    PluginLoggerUtil.info("知识库", "已加载知识文件：{}", key);
                } catch (IOException e) {
                    PluginLoggerUtil.error("知识库", I18nService.tr("加载知识文件失败: {} - {}", file, e.getMessage()), e);
                }
            }

            PluginLoggerUtil.info("知识库", "共加载 {} 个知识文件", knowledgeCache.size());
        } catch (IOException e) {
            PluginLoggerUtil.error("知识库", I18nService.tr("遍历知识库目录失败: {}", e.getMessage()), e);
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
     * 从知识库语料扫描词典候选词（语料播种词典）。
     *
     * <p>提取斜杠命令名（{@code /back} → {@code back}）与连字符/下划线标识符（{@code mob-farm}），
     * 合并入 HanLP 自定义词典，使分词时能识别服务器专有词、不在 {@code -/_} 处误切。
     * 纯英文普通词不收（HanLP 对 ASCII 已整体保留，收了也只是字典膨胀）；中文复合词挖掘因噪声风险暂不做。</p>
     *
     * @return 候选词集合（小写、去重保序）
     */
    public Set<String> extractDictionaryCandidates() {
        return extractDictionaryCandidates(knowledgeCache.values());
    }

    /**
     * 纯函数版候选词提取（便于单测，不依赖实例）。
     */
    public static Set<String> extractDictionaryCandidates(Collection<String> contents) {
        Set<String> candidates = new LinkedHashSet<>();
        if (contents == null) {
            return candidates;
        }
        for (String content : contents) {
            if (content == null || content.isEmpty()) {
                continue;
            }
            Matcher cmd = CORPUS_COMMAND_PATTERN.matcher(content);
            while (cmd.find()) {
                candidates.add(cmd.group(1).toLowerCase());
            }
            Matcher id = CORPUS_IDENTIFIER_PATTERN.matcher(content);
            while (id.find()) {
                candidates.add(id.group(1).toLowerCase());
            }
        }
        return candidates;
    }

    /**
     * 将内置 + 自定义词典词与语料候选词合并，供 {@code TextProcessorFactory.initialize} 使用。
     *
     * @param baseWords 来自配置的词典词（内置 + custom_dictionary.words）
     * @return 合并语料候选词后的词典词列表
     */
    public List<String> buildDictionaryWordsWithCorpus(List<String> baseWords) {
        List<String> merged = new ArrayList<>(baseWords != null ? baseWords : List.of());
        Set<String> candidates = extractDictionaryCandidates();
        if (!candidates.isEmpty()) {
            merged.addAll(candidates);
            PluginLoggerUtil.info("知识库", "从知识库语料播种 {} 个词典候选词", candidates.size());
        }
        return merged;
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
     * 重新加载知识库
     */
    public void reload() {
        PluginLoggerUtil.info("知识库", "正在重新加载知识库...");
        loadAllKnowledge();
        PluginLoggerUtil.info("知识库", "知识库加载完成");
    }

    /**
     * 获取知识库统计信息
     */
    public String getStatistics() {
        int fileCount = knowledgeCache.size();
        int totalChars = knowledgeCache.values().stream().mapToInt(String::length).sum();

        return I18nService.tr("知识库：{} 个文件，共 {} 字符", fileCount, totalChars);
    }
}
