package com.zm.kilacraftAI.skills.framework;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.IntentKeywordConfigManager;
import com.zm.kilacraftAI.util.BM25Scorer;
import com.zm.kilacraftAI.util.ChineseTextUtil;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 意图分类器（BM25 语义评分）
 *
 * <p>基于反转策略：将每个 Skill 的 description + action_descriptions 作为虚拟文档，
 * 用 BM25 算法计算用户输入与 Skill 文档的语义相关性得分。</p>
 *
 * <p>分类优先级（从高到低）：</p>
 * <ol>
 *   <li>普通对话关键词快速短路 → NORMAL_CHAT</li>
 *   <li>闲聊句式匹配 → NORMAL_CHAT</li>
 *   <li>BM25 评分 + 祈使句加分 → 超过阈值则 SKILL_INTENT</li>
 *   <li>默认 → NORMAL_CHAT</li>
 * </ol>
 *
 * <p>新增 Skill / SPI 第三方 Skill 注册后自动纳入索引，零维护。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-13
 */
public class IntentClassifier {

    private final IntentKeywordConfigManager keywordConfig;

    /**
     * Skill 文档索引：key=Skill名称，value=合并后的描述文本
     */
    private final Map<String, String> skillDocuments = new ConcurrentHashMap<>();

    /**
     * Skill 文档的平均字符长度（用于 BM25 归一化）
     */
    private volatile int avgDocLength = 200;

    /**
     * 上次构建索引时的 Skill 数量，用于判断是否需要重建
     */
    private volatile int lastSkillCount = -1;

    public IntentClassifier() {
        this.keywordConfig = IntentKeywordConfigManager.getInstance();
    }

    /**
     * 强制重建索引（用于热重载后刷新 Skill 描述变更）
     * <p>将 lastSkillCount 重置为 -1，下次 classify 调用时会触发完整的索引重建。</p>
     */
    public void forceRebuildIndex() {
        lastSkillCount = -1;
    }

    /**
     * 分类用户意图
     *
     * @param userInput 用户输入
     * @return NORMAL_CHAT 或 SKILL_INTENT
     */
    public IntentType classify(String userInput) {
        // 1. 普通对话关键词快速短路
        if (matchesKeyword(userInput.toLowerCase(), keywordConfig.getNormalChatKeywords())) {
            return IntentType.NORMAL_CHAT;
        }

        // 2. 闲聊句式强制 NORMAL_CHAT
        if (matchesKeyword(userInput, keywordConfig.getChatPatterns())) {
            return IntentType.NORMAL_CHAT;
        }

        // 3. 确保索引是最新的（Skill 可能动态注册/注销）
        rebuildIndexIfNeeded();

        if (skillDocuments.isEmpty()) {
            return IntentType.NORMAL_CHAT;
        }

        // 4. BM25 语义评分
        List<String> keywords = ChineseTextUtil.extractKeywords(userInput, 8);
        if (keywords.isEmpty()) {
            return IntentType.NORMAL_CHAT;
        }

        double maxScore = 0.0;
        String topSkill = null;

        for (Map.Entry<String, String> entry : skillDocuments.entrySet()) {
            String doc = entry.getValue();
            double score = BM25Scorer.score(doc, keywords, 1.5, 0.75, avgDocLength);

            // 精确匹配加分：关键词在文档中精确出现时额外加分
            // BM25 对短查询的 TF 项天然偏低，精确匹配作为补充信号
            double exactMatchBonus = 0.0;
            for (String keyword : keywords) {
                if (keyword.length() >= 2 && doc.contains(keyword)) {
                    exactMatchBonus += 2.0;
                }
            }
            score += exactMatchBonus;

            if (score > maxScore) {
                maxScore = score;
                topSkill = entry.getKey();
            }
        }

        // 5. 祈使句式加分
        if (matchesKeyword(userInput, keywordConfig.getImperativePatterns())) {
            maxScore += keywordConfig.getImperativeBonus();
        }

        // 6. 阈值判断
        IntentType result = maxScore >= keywordConfig.getSkillMatchThreshold() ? IntentType.SKILL_INTENT : IntentType.NORMAL_CHAT;

        if (KilacraftAI.getInstance().getConfigManager().isDebugMode()) {
            KilacraftAI.getInstance().getLogger().info(String.format("[DEBUG] [意图分类] %s | 得分=%.1f 阈值=%.1f 最佳匹配=%s | 关键词=%s", result.getDescription(), maxScore, keywordConfig.getSkillMatchThreshold(), topSkill != null ? topSkill : "无", keywords));
        }

        return result;
    }

    /**
     * 检查 Skill 列表是否变化，按需重建索引
     */
    private void rebuildIndexIfNeeded() {
        SkillManager skillManager = KilacraftAI.getInstance().getSkillManager();
        List<Skill> skills = skillManager.getAllSkills();

        if (skills.size() == lastSkillCount) {
            return;
        }

        skillDocuments.clear();
        int totalLength = 0;

        for (Skill skill : skills) {
            String doc = buildSkillDocument(skill);
            skillDocuments.put(skill.getName(), doc);
            totalLength += doc.length();
        }

        if (!skills.isEmpty()) {
            avgDocLength = totalLength / skills.size();
        }

        lastSkillCount = skills.size();
    }

    /**
     * 将 Skill 的元数据合并为一段文本文档，用于 BM25 匹配
     * <p>合并范围：description + action_descriptions。</p>
     */
    private String buildSkillDocument(Skill skill) {
        StringBuilder sb = new StringBuilder();
        sb.append(skill.getDescription()).append(" ");

        Map<String, String> actions = skill.getActions();
        if (actions != null) {
            for (String actionDesc : actions.values()) {
                sb.append(actionDesc).append(" ");
            }
        }

        return sb.toString();
    }

    /**
     * 检查文本是否包含列表中的任意一个关键词
     */
    private boolean matchesKeyword(String text, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (text.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 意图类型枚举
     */
    @Getter
    public enum IntentType {
        NORMAL_CHAT("普通对话"), SKILL_INTENT("技能意图");

        private final String description;

        IntentType(String description) {
            this.description = description;
        }
    }
}
