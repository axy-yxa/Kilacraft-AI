package com.zm.kilacraftAI.knowledge;

import java.util.*;

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
    private final int maxRelevantChunks;
    
    public KnowledgeRetriever(KnowledgeBaseManager knowledgeBase, int maxRelevantChunks) {
        this.knowledgeBase = knowledgeBase;
        this.maxRelevantChunks = maxRelevantChunks;
    }
    
    /**
     * 检索与问题相关的知识
     * 
     * @param question 用户问题
     * @return 相关知识片段列表
     */
    public List<String> retrieveKnowledge(String question) {
        Map<String, String> allKnowledge = knowledgeBase.getAllKnowledge();
        
        if (allKnowledge.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 计算每个知识文件的相关性得分
        Map<String, Double> relevanceScores = new HashMap<>();
        
        for (Map.Entry<String, String> entry : allKnowledge.entrySet()) {
            String fileName = entry.getKey();
            String content = entry.getValue();
            
            double score = calculateRelevance(question, content);
            if (score > 0) {
                relevanceScores.put(fileName, score);
            }
        }
        
        // 按相关性排序
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(relevanceScores.entrySet());
        sortedEntries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        
        // 取前 N 个最相关的
        List<String> relevantKnowledge = new ArrayList<>();
        int count = Math.min(sortedEntries.size(), maxRelevantChunks);
        
        for (int i = 0; i < count; i++) {
            String fileName = sortedEntries.get(i).getKey();
            String content = allKnowledge.get(fileName);
            
            // 添加来源标记
            relevantKnowledge.add("【来源：" + fileName + "】\n" + content);
        }
        
        return relevantKnowledge;
    }
    
    /**
     * 计算问题与内容的相关性得分
     * 
     * @param question 用户问题
     * @param content 知识内容
     * @return 相关性得分
     */
    private double calculateRelevance(String question, String content) {
        if (question == null || question.trim().isEmpty() || 
            content == null || content.trim().isEmpty()) {
            return 0.0;
        }
        
        // 转换为小写进行比较
        String lowerQuestion = question.toLowerCase();
        String lowerContent = content.toLowerCase();
        
        double score = 0.0;
        
        // 1. 完整句子匹配（权重最高）- 如果整句话都在内容中，给高分
        if (lowerContent.contains(lowerQuestion)) {
            score += 10.0;
        }
        
        // 2. 关键词提取和模糊匹配
        // 从问题中提取有意义的关键词（去除常见虚词）
        List<String> keywords = extractKeywords(lowerQuestion);
        
        int matchedKeywords = 0;
        int totalWeight = 0;
        
        for (String keyword : keywords) {
            int weight = getKeywordWeight(keyword);
            totalWeight += weight;
            
            // 精确匹配
            if (lowerContent.contains(keyword)) {
                matchedKeywords += weight;
                score += 2.0; // 精确匹配得分
            } 
            // 模糊匹配：包含关系
            else {
                // 检查是否有关键词的子串匹配
                for (int len = keyword.length() - 1; len >= 2; len--) {
                    String subKeyword = keyword.substring(0, len);
                    if (lowerContent.contains(subKeyword)) {
                        matchedKeywords += weight * 0.5;
                        score += 0.5; // 部分匹配得分
                        break;
                    }
                }
            }
        }
        
        // 3. 计算关键词覆盖率
        if (!keywords.isEmpty() && totalWeight > 0) {
            double coverageRate = (double) matchedKeywords / totalWeight;
            score *= (1.0 + coverageRate); // 覆盖率加成
        }
        
        // 4. 位置加权 - 如果关键词出现在开头，增加权重
        for (String keyword : keywords) {
            if (lowerContent.startsWith(keyword)) {
                score += 1.5;
                break;
            }
        }
        
        // 5. 频率加分 - 关键词出现次数越多，相关性越高
        for (String keyword : keywords) {
            int frequency = countOccurrences(lowerContent, keyword);
            if (frequency > 1) {
                score += Math.log(frequency) * 0.3;
            }
        }
        
        return score;
    }
    
    /**
     * 从问题中提取关键词（去除常见虚词）
     */
    private List<String> extractKeywords(String question) {
        List<String> keywords = new ArrayList<>();
        
        // 移除常见的无意义词汇
        String[] stopWords = {"的", "了", "吗", "呢", "啊", "呀", "怎么", "如何", "什么", "哪里", "哪个", "何", "怎样", "为啥", "咋"};
        
        // 先尝试按空格分词（如果有空格的话）
        String[] words = question.split("\\s+");
        
        for (String word : words) {
            // 跳过停用词和单个字符
            boolean isStopWord = false;
            for (String stop : stopWords) {
                if (word.equals(stop)) {
                    isStopWord = true;
                    break;
                }
            }
            
            if (!isStopWord && word.length() >= 2) {
                keywords.add(word);
            }
        }
        
        // 如果没有提取到关键词（纯中文），使用 n-gram 方法
        if (keywords.isEmpty()) {
            // 提取 2-gram 和 3-gram
            for (int i = 0; i < question.length() - 1; i++) {
                if (i + 2 <= question.length()) {
                    String biGram = question.substring(i, i + 2);
                    if (!biGram.matches(".*[的了吗呢啊呀].*")) {
                        keywords.add(biGram);
                    }
                }
                if (i + 3 <= question.length()) {
                    String triGram = question.substring(i, i + 3);
                    if (!triGram.matches(".*[的了吗呢啊呀].*")) {
                        keywords.add(triGram);
                    }
                }
            }
        }
        
        return keywords;
    }
    
    /**
     * 获取关键词的权重
     */
    private int getKeywordWeight(String keyword) {
        // 较长的词权重更高
        if (keyword.length() >= 4) {
            return 3;
        } else if (keyword.length() >= 3) {
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
