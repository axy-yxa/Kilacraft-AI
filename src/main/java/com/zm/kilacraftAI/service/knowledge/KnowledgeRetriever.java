package com.zm.kilacraftAI.service.knowledge;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.i18n.TextProcessorFactory;
import com.zm.kilacraftAI.model.knowledge.KnowledgeChunk;

import java.util.*;
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

    // 关键词提取配置
    private int keywordTopK;

    // BM25 评分算法参数
    private double bm25K1;  // 词频饱和参数
    private double bm25B;   // 文档长度归一化参数

    // 分段配置
    private int MAX_CHUNK_SIZE;
    private int MIN_CHUNK_SIZE;
    private int CHUNK_OVERLAP;

    // Embedding 语义检索
    private volatile EmbeddingService embeddingService;
    private volatile boolean embeddingEnabled;
    private volatile double embeddingMinSimilarity;

    // 检索结果过滤与融合
    private volatile double noiseFloor;
    private volatile double relativeThreshold;
    private volatile double rrfK;

    // BM25 长度归一化
    private volatile int bm25AvgDocLengthOverride;
    private volatile int avgDocLength;

    public KnowledgeRetriever(KnowledgeBaseManager knowledgeBase, int maxRelevantChunks, int maxChunkSize, int minChunkSize, int chunkOverlap, int keywordTopK, double bm25K1, double bm25B) {
        this.knowledgeBase = knowledgeBase;
        applyConfig(maxRelevantChunks, maxChunkSize, minChunkSize, chunkOverlap, keywordTopK, bm25K1, bm25B);
    }

    /**
     * 刷新配置参数（热重载时由 reload 流程调用）
     * <p>知识库内容的刷新由 {@link KnowledgeBaseManager#reload()} 处理，
     * 此方法仅更新算法调优参数（分段大小、BM25、阈值等）。</p>
     *
     * @param maxRelevantChunks 最大返回片段数
     * @param maxChunkSize      最大分段大小
     * @param minChunkSize      最小分段大小
     * @param chunkOverlap      段间重叠字符数
     * @param keywordTopK       关键词提取数量
     * @param bm25K1            BM25 k1 参数
     * @param bm25B             BM25 b 参数
     */
    public void refreshConfig(int maxRelevantChunks, int maxChunkSize, int minChunkSize, int chunkOverlap, int keywordTopK, double bm25K1, double bm25B) {
        applyConfig(maxRelevantChunks, maxChunkSize, minChunkSize, chunkOverlap, keywordTopK, bm25K1, bm25B);
    }

    /**
     * 设置 Embedding 服务
     */
    public void setEmbeddingService(EmbeddingService embeddingService, boolean embeddingEnabled, double embeddingMinSimilarity) {
        this.embeddingService = embeddingService;
        this.embeddingEnabled = embeddingEnabled;
        this.embeddingMinSimilarity = embeddingMinSimilarity;
    }

    /**
     * 设置检索过滤与 BM25 长度归一化参数（热重载时调用）。
     *
     * @param noiseFloor               噪声地板
     * @param relativeThreshold        相对阈值
     * @param rrfK                     RRF 融合常数
     * @param bm25AvgDocLengthOverride BM25 平均文档长度覆盖（0 = 自动统计）
     */
    public void setRetrievalConfig(double noiseFloor, double relativeThreshold, double rrfK, int bm25AvgDocLengthOverride) {
        this.noiseFloor = noiseFloor;
        this.relativeThreshold = relativeThreshold;
        this.rrfK = rrfK;
        this.bm25AvgDocLengthOverride = bm25AvgDocLengthOverride;
        computeAvgDocLength();   // 覆盖项变更后重算（chunkCache 未变也需重算以应用覆盖项）
    }

    /**
     * 统计全量 chunk 的平均字符长度，用于 BM25 长度归一化
     * 在 {@link #buildChunkCache()} 之后、以及知识库 reload 后调用。
     */
    public void computeAvgDocLength() {
        if (bm25AvgDocLengthOverride > 0) {
            this.avgDocLength = bm25AvgDocLengthOverride;
            PluginLoggerUtil.debug("知识库", "avgDocLength 使用配置覆盖：{}", this.avgDocLength);
            return;
        }
        Map<String, List<String>> allChunks = knowledgeBase.getAllChunkCache();
        long total = 0;
        int count = 0;
        for (List<String> chunks : allChunks.values()) {
            for (String chunk : chunks) {
                total += chunk.length();
                count++;
            }
        }
        this.avgDocLength = count > 0 ? (int) (total / count) : 0;
        PluginLoggerUtil.debug("知识库", "avgDocLength 统计：{} 个片段，平均 {} 字符", count, this.avgDocLength);
    }

    /**
     * BM25 长度归一化用的平均文档长度。
     * 优先配置覆盖项；其次运行时统计值；都不可用时回退原硬编码 500（保证未统计前仍可运行）。
     */
    private int effectiveAvgDocLength() {
        if (bm25AvgDocLengthOverride > 0) {
            return bm25AvgDocLengthOverride;
        }
        return avgDocLength > 0 ? avgDocLength : 500;
    }

    private void applyConfig(int maxRelevantChunks, int maxChunkSize, int minChunkSize, int chunkOverlap, int keywordTopK, double bm25K1, double bm25B) {
        this.maxRelevantChunks = maxRelevantChunks;
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
     * 检索与问题相关的知识。
     *
     * <p>未启用 Embedding 时走纯 BM25；启用且可用时走 BM25 + Embedding 的 RRF 融合。
     * 两条路最终都经 {@link #applySoftThreshold} 过滤后返回。</p>
     */
    public List<String> retrieveKnowledge(String question) {
        long startTime = System.currentTimeMillis();
        Map<String, String> allKnowledge = knowledgeBase.getAllKnowledge();
        if (allKnowledge.isEmpty()) {
            return Collections.emptyList();
        }

        // BM25 评分（恒做；同时产出 totalChunks 用于日志）
        ScoreResult bm25 = scoreAllByBM25(question, allKnowledge);

        boolean embedAvail = embeddingEnabled && embeddingService != null && embeddingService.isAvailable();
        List<KnowledgeChunk> ranked;
        double noiseFloorForFilter;

        if (embedAvail) {
            PluginLoggerUtil.debug("知识库", "使用 BM25 + Embedding 融合检索");
            List<KnowledgeChunk> embedList = scoreAllByEmbedding(question, allKnowledge);
            if (embedList != null && !embedList.isEmpty()) {
                ranked = fuseByRRF(bm25.chunks(), embedList, rrfK);
                // RRF 得分恒 > 0，纯靠相对阈值
                noiseFloorForFilter = 0.0;
            } else if (embedList == null) {
                // 查询向量获取失败 → 退化为纯 BM25
                ranked = bm25.chunks();
                noiseFloorForFilter = noiseFloor;
            } else {
                // 退回词法路 + 噪声地板（与未启用 Embedding 行为一致）
                PluginLoggerUtil.debug("知识库", "Embedding 无 ≥min_similarity 片段，退回 BM25 + 噪声地板");
                ranked = bm25.chunks();
                noiseFloorForFilter = noiseFloor;
            }
        } else {
            if (embeddingEnabled) {
                PluginLoggerUtil.debug("知识库", "Embedding 不可用，降级到 BM25");
            }
            ranked = bm25.chunks();
            noiseFloorForFilter = noiseFloor;
        }

        List<String> result = applySoftThreshold(ranked, noiseFloorForFilter, relativeThreshold, maxRelevantChunks);
        logRetrievalStats(startTime, allKnowledge.size(), bm25.totalChunks(), ranked.size(), result.size(), ranked);
        return result;
    }

    /**
     * 评分结果：按得分降序的片段列表 + 本次遍历的总片段数（用于日志）。
     */
    private record ScoreResult(List<KnowledgeChunk> chunks, int totalChunks) {
    }

    /**
     * 用 BM25 对全部片段评分，返回按得分降序的列表（得分 > 0 才入列）。
     */
    private ScoreResult scoreAllByBM25(String question, Map<String, String> allKnowledge) {
        List<String> keywords = extractKeywords(question);
        PluginLoggerUtil.debug("知识库", "提取关键词：{}", keywords);

        // 计算 IDF 用的文档频率 df：每个关键词出现在多少个 chunk 里（子串包含，小写）。
        // 子串匹配无法预建倒排表，故按查询关键词实时统计一遍。
        Map<String, Integer> documentFrequency = new HashMap<>();
        int totalChunks = 0;
        for (Map.Entry<String, String> entry : allKnowledge.entrySet()) {
            List<String> chunks = getOrSplitChunks(entry.getValue(), entry.getKey());
            totalChunks += chunks.size();
            for (String chunk : chunks) {
                String lower = chunk.toLowerCase();
                for (String keyword : keywords) {
                    if (!keyword.isEmpty() && lower.contains(keyword.toLowerCase())) {
                        documentFrequency.merge(keyword, 1, Integer::sum);
                    }
                }
            }
        }

        List<KnowledgeChunk> chunkScores = new ArrayList<>();
        for (Map.Entry<String, String> entry : allKnowledge.entrySet()) {
            for (String chunk : getOrSplitChunks(entry.getValue(), entry.getKey())) {
                double score = calculateRelevance(question, chunk, keywords, documentFrequency, totalChunks);
                if (score > 0) {
                    chunkScores.add(new KnowledgeChunk(entry.getKey(), chunk, score));
                }
            }
        }
        chunkScores.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return new ScoreResult(chunkScores, totalChunks);
    }

    /**
     * 用 Embedding 余弦相似度对全部片段评分，返回按相似度降序的列表。
     *
     * @return 评分列表；若获取查询向量失败返回 null（调用方据此退化为纯 BM25）
     */
    private List<KnowledgeChunk> scoreAllByEmbedding(String question, Map<String, String> allKnowledge) {
        float[] queryVec = embeddingService.getEmbedding(question);
        if (queryVec == null) {
            PluginLoggerUtil.warn("知识库", "获取问题 Embedding 失败，降级到 BM25");
            return null;
        }

        // 查询向量的 L2 范数：整个检索过程只算一次，不再对每个 chunk 重算
        double queryNormSumSq = 0.0;
        for (float v : queryVec) queryNormSumSq += (double) v * v;
        double queryNorm = Math.sqrt(queryNormSumSq);

        // 预过滤：低于 min_similarity 的片段视为语义无关，不参与 Embedding 路排名
        double embedThreshold = embeddingMinSimilarity > 0 ? embeddingMinSimilarity : 0.0;

        List<KnowledgeChunk> chunkScores = new ArrayList<>();
        for (Map.Entry<String, String> entry : allKnowledge.entrySet()) {
            String fileName = entry.getKey();
            List<String> chunks = getOrSplitChunks(entry.getValue(), fileName);
            for (String chunk : chunks) {
                float[] chunkVec = embeddingService.getEmbedding(chunk);
                if (chunkVec == null) continue;
                double chunkNorm = embeddingService.getChunkNorm(chunk);
                double similarity = cosineSimilarity(queryVec, chunkVec, queryNorm, chunkNorm);
                if (similarity >= embedThreshold) {
                    chunkScores.add(new KnowledgeChunk(fileName, chunk, similarity));
                }
            }
        }
        chunkScores.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return chunkScores;
    }

    /**
     * 用 RRF（Reciprocal Rank Fusion）融合两路评分结果。
     *
     * <p>RRF(d) = 1/(k + rank_bm25(d)) + 1/(k + rank_embed(d))，按排名融合、与得分尺度无关。
     * 两路覆盖同一批片段，故每个片段在两路各有一个名次（未在某路出现则该路不计）。</p>
     *
     * @param bm25List  BM25 路评分（已降序）
     * @param embedList Embedding 路评分（已降序）
     * @param k         平滑常数（默认 60）
     * @return 按 RRF 得分降序的融合列表
     */
    private List<KnowledgeChunk> fuseByRRF(List<KnowledgeChunk> bm25List, List<KnowledgeChunk> embedList, double k) {
        Map<String, Integer> rankBm25 = buildRankIndex(bm25List);
        Map<String, Integer> rankEmbed = buildRankIndex(embedList);

        // 并集 key → 任一路的代表片段（同一片段两路的 fileName/content 一致）
        Map<String, KnowledgeChunk> byKey = new LinkedHashMap<>();
        for (KnowledgeChunk c : bm25List) byKey.putIfAbsent(chunkKey(c), c);
        for (KnowledgeChunk c : embedList) byKey.putIfAbsent(chunkKey(c), c);

        List<KnowledgeChunk> fused = new ArrayList<>(byKey.size());
        for (Map.Entry<String, KnowledgeChunk> e : byKey.entrySet()) {
            String key = e.getKey();
            double rrf = 0.0;
            if (rankBm25.containsKey(key)) rrf += 1.0 / (k + rankBm25.get(key));
            if (rankEmbed.containsKey(key)) rrf += 1.0 / (k + rankEmbed.get(key));
            KnowledgeChunk orig = e.getValue();
            fused.add(new KnowledgeChunk(orig.getFileName(), orig.getContent(), rrf));
        }
        fused.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return fused;
    }

    /**
     * 片段复合键：文件名 + 分隔符 + 正文（避免跨文件相同正文被误合并）。
     */
    private static String chunkKey(KnowledgeChunk c) {
        return c.getFileName() + "|" + c.getContent();
    }

    /**
     * 按已降序列表构建 key → 名次（1 起）索引。
     */
    private static Map<String, Integer> buildRankIndex(List<KnowledgeChunk> sortedDesc) {
        Map<String, Integer> rank = new HashMap<>(sortedDesc.size() * 2);
        for (int i = 0; i < sortedDesc.size(); i++) {
            rank.putIfAbsent(chunkKey(sortedDesc.get(i)), i + 1);
        }
        return rank;
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
     * BM25 相关性评分。
     */
    private double calculateRelevance(String question, String content, List<String> keywords, Map<String, Integer> documentFrequency, int totalDocs) {
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

        // BM25 评分（含 IDF：稀有词加权、高频通用词降权）
        score += BM25Scorer.score(content, keywords, bm25K1, bm25B, effectiveAvgDocLength(), totalDocs, documentFrequency);

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
     * 软阈值 / 自适应阈值过滤
     *
     * <p>{@code floor = max(noiseFloor, top × relativeThreshold)}，其中 top 为最高分。
     * 因入参已按得分降序、且 {@code top × relativeThreshold ≤ top}，故 {@code floor ≤ top ⟺ top ≥ noiseFloor}：
     * 最高分过噪声地板时，最高分片段必 {@code ≥ floor}，数学上保证至少返回 1 条；
     * 最高分都低于噪声地板时返回空（纯噪声）。无需额外"兜底返回 1"逻辑。</p>
     *
     * @param sortedDesc        按得分降序的片段列表
     * @param noiseFloor        噪声地板（BM25 路为 retrieval.noise_floor；Embedding 路为 min_similarity）
     * @param relativeThreshold 相对阈值（默认 0.5）
     * @param maxResults        最大返回数（max_relevant_chunks）
     */
    private List<String> applySoftThreshold(List<KnowledgeChunk> sortedDesc, double noiseFloor, double relativeThreshold, int maxResults) {
        if (sortedDesc == null || sortedDesc.isEmpty()) {
            return Collections.emptyList();
        }
        double top = sortedDesc.get(0).getScore();
        double floor = Math.max(noiseFloor, top * relativeThreshold);

        List<String> out = new ArrayList<>();
        for (KnowledgeChunk chunk : sortedDesc) {
            if (out.size() >= maxResults) {
                break;
            }
            if (chunk.getScore() < floor) {
                PluginLoggerUtil.debug("知识库", "软阈值命中（floor={}），停止返回", String.format("%.4f", floor));
                break;   // 降序，后续只会更低
            }
            out.add(chunk.getContent());
        }
        return out;
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
