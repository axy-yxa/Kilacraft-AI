package com.zm.kilacraftAI.skills.framework;

import com.zm.kilacraftAI.KilacraftAI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能管理器
 *
 * <p>负责注册和管理所有技能（基于 LLM 意图识别）</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-30
 */
public class SkillManager {

    private final Map<String, Skill> skills;

    public SkillManager() {
        this.skills = new ConcurrentHashMap<>();
    }

    /**
     * 注册技能
     *
     * @param skill 技能实例
     */
    public void registerSkill(Skill skill) {
        if (skill == null) {
            throw new IllegalArgumentException("技能不能为空");
        }

        String name = skill.getName();
        if (skills.containsKey(name)) {
            throw new IllegalArgumentException("技能已注册：" + name);
        }

        skills.put(name, skill);
    }

    /**
     * 注销技能
     *
     * @param skillName 技能名称
     */
    public void unregisterSkill(String skillName) {
        skills.remove(skillName);
    }

    /**
     * 获取已注册的技能
     *
     * @param skillName 技能名称
     * @return 技能实例，不存在则返回 null
     */
    public Skill getSkill(String skillName) {
        return skills.get(skillName);
    }

    /**
     * 获取所有已注册的技能
     *
     * @return 技能列表
     */
    public List<Skill> getAllSkills() {
        return new ArrayList<>(skills.values());
    }

    /**
     * 根据意图执行技能
     *
     * @param intent  识别出的意图
     * @param context 执行上下文
     * @return 执行结果
     */
    public java.util.concurrent.CompletableFuture<SkillResult> executeSkillByIntent(SkillIntent intent, SkillContext context) {
        if (intent == null || !intent.isValid()) {
            return java.util.concurrent.CompletableFuture.completedFuture(SkillResult.failure("无法识别你的意图，请详细描述你的需求"));
        }

        // 根据技能名称查找技能
        String skillName = intent.getSkillName();
        KilacraftAI plugin = KilacraftAI.getInstance();
        boolean isDebug = plugin != null && plugin.getConfigManager().isDebugMode();

        Skill skill = skills.get(skillName);

        if (skill == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(SkillResult.failure("抱歉，我没有找到名为 '" + skillName + "' 的技能"));
        }

        // 检查技能是否可用
        if (!skill.isAvailable(context)) {
            return java.util.concurrent.CompletableFuture.completedFuture(SkillResult.failure("抱歉，该功能暂时不可用"));
        }

        if (isDebug) {
            plugin.getLogger().info("[DEBUG] 开始执行技能：" + skillName + ", action=" + intent.getAction());
        }

        // 执行技能
        return skill.execute(context);
    }


}
