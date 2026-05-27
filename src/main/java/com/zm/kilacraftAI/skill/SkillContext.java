package com.zm.kilacraftAI.skill;

import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Skill 执行上下文
 *
 * <p>封装 Skill 执行时所需的所有信息。</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
@Getter
public class SkillContext {

    /**
     * 玩家对象（可能为 null）
     */
    private final Player player;
    /**
     * LLM 识别出的动作
     */
    private final String action;
    /**
     * LLM 提取的实体参数
     */
    private final Map<String, String> entities;

    /**
     * 审计用：用户原始消息（可选，由调用方注入）
     */
    private String triggerMessage;

    /**
     * 审计用：触发来源（可选，如 "agent" / "afk_callback"）
     */
    private String executionSource;

    /**
     * 创建 SkillContext
     */
    public SkillContext(Player player, String action, Map<String, String> entities) {
        this.player = player;
        this.action = action;
        this.entities = entities != null ? entities : new HashMap<>();
    }

    /**
     * 设置审计上下文（链式调用）
     *
     * @param triggerMessage  用户原始消息
     * @param executionSource 触发来源
     * @return this
     */
    public SkillContext withAudit(String triggerMessage, String executionSource) {
        this.triggerMessage = triggerMessage;
        this.executionSource = executionSource;
        return this;
    }

    /**
     * 获取指定的实体参数
     *
     * @param key 参数键
     * @return 参数值，如果不存在则返回 null
     */
    public String getEntity(String key) {
        return entities.get(key);
    }
}
