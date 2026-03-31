package com.zm.kilacraftAI.knowledge;

import java.util.*;
import java.util.regex.*;

/**
 * 知识检索器（标准 RAG 方案）
 *
 * <p>从知识库中检索与问题相关的知识片段</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-25
 */
public class KnowledgeRetriever {

    private final KnowledgeBaseManager knowledgeBase;
    private final int maxRelevantChunks;

    // 分段配置
    private final int MAX_CHUNK_SIZE;
    private final int MIN_CHUNK_SIZE;
    private final int CHUNK_OVERLAP;

    public KnowledgeRetriever(KnowledgeBaseManager knowledgeBase, int maxRelevantChunks,
                              int maxChunkSize, int minChunkSize, int chunkOverlap) {
        this.knowledgeBase = knowledgeBase;
        this.maxRelevantChunks = maxRelevantChunks;
        this.MAX_CHUNK_SIZE = maxChunkSize;
        this.MIN_CHUNK_SIZE = minChunkSize;
        this.CHUNK_OVERLAP = chunkOverlap;
    }

    /**
     * 检索与问题相关的知识（标准 RAG 方案 + 缓存优化）
     *
     * @param question 用户问题
     * @return 相关知识片段列表
     */
    public List<String> retrieveKnowledge(String question) {
        long startTime = System.currentTimeMillis();
        Map<String, String> allKnowledge = knowledgeBase.getAllKnowledge();
        
        if (allKnowledge.isEmpty()) {
            return Collections.emptyList();
        }
            
        // 【标准 RAG】提取有意义的关键词
        List<String> keywords = extractKeywords(question);
            
        if (knowledgeBase.isDebugMode()) {
            knowledgeBase.logInfo("[DEBUG] [知识库] 提取关键词：" + keywords);
        }
            
        // 存储所有片段及其得分
        List<KnowledgeChunk> chunkScores = new ArrayList<>();
        int totalChunks = 0;
            
        for (Map.Entry<String, String> entry : allKnowledge.entrySet()) {
            String fileName = entry.getKey();
            String content = entry.getValue();
                
            // 尝试从缓存获取分段
            List<String> chunks = knowledgeBase.getChunkCache(fileName);
                
            // 如果缓存不存在，重新分段并缓存
            if (chunks == null) {
                long cacheStartTime = System.currentTimeMillis();
                chunks = splitIntoChunks(content, fileName);
                knowledgeBase.setChunkCache(fileName, chunks);
                    
                if (knowledgeBase.isDebugMode()) {
                    long cacheTime = System.currentTimeMillis() - cacheStartTime;
                    knowledgeBase.logInfo("[DEBUG] [知识库缓存] 文件：" + fileName + " - 首次分段并缓存，耗时 " + cacheTime + "ms");
                }
            } else {
                if (knowledgeBase.isDebugMode()) {
                    knowledgeBase.logInfo("[DEBUG] [知识库缓存] 文件：" + fileName + " - 使用缓存的分段（" + chunks.size() + " 个片段）");
                }
            }
                
            totalChunks += chunks.size();
                
            // 【标准 RAG】计算每个片段与问题的相关性得分
            for (String chunk : chunks) {
                double score = calculateRelevance(question, chunk, keywords);
                if (score > 0) {
                    chunkScores.add(new KnowledgeChunk(fileName, chunk, score));
                }
            }
        }
    
        // 按得分排序
        chunkScores.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
    
        // 取前 N 个最相关的片段
        List<String> relevantKnowledge = new ArrayList<>();
        int count = Math.min(chunkScores.size(), maxRelevantChunks);
    
        for (int i = 0; i < count; i++) {
            relevantKnowledge.add(chunkScores.get(i).getContent());
        }
    
        long endTime = System.currentTimeMillis();
    
        // 输出分段统计日志
        if (knowledgeBase.isDebugMode()) {
            knowledgeBase.logInfo("[DEBUG] [知识库] 检索耗时：" + (endTime - startTime) + "ms");
            knowledgeBase.logInfo("[DEBUG] [知识库] 文件总数：" + allKnowledge.size() + ", 总片段数：" + totalChunks);
            knowledgeBase.logInfo("[DEBUG] [知识库] 匹配片段：" + chunkScores.size() + ", 返回得分最高的 " + count + " 条");
    
            // 输出匹配的详细信息
            for (int i = 0; i < chunkScores.size(); i++) {
                KnowledgeChunk chunk = chunkScores.get(i);
                String preview = chunk.getContent().length() > 100 ? chunk.getContent().substring(0, 100) + "..." : chunk.getContent();
                knowledgeBase.logInfo("[DEBUG] [知识库] 匹配 #" + (i + 1) + " - 文件：" + chunk.getFileName() + ", 得分：" + String.format("%.2f", chunk.getScore()) + ", 长度：" + chunk.getContent().length() + " 字符");
                knowledgeBase.logInfo("[DEBUG] [知识库] " + preview.replace("\n", "\\n"));
            }
        }
            
        return relevantKnowledge;
    }

    /**
     * 【标准 RAG】从问题中提取关键词（去除常见虚词和标点）
     * 
     * @param question 用户问题
     * @return 关键词列表
     */
    private List<String> extractKeywords(String question) {
        List<String> keywords = new ArrayList<>();
        
        if (question == null || question.trim().isEmpty()) {
            return keywords;
        }
        
        // 移除标点符号（包括中英文标点）
        String cleanQuestion = question.replaceAll("[\\p{Punct}\\s，。？！,.!?]+", "");
        
        // 停用词表（常见的无意义词汇）
        Set<String> stopWords = Set.of(
            "的", "了", "吗", "呢", "啊", "呀", "吧", "嘛",
            "怎么", "如何", "怎样", "为啥", "为什么", "哪", "哪里", "哪儿",
            "什么", "哪些", "哪个", "何", "咋",
            "做", "作", "搞", "弄", "弄法", "制作", "合成", "配方",
            "怎么做", "如何做", "怎样做", "咋做", "咋弄", "做法", "制作方法",
            "是什么", "有哪些", "在哪", "怎么弄", "怎么搞"
        );
        
        // 【关键修复】先移除停用词，再提取 n-gram
        String noStopText = cleanQuestion;
        for (String stop : stopWords) {
            noStopText = noStopText.replace(stop, " "); // 替换为空格而不是直接删除
        }
        // 移除多余空格
        noStopText = noStopText.replaceAll("\\s+", "").trim();
        
        if (noStopText.isEmpty()) {
            return keywords;
        }
        
        // 从清理后的文本中提取 n-gram
        for (int len = Math.min(4, noStopText.length()); len >= 2; len--) {
            for (int i = 0; i <= noStopText.length() - len; i++) {
                String nGram = noStopText.substring(i, i + len);
                
                // 避免添加重复或包含关系的词
                boolean shouldAdd = true;
                for (String existing : keywords) {
                    if (existing.contains(nGram)) {
                        shouldAdd = false;
                        break;
                    }
                    if (nGram.contains(existing) && nGram.length() > existing.length()) {
                        keywords.remove(existing);
                    }
                }
                
                if (shouldAdd) {
                    keywords.add(nGram);
                }
            }
        }
        return keywords;
    }

    /**
     * 【标准 RAG】计算问题与内容的相关性得分
     * 
     * @param question 用户问题
     * @param content  知识内容
     * @param keywords 提取的关键词
     * @return 相关性得分
     */
    private double calculateRelevance(String question, String content, List<String> keywords) {
        if (question == null || question.trim().isEmpty() || content == null || content.trim().isEmpty()) {
            return 0.0;
        }

        // 转换为小写进行比较
        String lowerQuestion = question.toLowerCase();
        String lowerContent = content.toLowerCase();

        double score = 0.0;

        // 1. 完整问题匹配（最高优先级）
        if (lowerContent.contains(lowerQuestion)) {
            score += 50.0;
        }

        // 2. 关键词匹配
        int matchedKeywords = 0;
        int totalWeight = 0;
        boolean hasExactMatch = false;
        
        for (String keyword : keywords) {
            int weight = getKeywordWeight(keyword);
            totalWeight += weight;

            // 精确匹配整个词
            if (lowerContent.contains(keyword)) {
                matchedKeywords += weight;
                score += 5.0;
                hasExactMatch = true;
            }
            // 模糊匹配：子串匹配
            else {
                for (int len = keyword.length() - 1; len >= 2; len--) {
                    String subKeyword = keyword.substring(0, len);
                    if (lowerContent.contains(subKeyword)) {
                        matchedKeywords += (int) (weight * 0.5);
                        score += 1.0;
                        break;
                    }
                }
            }
        }

        // 3. 计算关键词覆盖率
        if (!keywords.isEmpty() && totalWeight > 0) {
            double coverageRate = (double) matchedKeywords / totalWeight;
            score *= (1.0 + coverageRate * 2);
        }

        // 4. 位置加权 - 如果关键词出现在开头或标题中
        for (String keyword : keywords) {
            // 开头匹配
            if (lowerContent.startsWith(keyword)) {
                score += 5.0;
                break;
            }

            // 标题匹配（# 开头的行）
            String[] lines = lowerContent.split("\\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("#") && line.contains(keyword)) {
                    score += 15.0; // 标题中的关键词权重更高
                    
                    // 额外奖励：如果标题包含完整的多词关键词
                    if (keyword.length() >= 3) {
                        score += 10.0;
                    }
                    break;
                }
            }
        }

        // 5. 频率加分 - 但有上限
        for (String keyword : keywords) {
            int frequency = countOccurrences(lowerContent, keyword);
            if (frequency > 1) {
                score += Math.sqrt(frequency) * 0.5;
            }
        }

        // 6. 精确匹配额外奖励
        if (hasExactMatch) {
            score += 10.0;
        }

        return score;
    }

    /**
     * 获取关键词的权重（较长的词权重更高）
     */
    private int getKeywordWeight(String keyword) {
        if (keyword.length() >= 4) {
            return 3;
        } else if (keyword.length() == 3) {
            return 2;
        } else {
            return 1;
        }
    }

    /**
     * 统计关键词在文本中出现的次数
     */
    private int countOccurrences(String text, String keyword) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(keyword, index)) != -1) {
            count++;
            index += keyword.length();
        }
        return count;
    }

    /**
     * 将长文本分割成合适大小的片段
     *
     * @param content  原始内容
     * @param fileName 文件名（用于日志）
     * @return 片段列表
     */
    private List<String> splitIntoChunks(String content, String fileName) {
        List<String> chunks = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            return chunks;
        }

        long splitStartTime = System.currentTimeMillis();

        // 策略 1: 按 Markdown 标题分割 (# 或 ## 等)
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

            if (knowledgeBase.isDebugMode()) {
                long splitTime = System.currentTimeMillis() - splitStartTime;
                knowledgeBase.logInfo("[DEBUG] [知识库分段] 文件：" + fileName + " - 使用 Markdown 标题分割，得到 " + chunks.size() + " 个片段，耗时 " + splitTime + "ms");
            }
            return chunks;
        }

        // 策略 2: 按段落分割（空行分隔）
        chunks = splitByParagraphs(content);
        if (!chunks.isEmpty()) {
            if (knowledgeBase.isDebugMode()) {
                long splitTime = System.currentTimeMillis() - splitStartTime;
                knowledgeBase.logInfo("[DEBUG] [知识库分段] 文件：" + fileName + " - 使用段落分割，得到 " + chunks.size() + " 个片段，耗时 " + splitTime + "ms");
            }
            return chunks;
        }

        // 策略 3: 如果以上都失败，按固定大小分割
        chunks = splitByFixedSize(content);

        if (knowledgeBase.isDebugMode()) {
            long splitTime = System.currentTimeMillis() - splitStartTime;
            knowledgeBase.logInfo("[DEBUG] [知识库分段] 文件：" + fileName + " - 使用固定大小分割，得到 " + chunks.size() + " 个片段，耗时 " + splitTime + "ms");
        }
        return chunks;
    }

    /**
     * 按 Markdown 标题分割（优化版：包含标题和内容）
     */
    private List<String> splitByMarkdownHeaders(String content) {
        List<String> chunks = new ArrayList<>();

        // 使用正则表达式匹配 Markdown 标题
        Pattern pattern = Pattern.compile("^#{1,6}\\s+.*$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);

        int lastEnd = 0;
        while (matcher.find()) {
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
     * 按段落分割（空行分隔）
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
     * 按固定大小分割（最后的手段）
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

            start = end - CHUNK_OVERLAP; // 添加重叠部分
            if (start >= content.length()) {
                break;
            }
        }

        return chunks;
    }

    /**
     * 将检索到的知识格式化为上下文提示
     *
     * @param knowledgeList 知识片段列表
     * @return 格式化后的上下文文本
     */
    public String formatAsContext(List<String> knowledgeList) {
        if (knowledgeList.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\n\n=== 参考知识库 ===\n");

        for (int i = 0; i < knowledgeList.size(); i++) {
            context.append("[知识片段 ").append(i + 1).append("]\n");
            context.append(knowledgeList.get(i)).append("\n\n");
        }

        context.append("===============\n");

        return context.toString();
    }
}
