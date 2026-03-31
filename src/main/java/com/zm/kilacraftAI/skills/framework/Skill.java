package com.zm.kilacraftAI.skills.framework;

import com.zm.kilacraftAI.skills.config.SkillConfig;

import java.util.concurrent.CompletableFuture;

/**
 * Skill 基础接口
 * 
 * <p>所有技能实现都必须实现此接口</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
public interface Skill {
    
    /**
     * 获取技能名称（唯一标识）
     *
     * @return 技能名称
     */
    String getName();
    
    /**
     * 获取技能描述（用于 LLM 意图识别）
     *
     * @return 技能描述
     */
    String getDescription();
    
    /**
     * 获取技能配置（从 YAML 文件读取）
     *
     * @return 技能配置对象，如果未配置则返回 null
     */
    default SkillConfig getSkillConfig() {
        return null;
    }
    
    /**
     * 执行技能
     *
     * @param context 执行上下文
     * @return 执行结果（异步）
     */
    CompletableFuture<SkillResult> execute(SkillContext context);
    
    /**
     * 检查技能是否可用
     *
     * @param context 执行上下文
     * @return true=可用，false=不可用
     */
    default boolean isAvailable(SkillContext context) {
        return true;
    }
}
