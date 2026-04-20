package com.zm.kilacraftAI.metrics;

import lombok.Getter;

/**
 * Skill 元信息（用于 bStats 上报）
 *
 * <p>包含 Skill 的基本信息，用于全球 Skill 台账统计。</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-19
 */
@Getter
public class SkillInfo {

    /**
     * Skill 名称
     */
    private final String name;

    /**
     * Skill 类型：built_in（内置）/ third_party（第三方 SPI）
     */
    private final String type;

    /**
     * 来源插件名
     */
    private final String sourcePlugin;

    public SkillInfo(String name, String type, String sourcePlugin) {
        this.name = name;
        this.type = type;
        this.sourcePlugin = sourcePlugin;
    }

    @Override
    public String toString() {
        return "SkillInfo{" + "name='" + name + '\'' + ", type='" + type + '\'' + ", sourcePlugin='" + sourcePlugin + '\'' + '}';
    }
}
