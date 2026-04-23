package com.zm.kilacraftAI.util;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 英文文本处理器实现
 * <p>提供英文专用的分词、关键词提取和搜索查询生成。
 * 使用空格/标点分词和英文停用词，不依赖 HanLP。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-21
 */
public class EnglishTextProcessor implements TextProcessor {

    // 常见英文停用词
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "it", "its", "are", "was", "were",
            "be", "been", "being", "have", "has", "had", "do", "does", "did",
            "will", "would", "could", "should", "may", "might", "can", "shall",
            "not", "no", "nor", "so", "if", "then", "than", "that", "this",
            "these", "those", "i", "me", "my", "we", "our", "you", "your",
            "he", "him", "his", "she", "her", "they", "them", "their",
            "what", "which", "who", "whom", "how", "when", "where", "why",
            "all", "each", "every", "both", "few", "more", "most", "other",
            "some", "such", "only", "own", "same", "too", "very", "just",
            "about", "above", "after", "before", "between", "into", "through",
            "during", "here", "there", "up", "down", "out", "off", "over",
            "under", "again", "further", "once", "also", "get", "got", "like",
            "know", "need", "want", "let", "make", "go", "think", "say", "tell",
            "give", "use", "find", "ask", "work", "seem", "feel", "try",
            "leave", "call", "keep", "much", "many", "any", "now", "new",
            "way", "thing", "things", "still", "well", "back", "even",
            "am", "oh", "wow", "hey", "yeah", "yep", "nope", "omg", "ugh", "huh",
            "holy", "shit", "damn", "hell", "fuck", "fucking", "goddamn",
            "lol", "lmao", "wtf", "bruh", "bro", "dude", "ya", "yay",
            // 罗马数字（MC附魔等级标记，对知识库检索无语义价值）
            "ii", "iii", "iv", "vi", "vii", "viii", "ix"
    );

    // 对MC查询来说语义价值较低的通用英文词
    private static final Set<String> GENERIC_WORDS = Set.of(
            "player", "item", "information", "content", "data", "result",
            "execute", "input", "output", "task", "target", "query",
            "check", "current", "following", "above", "related", "specific",
            "detail", "details", "please", "help", "show", "tell",
            "what", "status", "situation",
            // MC格式化输出中的结构标签
            "ench", "durability", "lore"
    );

    private final Set<String> customWords = new LinkedHashSet<>();

    @Override
    public void initCustomDictionary(List<String> words) {
        if (words != null) {
            customWords.addAll(words);
        }
    }

    @Override
    public List<String> segment(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        // 英文分词：按非字母数字字符拆分
        return Arrays.stream(text.toLowerCase().split("[^a-zA-Z0-9_-]+"))
                .filter(word -> !word.isEmpty())
                .filter(word -> word.length() > 1)
                .filter(word -> !isPureNumeric(word))  // 过滤纯数字（坐标、数值等无语义价值）
                .filter(word -> !STOP_WORDS.contains(word))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> extractKeywords(String text, int topK) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        Set<String> keywordSet = new LinkedHashSet<>();

        String normalizedText = text.trim().replaceAll("\\s+", " ");
        String cleanedText = normalizedText.replaceAll("[,，.。?？!！;；:：]+", " ").trim().toLowerCase();

        // 第一层：原始短查询保留
        if (!cleanedText.isEmpty() && cleanedText.split("\\s+").length <= 4) {
            // 对短查询，保留清洗后的完整文本
            if (!cleanedText.contains(" ")) {
                if (!STOP_WORDS.contains(cleanedText) && !GENERIC_WORDS.contains(cleanedText)) {
                    keywordSet.add(cleanedText);
                }
            } else {
                for (String token : cleanedText.split("\\s+")) {
                    String t = token.trim();
                    if (t.length() > 1 && !STOP_WORDS.contains(t) && !GENERIC_WORDS.contains(t)
                            && !isPureNumeric(t)) {
                        keywordSet.add(t);
                    }
                }
            }
        }

        // 第二层：分词结果
        List<String> tokens = segment(normalizedText);
        for (String token : tokens) {
            if (!GENERIC_WORDS.contains(token) && !isPureNumeric(token)) {
                keywordSet.add(token);
            }
        }

        // 第三层：自定义词典匹配
        for (String customWord : customWords) {
            if (normalizedText.toLowerCase().contains(customWord.toLowerCase())) {
                keywordSet.add(customWord.toLowerCase());
            }
        }

        // 第四层：基于TF的关键词评分（用于较长文本）
        if (normalizedText.split("\\s+").length > 4) {
            Map<String, Integer> freq = new HashMap<>();
            for (String token : segment(normalizedText)) {
                freq.merge(token, 1, Integer::sum);
            }
            freq.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(topK)
                    .filter(e -> !GENERIC_WORDS.contains(e.getKey()))
                    .filter(e -> !isPureNumeric(e.getKey()))
                    .forEach(e -> keywordSet.add(e.getKey()));
        }

        return keywordSet.stream().limit(topK).collect(Collectors.toList());
    }

    @Override
    public String toSearchQuery(String text, int topK) {
        List<String> keywords = extractKeywords(text, topK);
        return String.join(" ", keywords);
    }

    /**
     * 判断字符串是否为纯数字（含负号、小数点）
     * <p>纯数字（如坐标值 -11, 97.00）对知识库检索无语义价值，应过滤</p>
     */
    private static boolean isPureNumeric(String word) {
        if (word == null || word.isEmpty()) return true;
        return word.matches("-?\\d+(\\.\\d+)?");
    }
}
