package com.zm.kilacraftAI.skill;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 技能意图 - 由 LLM 识别出的用户意图
 */
@Getter
public class SkillIntent {
    private final String skillName;        // 技能名称（由 LLM 识别）
    private final String action;           // 具体动作（由 LLM 识别）
    private final Map<String, String> entities;  // 实体参数（由 LLM 提取）
    private final double confidence;       // 置信度（0.0 - 1.0）
    private final String rawInput;         // 原始用户输入

    public SkillIntent(String skillName, String action, Map<String, String> entities, double confidence, String rawInput) {
        this.skillName = skillName;
        this.action = action;
        this.entities = entities != null ? entities : new HashMap<>();
        this.confidence = confidence;
        this.rawInput = rawInput;
    }

    /**
     * 判断意图是否有效
     */
    public boolean isValid() {
        return skillName != null && !skillName.isEmpty() && confidence >= 0.5;
    }

    @Override
    public String toString() {
        return String.format("SkillIntent{skill='%s', action='%s', confidence=%.2f, entities=%s}", skillName, action, confidence, entities);
    }
}
