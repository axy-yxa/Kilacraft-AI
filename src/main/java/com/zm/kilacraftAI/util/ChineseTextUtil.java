package com.zm.kilacraftAI.util;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.dictionary.CustomDictionary;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 中文文本处理工具类
 * <p>提供通用的中文分词、关键词提取等功能，用于知识库检索增强</p>
 * 
 * @author Zm_Mmm
 * @since 2026-04-06
 */
public class ChineseTextUtil {
    
    /**
     * 初始化自定义词典
     * <p>在插件启动时调用，添加专业术语到 HanLP 词典</p>
     * 
     * @param customWords 自定义词汇列表
     */
    public static void initCustomDictionary(List<String> customWords) {
        if (customWords == null || customWords.isEmpty()) {
            return;
        }
        
        // 将自定义词汇添加到 HanLP 的自定义词典中
        for (String word : customWords) {
            if (word != null && !word.trim().isEmpty()) {
                CustomDictionary.add(word.trim());
            }
        }
    }
    
    /**
     * 对中文文本进行智能分词
     * <p>使用 HanLP 进行准确的中文分词，过滤无意义单字和停用词</p>
     * 
     * @param text 待分词的文本
     * @return 分词结果列表
     */
    public static List<String> segment(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        
        return HanLP.segment(text).stream()
            .map(term -> term.word)  // Term.word 是公共字段
            .filter(word -> word.length() > 1)  // 过滤单字（如"的"、"了"）
            .filter(word -> !isStopWord(word))   // 过滤停用词
            .collect(Collectors.toList());
    }
    
    /**
     * 提取文本中的关键词
     * <p>基于 TF-IDF 算法提取最重要的关键词，并结合规则过滤噪音</p>
     * 
     * @param text 待提取关键词的文本
     * @param topK 返回前 K 个关键词
     * @return 关键词列表
     */
    public static List<String> extractKeywords(String text, int topK) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        
        // 使用 HanLP 的 TF-IDF 提取候选关键词
        List<String> candidates = HanLP.extractKeyword(text, topK * 2);  // 多提取一些，后续过滤
        
        // 过滤噪音词
        return candidates.stream()
            .filter(kw -> kw.length() >= 2)                    // 过滤单字
            .filter(kw -> !containsEnglish(kw))                // 过滤纯英文/代码变量(如 step_)
            .filter(kw -> !isGenericWord(kw))                  // 过滤通用词(如“玩家”、“物品”)
            .limit(topK)                                        // 取前 topK 个
            .collect(Collectors.toList());
    }
    
    /**
     * 判断是否包含英文字符或下划线（代码变量特征）
     */
    private static boolean containsEnglish(String word) {
        return word.matches(".*[a-zA-Z_].*");
    }
    
    /**
     * 判断是否为通用词
     */
    private static boolean isGenericWord(String word) {
        Set<String> genericWords = Set.of(
            "玩家", "物品", "信息", "内容", "数据", "情况", "结果",
            "执行", "输入", "输出", "任务", "目标", "查询", "检查",
            "当前", "以下", "以上", "相关", "具体", "详细"
        );
        return genericWords.contains(word);
    }
    
    /**
     * 判断是否为停用词
     * 
     * @param word 待判断的词
     * @return true 如果是停用词
     */
    private static boolean isStopWord(String word) {
        return word.matches("^[的得地了着过吗呢吧啊呀哦嗯嘛啦呗哇哈嘿哟嚯]$") ||
               word.matches("^(这个|那个|哪些|什么|怎么|如何|是否|可以|可能|应该|需要|想要)$");
    }
    
    /**
     * 将文本转换为搜索查询字符串
     * <p>使用 TF-IDF 算法提取最重要的关键词，而非简单分词</p>
     * 
     * @param text 原始文本
     * @return 用空格分隔的关键词字符串，适合用于知识库检索
     */
    public static String toSearchQuery(String text) {
        List<String> keywords = extractKeywords(text, 10);
        return String.join(" ", keywords);
    }
}
