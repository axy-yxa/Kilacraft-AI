package com.zm.kilacraftAI.skills.framework;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 技能意图 - 由 LLM 识别出的用户意图
 *
 * @author Zm_Mmm
 * @since 2026-03-31
 */
@Getter
public class SkillIntent {

    /**
     * 技能名称（由 LLM 识别）
     */
    private final String skillName;

    /**
     * 具体动作（由 LLM 识别）
     */
    private final String action;

    /**
     * 实体参数（由 LLM 提取）
     */
    private final Map<String, String> entities;

    /**
     * 置信度（0.0 - 1.0）
     */
    private final double confidence;

    /**
     * 双语义字段：
     * <ul>
     *   <li>正常意图：原始用户输入（当前无消费方，预留）</li>
     *   <li>无效意图：失败原因——技能路径失败时以 §c 前缀标记（经 {@code LLMResponseUtil.errorResponse}），
     *       供 {@code AIRequestHandler.dispatchIntentResult} 用 {@code isErrorResponse} 判定是否注入 [FAILURE]；
     *       非技能请求无 §c 前缀，静默回退普通对话</li>
     * </ul>
     */
    private final String rawInput;

    public SkillIntent(String skillName, String action, Map<String, String> entities, double confidence, String rawInput) {
        this.skillName = skillName;
        this.action = action;
        this.entities = entities != null ? entities : new HashMap<>();
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
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
