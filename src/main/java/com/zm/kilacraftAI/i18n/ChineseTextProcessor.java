package com.zm.kilacraftAI.i18n;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.dictionary.CustomDictionary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
public class ChineseTextProcessor implements TextProcessor {

    @Override
    public void initCustomDictionary(List<String> customWords) {
        initCustomDictionaryStatic(customWords);
    }

    /**
     * 初始化自定义词典
     * <p>添加专业术语到 HanLP 词典</p>
     */
    private static void initCustomDictionaryStatic(List<String> customWords) {
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

    @Override
    public List<String> segment(String text) {
        return segmentStatic(text);
    }

    @Override
    public List<String> extractKeywords(String text, int topK) {
        return extractKeywordsStatic(text, topK);
    }

    @Override
    public String toSearchQuery(String text, int topK) {
        return toSearchQueryStatic(text, topK);
    }

    /**
     * 对中文文本进行智能分词
     * <p>使用 HanLP 进行准确的中文分词，过滤无意义单字和停用词</p>
     *
     * @param text 待分词的文本
     * @return 分词结果列表
     */
    private static List<String> segmentStatic(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        return HanLP.segment(text).stream().map(term -> term.word)  // Term.word 是公共字段
                .filter(word -> word.length() > 1)  // 过滤单字（如"的"、"了"）
                .filter(word -> !isStopWord(word))   // 过滤停用词
                .collect(Collectors.toList());
    }

    /**
     * 提取文本中的关键词(优化版:兼容短文本和长文本)
     * <p>策略:原始查询 + 分词结果 + TF-IDF 关键词,多层混合</p>
     * <p>特殊处理:单字查询(如“弓”)如果是自定义词典词汇或非停用词,也会保留</p>
     * <p>命令优化:保留英文单词(如 back, spawn)，用于命令文档检索</p>
     *
     * @param text 待提取关键词的文本
     * @param topK 返回前 K 个关键词
     * @return 关键词列表(去重保序)
     */
    private static List<String> extractKeywordsStatic(String text, int topK) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        Set<String> keywordSet = new LinkedHashSet<>();  // 去重保序

        // 统一归一化：将所有空白字符(含换行、制表符)归为单空格
        String normalizedText = text.trim().replaceAll("\\s+", " ");
        // 【第1层】原始查询(优先级最高,确保短文本完整匹配)
        // 清理标点符号（逗号、句号、问号、感叹号等），统一为空格
        String cleanedText = normalizedText.replaceAll("[,，.。?？!！;；:：]+", " ").trim();

        if (!cleanedText.isEmpty() && cleanedText.length() <= 20) {
            if (cleanedText.length() == 1) {
                // 单字查询:只有非停用词才保留
                if (!isStopWord(cleanedText)) {
                    keywordSet.add(cleanedText);
                }
            } else if (!cleanedText.contains(" ")) {
                // 无空格的连续短语(如"钻石矛")，完整保留以支持精确匹配
                keywordSet.add(cleanedText);
            } else {
                // 含空格的多词文本(如"那3格都是啥 背包 空")，拆分为独立 token
                for (String token : cleanedText.split("\\s+")) {
                    String t = token.trim();
                    if (!t.isEmpty() && t.length() > 1 && !isStopWord(t) && !isGenericWord(t)) {
                        keywordSet.add(t);
                    }
                }
            }
        }

        // 【第1.5层】提取英文单词（命令名优化）
        // 保留独立的英文单词，如 "back", "spawn", "money"
        String[] words = normalizedText.split("[,，.。?？!！;；:：]+");
        for (String word : words) {
            String trimmed = word.trim();
            // 保留纯英文单词（2-15字符），如命令名；排除罗马数字（MC附魔等级标记）
            if (!trimmed.isEmpty() && trimmed.matches("^[a-zA-Z]{2,15}$") && !isRomanNumeral(trimmed)) {
                keywordSet.add(trimmed.toLowerCase());
            }
        }

        // 【第2层】分词结果(中等优先级,捕获复合词的组成部分)
        // 使用归一化文本，避免 HanLP 将换行符作为 token
        List<String> segments = segmentStatic(normalizedText);
        for (String seg : segments) {
            if (!isGenericWord(seg) && !isPureNumeric(seg)) {
                keywordSet.add(seg);
            }
        }

        // 【第2.5层】单字分词补充(捕获自定义词典中的单字词)
        // 对于短查询,额外检查单字分词结果
        if (normalizedText.length() <= 4) {
            List<String> singleCharWords = extractMeaningfulSingleChars(normalizedText);
            keywordSet.addAll(singleCharWords);
        }

        // 【第3层】TF-IDF 关键词(补充,用于长文本)
        // 使用归一化文本，避免 HanLP 返回含换行的 token
        List<String> tfidfKeywords = HanLP.extractKeyword(normalizedText, topK * 2);
        for (String kw : tfidfKeywords) {
            // 清理可能的逗号、空格等分隔符
            String cleanedKw = kw.trim().replaceAll("[,，\\s]+", "");
            // 优化：允许英文命令名（如 back, spawn）
            if (!cleanedKw.isEmpty() && !isGenericWord(cleanedKw) && !isStopWord(cleanedKw) && !isPureNumeric(cleanedKw)) {
                // 英文单词：只保留 2-15 字符的纯英文（命令名），排除罗马数字
                if (containsEnglish(cleanedKw)) {
                    if (cleanedKw.matches("^[a-zA-Z]{2,15}$") && !isRomanNumeral(cleanedKw)) {
                        keywordSet.add(cleanedKw.toLowerCase());
                    }
                } else {
                    // 中文关键词：正常保留
                    keywordSet.add(cleanedKw);
                }
            }
        }

        // 返回前 topK 个(保持优先级顺序)
        return keywordSet.stream().limit(topK).collect(Collectors.toList());
    }

    /**
     * 提取有意义的单字词（来自自定义词典）
     * <p>用于捕获如"弓"、"剑"等在自定义词典中定义的单字物品名</p>
     *
     * @param text 待提取的文本
     * @return 有意义的单字词列表
     */
    private static List<String> extractMeaningfulSingleChars(String text) {
        List<String> result = new ArrayList<>();

        // 获取 HanLP 分词结果（不过滤单字）
        for (com.hankcs.hanlp.corpus.tag.Nature nature : com.hankcs.hanlp.corpus.tag.Nature.values()) {
            // 跳过无意义的词性
            if (nature == com.hankcs.hanlp.corpus.tag.Nature.nx ||  // 字母专名
                    nature == com.hankcs.hanlp.corpus.tag.Nature.m ||   // 数词
                    nature == com.hankcs.hanlp.corpus.tag.Nature.q) {  // 量词
                continue;
            }
        }

        // 直接检查文本中的每个中文字符是否在自定义词典中
        for (char c : text.toCharArray()) {
            String charStr = String.valueOf(c);
            // 检查是否为中文字符且在自定义词典中
            if (isChineseChar(c) && CustomDictionary.contains(charStr)) {
                if (!isStopWord(charStr)) {
                    result.add(charStr);
                }
            }
        }

        return result;
    }

    /**
     * 判断字符是否为中文字符
     */
    private static boolean isChineseChar(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
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
        Set<String> genericWords = Set.of("玩家", "物品", "信息", "内容", "数据", "情况", "结果", "执行", "输入", "输出", "任务", "目标", "查询", "检查", "当前", "以下", "以上", "相关", "具体", "详细");
        return genericWords.contains(word);
    }

    /**
     * 判断是否为停用词
     *
     * @param word 待判断的词
     * @return true 如果是停用词
     */
    private static boolean isStopWord(String word) {
        return word.matches("^[的得地了着过吗呢吧啊呀哦嗯嘛啦呗哇哈嘿哟嚯]$") || word.matches("^(这个|那个|哪些|什么|怎么|如何|是否|可以|可能|应该|需要|想要)$");
    }

    /**
     * 判断是否为罗马数字（MC附魔等级标记，如 III、V）
     */
    private static boolean isRomanNumeral(String word) {
        return word.matches("^(?i)(i{1,3}|iv|v|vi{0,3}|ix|x)$");
    }

    /**
     * 判断是否为纯数字（含负号、小数点）
     */
    private static boolean isPureNumeric(String word) {
        if (word == null || word.isEmpty()) return true;
        return word.matches("-?\\d+(\\.\\d+)?");
    }

    /**
     * 将文本转换为搜索查询字符串
     * <p>使用 TF-IDF 算法提取最重要的关键词，而非简单分词</p>
     *
     * @param text 原始文本
     * @param topK 提取的关键词数量
     * @return 用空格分隔的关键词字符串，适合用于知识库检索
     */
    private static String toSearchQueryStatic(String text, int topK) {
        List<String> keywords = extractKeywordsStatic(text, topK);
        return String.join(" ", keywords);
    }
}
