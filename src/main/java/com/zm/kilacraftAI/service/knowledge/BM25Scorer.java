package com.zm.kilacraftAI.service.knowledge;

import java.util.List;
import java.util.Map;

/**
 * BM25 评分工具类
 *
 * <p>提供可复用的 BM25 相关性评分算法，供知识库检索使用。</p>
 * <p>核心公式：score = Σ IDF(q) · (tf·(k1+1)) / (tf + k1·lengthNorm) · weight</p>
 * <p>其中 IDF 采用 BM25+ 形式 ln(1 + (N-df+0.5)/(df+0.5))，恒非负：稀有词加权、高频通用词降权。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-14
 */
public class BM25Scorer {

    /**
     * 计算 BM25 相关性得分
     *
     * @param document         文档文本（原始大小写，方法内部自动转小写）
     * @param keywords         查询关键词列表
     * @param k1               词频饱和参数（建议 1.2-2.0）
     * @param b                文档长度归一化参数（建议 0.5-0.8）
     * @param avgDocLength     平均文档长度（用于归一化）
     * @param totalDocs        知识库总 chunk 数（N，用于 IDF）
     * @param documentFrequency 关键词→文档频率（df，含该词的 chunk 数；可为 null 退化为无 IDF）
     * @return BM25 得分
     */
    public static double score(String document, List<String> keywords, double k1, double b, int avgDocLength, int totalDocs, Map<String, Integer> documentFrequency) {
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
            if (keyword == null || keyword.isEmpty()) {
                continue; // 空关键词不贡献分数，避免 countOccurrences 在空串上无限循环
            }
            int termFreq = countOccurrences(lowerDoc, keyword.toLowerCase());
            if (termFreq > 0) {
                double tfScore = (termFreq * (k1 + 1)) / (termFreq + k1 * lengthNorm);
                int docFreq = documentFrequency != null ? documentFrequency.getOrDefault(keyword, 1) : 1;
                double idf = computeIdf(totalDocs, docFreq);
                score += idf * tfScore * getKeywordWeight(keyword) * 5.0;
            }
        }

        return score;
    }

    /**
     * 计算 BM25+ 形式的 IDF（逆文档频率），恒非负。
     *
     * <p>公式：{@code ln(1 + (N - df + 0.5) / (df + 0.5))}。N=总文档数，df=含该词的文档数。</p>
     * <p>采用 {@code ln(1+x)} 而非经典 {@code ln(x)}：后者在 df &gt; N/2 时为负，
     * 会与外层 {@code score > 0} 过滤冲突（仅命中高频词的文档被判负分丢弃）。Lucene 等现代实现同此。</p>
     * <p>totalDocs ≤ 0（未统计）时返回 1.0，等价不做 IDF 加权。</p>
     *
     * @param totalDocs 总文档数 N
     * @param docFreq    含该词的文档数 df（df ≤ 0 时按 1 处理）
     * @return IDF 值（恒 ≥ 0）
     */
    private static double computeIdf(int totalDocs, int docFreq) {
        if (totalDocs <= 0) {
            return 1.0;
        }
        int df = Math.max(1, docFreq);
        return Math.log(1.0 + ((double) (totalDocs - df + 0.5) / (df + 0.5)));
    }

    /**
     * 无 IDF 重载（IDF 恒为 1.0）。
     */
    public static double score(String document, List<String> keywords, double k1, double b, int avgDocLength) {
        return score(document, keywords, k1, b, avgDocLength, 0, null);
    }

    /**
     * 统计关键词在文本中出现的次数（子串匹配）
     */
    public static int countOccurrences(String text, String keyword) {
        // 空串防御：text.indexOf("", idx) 恒返回 idx，idx+=0 永不前进会导致死循环
        if (text == null || keyword == null || keyword.isEmpty()) {
            return 0;
        }
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
