package com.zm.kilacraftAI.skills.framework.config;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 技能配置数据类
 *
 * <p>封装单个技能的配置信息</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
@Getter
public class SkillConfig {

    private final String packageName;      // 包名称（如 globalmarketplus）
    private final String skillName;        // 技能类名（如 MarketQuerySkill）
    private final String description;      // 技能描述（用于 LLM 意图识别）
    private final Map<String, String> actionDescriptions;  // 动作描述（key=动作名称，value=描述）
    private final List<String> hints;      // 额外提示信息（用于指导 LLM 使用技能）

    public SkillConfig(String packageName, String skillName,
                       String description,
                       Map<String, String> actionDescriptions,
                       List<String> hints) {
        this.packageName = packageName;
        this.skillName = skillName;
        this.description = description;
        this.actionDescriptions = actionDescriptions;
        this.hints = hints;
    }
}
