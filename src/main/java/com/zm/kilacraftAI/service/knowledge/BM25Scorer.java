package com.zm.kilacraftAI.service.knowledge;

import java.util.List;

/**
 * BM25 评分工具类
 *
 * <p>提供可复用的 BM25 相关性评分算法，供知识库检索使用。</p>
 * <p>核心公式：TF-Score = (tf * (k1 + 1)) / (tf + k1 * lengthNorm)</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-14
 */
public class BM25Scorer {

    /**
     * 计算 BM25 相关性得分
     *
     * @param document     文档文本（原始大小写，方法内部自动转小写）
     * @param keywords     查询关键词列表
     * @param k1           词频饱和参数（建议 1.2-2.0）
     * @param b            文档长度归一化参数（建议 0.5-0.8）
     * @param avgDocLength 平均文档长度（用于归一化）
     * @return BM25 得分
     */
    public static double score(String document, List<String> keywords, double k1, double b, int avgDocLength) {
        if (document == null || document.isEmpty() || keywords == null || keywords.isEmpty()) {
            return 0.0;
        }

        String lowerDoc = document.toLowerCase();
        int docLength = lowerDoc.length();

        // 优化：对短文档降低长度惩罚，避免命令文档得分过低
        // 如果文档很短（< 50字符），使用最小的长度归一化
        double effectiveLength = Math.max(docLength, 50);  // 最小按 50 字符计算
        double lengthNorm = 1 - b + b * (effectiveLength / avgDocLength);

        double score = 0.0;
        for (String keyword : keywords) {
            int termFreq = countOccurrences(lowerDoc, keyword.toLowerCase());
            if (termFreq > 0) {
                double tfScore = (termFreq * (k1 + 1)) / (termFreq + k1 * lengthNorm);
                score += tfScore * getKeywordWeight(keyword) * 5.0;
            }
        }

        return score;
    }

    /**
     * 统计关键词在文本中出现的次数（子串匹配）
     */
    public static int countOccurrences(String text, String keyword) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(keyword, index)) != -1) {
            count++;
            index += keyword.length();
        }
        return count;
    }

    /**
     * 获取关键词的权重（较长的词权重更高，信息量更大）
     */
    public static int getKeywordWeight(String keyword) {
        if (keyword.length() >= 4) {
            return 3;
        } else if (keyword.length() == 3) {
            return 2;
        } else {
            return 1;
        }
    }
}
