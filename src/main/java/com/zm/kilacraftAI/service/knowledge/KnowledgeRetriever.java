package com.zm.kilacraftAI.service.knowledge;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.i18n.TextProcessorFactory;
import com.zm.kilacraftAI.model.knowledge.KnowledgeChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识检索器
 *
 * <p>从知识库中检索与问题相关的知识片段</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
public class KnowledgeRetriever {

    private final KnowledgeBaseManager knowledgeBase;
    private int maxRelevantChunks;
    private double minRelevanceScore;      // 最低相关性得分阈值（BM25 模式）

    // 关键词提取配置
    private int keywordTopK;

    // BM25 评分算法参数
    private double bm25K1;  // 词频饱和参数
    private double bm25B;   // 文档长度归一化参数

    // 分段配置
    private int MAX_CHUNK_SIZE;
    private int MIN_CHUNK_SIZE;
    private int CHUNK_OVERLAP;

    // Embedding 语义检索（volatile: 主线程写配置，异步检索线程读）
    private volatile EmbeddingService embeddingService;
    private volatile boolean embeddingEnabled;
    private volatile double embeddingMinSimilarity;

    public KnowledgeRetriever(KnowledgeBaseManager knowledgeBase, int maxRelevantChunks, double minRelevanceScore, int maxChunkSize, int minChunkSize, int chunkOverlap, int keywordTopK, double bm25K1, double bm25B) {
        this.knowledgeBase = knowledgeBase;
        applyConfig(maxRelevantChunks, minRelevanceScore, maxChunkSize, minChunkSize, chunkOverlap, keywordTopK, bm25K1, bm25B);
    }

    /**
     * 刷新配置参数（热重载时由 reload 流程调用）
     * <p>知识库内容的刷新由 {@link KnowledgeBaseManager#reload()} 处理，
     * 此方法仅更新算法调优参数（分段大小、BM25、阈值等）。</p>
     *
     * @param maxRelevantChunks 最大返回片段数
     * @param minRelevanceScore 最低相关性阈值
     * @param maxChunkSize      最大分段大小
     * @param minChunkSize      最小分段大小
     * @param chunkOverlap      段间重叠字符数
     * @param keywordTopK       关键词提取数量
     * @param bm25K1            BM25 k1 参数
     * @param bm25B             BM25 b 参数
     */
    public void refreshConfig(int maxRelevantChunks, double minRelevanceScore, int maxChunkSize, int minChunkSize, int chunkOverlap, int keywordTopK, double bm25K1, double bm25B) {
        applyConfig(maxRelevantChunks, minRelevanceScore, maxChunkSize, minChunkSize, chunkOverlap, keywordTopK, bm25K1, bm25B);
    }

    /**
     * 设置 Embedding 服务
     */
    public void setEmbeddingService(EmbeddingService embeddingService, boolean embeddingEnabled, double embeddingMinSimilarity) {
        this.embeddingService = embeddingService;
        this.embeddingEnabled = embeddingEnabled;
        this.embeddingMinSimilarity = embeddingMinSimilarity;
    }

    private void applyConfig(int maxRelevantChunks, double minRelevanceScore, int maxChunkSize, int minChunkSize, int chunkOverlap, int keywordTopK, double bm25K1, double bm25B) {
        this.maxRelevantChunks = maxRelevantChunks;
        this.minRelevanceScore = minRelevanceScore;
        this.keywordTopK = keywordTopK;
        this.bm25K1 = bm25K1;
        this.bm25B = bm25B;
        this.MAX_CHUNK_SIZE = maxChunkSize;
        this.MIN_CHUNK_SIZE = minChunkSize;
        this.CHUNK_OVERLAP = chunkOverlap;
    }

    /**
     * 预构建全部分段缓存
     *
     * <p>在 Embedding 预计算前调用，确保所有知识文件已完成分段。</p>
     */
    public void buildChunkCache() {
        Map<String, String> allKnowledge = knowledgeBase.getAllKnowledge();
        for (Map.Entry<String, String> entry : allKnowledge.entrySet()) {
            getOrSplitChunks(entry.getValue(), entry.getKey());
        }
    }

    /**
     * 检索与问题相关的知识
     */
    public List<String> retrieveKnowledge(String question) {
        long startTime = System.currentTimeMillis();
        Map<String, String> allKnowledge = knowledgeBase.getAllKnowledge();

        if (allKnowledge.isEmpty()) {
            return Collections.emptyList();
        }

        // Embedding 语义检索路径
        if (embeddingEnabled && embeddingService != null && embeddingService.isAvailable()) {
            return retrieveByEmbedding(question, allKnowledge, startTime);
        }

        // BM25 算法检索路径（默认 / 降级）
        return retrieveByBM25(question, allKnowledge, startTime);
    }

    /**
     * Embedding 语义检索路径
     */
    private List<String> retrieveByEmbedding(String question, Map<String, String> allKnowledge, long startTime) {
        PluginLoggerUtil.debug("知识库", "使用 Embedding 语义检索");

        float[] queryVec = embeddingService.getEmbedding(question);
        if (queryVec == null) {
            PluginLoggerUtil.warn("知识库", "获取问题 Embedding 失败，降级到 BM25");
            return retrieveByBM25(question, allKnowledge, startTime);
        }

        // 查询向量的 L2 范数：整个检索过程只算一次，不再对每个 chunk 重算
        double queryNormSumSq = 0.0;
        for (float v : queryVec) queryNormSumSq += (double) v * v;
        double queryNorm = Math.sqrt(queryNormSumSq);

        // 遍历片段计算余弦相似度
        List<KnowledgeChunk> chunkScores = new ArrayList<>();
        int totalChunks = 0;

        for (Map.Entry<String, String> entry : allKnowledge.entrySet()) {
            String fileName = entry.getKey();
            String content = entry.getValue();

            List<String> chunks = getOrSplitChunks(content, fileName);
            totalChunks += chunks.size();

            for (String chunk : chunks) {
                float[] chunkVec = embeddingService.getEmbedding(chunk);
                if (chunkVec == null) continue;

                double chunkNorm = embeddingService.getChunkNorm(chunk);
                double similarity = cosineSimilarity(queryVec, chunkVec, queryNorm, chunkNorm);
                if (similarity > 0) {
                    chunkScores.add(new KnowledgeChunk(fileName, chunk, similarity));
                }
            }
        }

        // 按相似度排序
        chunkScores.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        // 过滤低相似度 + 取前 N
        List<String> relevantKnowledge = new ArrayList<>();
        int count = Math.min(chunkScores.size(), maxRelevantChunks);
        double threshold = embeddingMinSimilarity >= 0 ? embeddingMinSimilarity : 0.3;

        for (int i = 0; i < count; i++) {
            KnowledgeChunk chunk = chunkScores.get(i);
            if (chunk.getScore() < threshold) {
                PluginLoggerUtil.debug("知识库", "剩余片段相似度过低（{}），停止返回", String.format("%.4f", chunk.getScore()));
                break;
            }
            relevantKnowledge.add(chunk.getContent());
        }

        logRetrievalStats(startTime, allKnowledge.size(), totalChunks, chunkScores.size(), relevantKnowledge.size(), chunkScores);
        return relevantKnowledge;
    }

    /**
     * BM25 算法检索路径（默认 / Embedding 降级）
     */
    private List<String> retrieveByBM25(String question, Map<String, String> allKnowledge, long startTime) {
        if (embeddingEnabled) {
            PluginLoggerUtil.debug("知识库", "Embedding 不可用，降级到 BM25");
        }

        // HanLP TF-IDF 提取有意义的关键词
        List<String> keywords = extractKeywords(question);

        PluginLoggerUtil.debug("知识库", "提取关键词：{}", keywords);

        // 存储所有片段及其得分
        List<KnowledgeChunk> chunkScores = new ArrayList<>();
        int totalChunks = 0;

        for (Map.Entry<String, String> entry : allKnowledge.entrySet()) {
            String fileName = entry.getKey();
            String content = entry.getValue();

            List<String> chunks = getOrSplitChunks(content, fileName);
            totalChunks += chunks.size();

            // 计算每个片段与问题的相关性得分
            for (String chunk : chunks) {
                double score = calculateRelevance(question, chunk, keywords);
                if (score > 0) {
                    chunkScores.add(new KnowledgeChunk(fileName, chunk, score));
                }
            }
        }

        // 按得分排序
        chunkScores.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        // 取前 N 个最相关的片段，过滤掉低分噪声
        List<String> relevantKnowledge = new ArrayList<>();
        int count = Math.min(chunkScores.size(), maxRelevantChunks);

        for (int i = 0; i < count; i++) {
            KnowledgeChunk chunk = chunkScores.get(i);
            if (chunk.getScore() < minRelevanceScore) {
                PluginLoggerUtil.debug("知识库", "剩余片段得分过低（{}），停止返回", String.format("%.2f", chunk.getScore()));
                break;
            }
            relevantKnowledge.add(chunk.getContent());
        }

        logRetrievalStats(startTime, allKnowledge.size(), totalChunks, chunkScores.size(), relevantKnowledge.size(), chunkScores);
        return relevantKnowledge;
    }

    /**
     * 获取或创建分段缓存
     */
    private List<String> getOrSplitChunks(String content, String fileName) {
        List<String> chunks = knowledgeBase.getChunkCache(fileName);
        if (chunks == null) {
            long cacheStartTime = System.currentTimeMillis();
            chunks = splitIntoChunks(content, fileName);
            knowledgeBase.setChunkCache(fileName, chunks);
            PluginLoggerUtil.debug("知识库", "缓存文件：{} - 首次分段并缓存，耗时 {}ms", fileName, System.currentTimeMillis() - cacheStartTime);
        } else {
            PluginLoggerUtil.debug("知识库", "缓存文件：{} - 使用缓存的分段（{} 个片段）", fileName, chunks.size());
        }
        return chunks;
    }

    /**
     * 输出检索统计日志
     */
    private void logRetrievalStats(long startTime, int fileCount, int totalChunks, int matchedChunks, int returnedChunks, List<KnowledgeChunk> chunkScores) {
        long endTime = System.currentTimeMillis();
        PluginLoggerUtil.debug("知识库", "检索耗时：{}ms", endTime - startTime);
        PluginLoggerUtil.debug("知识库", "文件总数：{}, 总片段数：{}", fileCount, totalChunks);
        PluginLoggerUtil.debug("知识库", "匹配片段：{}, 返回得分最高的 {} 条", matchedChunks, returnedChunks);

        for (int i = 0; i < Math.min(returnedChunks, chunkScores.size()); i++) {
            KnowledgeChunk chunk = chunkScores.get(i);
            PluginLoggerUtil.debug("知识库", "匹配 #{} - 文件：{}, 得分：{}, 长度：{} 字符", i + 1, chunk.getFileName(), String.format("%.4f", chunk.getScore()), chunk.getContent().length());
            String preview = chunk.getContent().length() > 100 ? chunk.getContent().substring(0, 100) + "..." : chunk.getContent();
            PluginLoggerUtil.debug("知识库", preview.replace("\n", "\\n"));
        }
    }

    /**
     * 从问题中提取关键词（中文 HanLP TF-IDF / 英文 TF + 停用词过滤）
     */
    private List<String> extractKeywords(String question) {
        if (question == null || question.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return TextProcessorFactory.get().extractKeywords(question, keywordTopK);
    }

    /**
     * BM25 相关性评分
     */
    private double calculateRelevance(String question, String content, List<String> keywords) {
        if (question == null || question.trim().isEmpty() || content == null || content.trim().isEmpty()) {
            return 0.0;
        }

        String lowerQuestion = question.toLowerCase();
        String lowerContent = content.toLowerCase();

        double score = 0.0;

        // 完整问题匹配（最高优先级）
        if (lowerContent.contains(lowerQuestion)) {
            score += 50.0;
        }

        // BM25 评分
        score += BM25Scorer.score(content, keywords, bm25K1, bm25B, 500);

        // 标题位置加权：标题中的关键词额外加分
        for (String keyword : keywords) {
            String[] lines = lowerContent.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("#") && line.contains(keyword.toLowerCase())) {
                    score += 15.0 * BM25Scorer.getKeywordWeight(keyword);
                    break;
                }
            }
        }

        // 精确匹配额外奖励
        boolean hasExactMatch = keywords.stream().anyMatch(lowerContent::contains);
        if (hasExactMatch) {
            score += 10.0;
        }

        return score;
    }

    /**
     * 将长文本分割成合适大小的片段
     */
    private List<String> splitIntoChunks(String content, String fileName) {
        List<String> chunks = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            return chunks;
        }

        long splitStartTime = System.currentTimeMillis();

        // 策略 1: 按 Markdown 标题分割 (# 或 ## 等) - 仅对 .md 文件生效
        boolean isMarkdownFile = fileName != null && fileName.toLowerCase().endsWith(".md");

        if (isMarkdownFile) {
            List<String> markdownChunks = splitByMarkdownHeaders(content);
            if (!markdownChunks.isEmpty()) {
                // 如果 Markdown 分割成功，检查每个块是否还需要进一步分割
                for (String mdChunk : markdownChunks) {
                    if (mdChunk.length() > MAX_CHUNK_SIZE) {
                        // 仍然太大，按段落分割
                        chunks.addAll(splitByParagraphs(mdChunk));
                    } else if (mdChunk.length() >= MIN_CHUNK_SIZE) {
                        chunks.add(mdChunk.trim());
                    }
                }

                PluginLoggerUtil.debug("知识库", "分段文件：{} - 使用 Markdown 标题分割，得到 {} 个片段，耗时 {}ms", fileName, chunks.size(), System.currentTimeMillis() - splitStartTime);
                return chunks;
            }
        }

        // 策略 2: 按段落分割（空行分隔）
        chunks = splitByParagraphs(content);
        if (!chunks.isEmpty()) {
            PluginLoggerUtil.debug("知识库", "分段文件：{} - 使用段落分割，得到 {} 个片段，耗时 {}ms", fileName, chunks.size(), System.currentTimeMillis() - splitStartTime);
            return chunks;
        }

        // 策略 3: 如果以上都失败，按固定大小分割
        chunks = splitByFixedSize(content);

        PluginLoggerUtil.debug("知识库", "分段文件：{} - 使用固定大小分割，得到 {} 个片段，耗时 {}ms", fileName, chunks.size(), System.currentTimeMillis() - splitStartTime);
        return chunks;
    }

    /**
     * 按 Markdown 标题分割。无标题时返回空列表，交由段落分割处理。
     */
    private List<String> splitByMarkdownHeaders(String content) {
        List<String> chunks = new ArrayList<>();

        // 使用正则表达式匹配 Markdown 标题
        Pattern pattern = Pattern.compile("^#{1,6}\\s+.*$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);

        // 检查是否找到至少一个标题
        boolean foundAnyHeader = false;
        int lastEnd = 0;

        while (matcher.find()) {
            // 标记找到标题
            foundAnyHeader = true;

            int start = matcher.start();
            int end = matcher.end();

            // 找到下一行的末尾（包含标题后的内容行）
            int nextNewline = content.indexOf('\n', end);
            if (nextNewline == -1) {
                nextNewline = content.length();
            } else {
                // 继续读取，直到遇到空行或下一个标题
                int checkPos = nextNewline + 1;
                while (checkPos < content.length()) {
                    // 检查是否是空行
                    int lineStart = checkPos;
                    int lineEnd = content.indexOf('\n', checkPos);
                    if (lineEnd == -1) lineEnd = content.length();

                    String line = content.substring(lineStart, lineEnd).trim();

                    // 如果是空行，停止
                    if (line.isEmpty()) {
                        break;
                    }

                    // 如果是下一个标题，停止
                    if (line.startsWith("#")) {
                        break;
                    }

                    // 继续下一行
                    checkPos = lineEnd + 1;
                    nextNewline = lineEnd;
                }
            }

            // 添加上一个片段
            if (lastEnd < start) {
                String chunk = content.substring(lastEnd, start).trim();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }
            }

            // 添加当前标题及其内容
            String titleAndContent = content.substring(start, nextNewline).trim();
            if (!titleAndContent.isEmpty()) {
                chunks.add(titleAndContent);
            }

            lastEnd = nextNewline;
        }

        // 如果没有找到任何 Markdown 标题，返回空列表（让策略 2 接管）
        if (!foundAnyHeader) {
            return chunks;
        }

        // 添加最后一部分
        if (lastEnd < content.length()) {
            String chunk = content.substring(lastEnd).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
        }

        return chunks;
    }

    /**
     * 按空行分割
     */
    private List<String> splitByParagraphs(String content) {
        List<String> chunks = new ArrayList<>();

        // 按两个或更多换行符分割
        String[] paragraphs = content.split("\\n\\s*\\n");

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.length() >= MIN_CHUNK_SIZE) {
                chunks.add(trimmed);
            }
        }

        return chunks;
    }

    /**
     * 按固定大小分割，优先在句子边界处切割
     */
    private List<String> splitByFixedSize(String content) {
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + MAX_CHUNK_SIZE, content.length());

            // 尝试在句子边界处切割
            if (end < content.length()) {
                int lastPeriod = content.lastIndexOf('.', end);
                int lastExclamation = content.lastIndexOf('!', end);
                int lastQuestion = content.lastIndexOf('?', end);
                int lastNewline = content.lastIndexOf('\n', end);

                int bestBreak = Math.max(Math.max(lastPeriod, lastExclamation), Math.max(lastQuestion, lastNewline));

                if (bestBreak > start) {
                    end = bestBreak + 1;
                }
            }

            String chunk = content.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // 已处理到文档末尾 → 结束，无需重叠回退（否则会凭空切出多余的末尾小段）
            if (end >= content.length()) {
                break;
            }

            // 还有剩余内容时才应用重叠回退；确保 start 每次都前进，避免无限循环
            int nextStart = end - CHUNK_OVERLAP;
            if (nextStart <= start) {
                // 如果重叠导致不前进，直接跳到 end
                nextStart = end;
            }
            start = nextStart;
        }

        return chunks;
    }

    /**
     * 余弦相似度（预计算范数版）。
     *
     * <p>仅计算点积；normA 和 normB 已由调用方预计算（查询向量 norm 一次、chunk norm 从缓存取），
     * 消除每次查询对所有 chunk 重复开方求和的 O(N×dim) 浪费。</p>
     *
     * @param a     查询向量
     * @param b     chunk 向量
     * @param normA 预计算的 a 的 L2 范数
     * @param normB 预计算的 b 的 L2 范数
     */
    private double cosineSimilarity(float[] a, float[] b, double normA, double normB) {
        if (a.length != b.length) return 0.0;
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        double denominator = normA * normB;
        return denominator == 0.0 ? 0.0 : dot / denominator;
    }

    /**
     * 格式化为 LLM 上下文提示
     */
    public String formatAsContext(List<String> knowledgeList) {
        if (knowledgeList.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\n=== ").append(I18nService.tr("参考知识库")).append(" ===\n");

        for (int i = 0; i < knowledgeList.size(); i++) {
            context.append("[").append(I18nService.tr("知识片段")).append(" ").append(i + 1).append("]\n");
            context.append(knowledgeList.get(i)).append("\n\n");
        }

        context.append("===============\n");

        return context.toString();
    }
}
