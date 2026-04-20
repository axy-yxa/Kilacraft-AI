package com.zm.kilacraftAI.skills.framework;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.metrics.MetricsCollector;
import com.zm.kilacraftAI.util.PluginLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    private final KilacraftAI plugin;

    public SkillManager() {
        this.skills = new java.util.concurrent.ConcurrentHashMap<>();
        this.plugin = KilacraftAI.getInstance();
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
     * 根据意图执行技能（带错误隔离和安全消毒）
     *
     * <p>第三方 Skill 的异常会被 try-catch 捕获，不会影响 KilacraftAI 核心流程。</p>
     * <p>执行前会通过 {@link SkillSecurityFilter} 对entities进行安全消毒。</p>
     *
     * @param intent  识别出的意图
     * @param context 执行上下文
     * @return 执行结果
     */
    public CompletableFuture<SkillResult> executeSkillByIntent(SkillIntent intent, SkillContext context) {
        if (intent == null || !intent.isValid()) {
            return CompletableFuture.completedFuture(SkillResult.failure("无法识别你的意图，请详细描述你的需求"));
        }

        String skillName = intent.getSkillName();
        Skill skill = skills.get(skillName);

        if (skill == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("抱歉，我没有找到名为 '" + skillName + "' 的技能"));
        }

        // 检查技能是否可用（带错误隔离）
        try {
            if (!skill.isAvailable(context)) {
                return CompletableFuture.completedFuture(SkillResult.failure("抱歉，该功能暂时不可用"));
            }
        } catch (Exception e) {
            PluginLogger.warn("技能管理", "检查技能可用性时异常：" + skillName + " - " + e.getMessage(), e);
            return CompletableFuture.completedFuture(SkillResult.failure("抱歉，该功能暂时不可用"));
        }

        // 安全消毒：扫描entities中所有Value，在线玩家名不是自己则替换
        Map<String, String> sanitizedEntities = SkillSecurityFilter.sanitize(skillName, intent.getAction(), context);
        if (sanitizedEntities == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("执行上下文异常"));
        }

        // 如果entities被消毒过，需要重建SkillContext
        SkillContext executionContext = context;
        if (sanitizedEntities != context.getEntities()) {
            executionContext = new SkillContext(context.getPlayer(), context.getAction(), sanitizedEntities);
        }

        PluginLogger.debug("技能管理", "开始执行技能：" + skillName + ", action=" + intent.getAction());

        // 统计埋点：记录技能调用
        MetricsCollector.getInstance().recordSkillAction(skillName, intent.getAction());
        MetricsCollector.getInstance().recordSkillSource(skill.getClass());

        // 执行技能（带错误隔离，第三方 Skill 异常不影响核心流程）
        try {
            return skill.execute(executionContext).exceptionally(ex -> {
                PluginLogger.error("技能管理", "技能执行异常（可能为第三方技能）：" + skillName + " - " + ex.getMessage(), ex);
                return SkillResult.failure("技能执行出错，请联系管理员");
            });
        } catch (Exception e) {
            PluginLogger.error("技能管理", "技能执行失败：" + skillName + " - " + e.getMessage(), e);
            return CompletableFuture.completedFuture(SkillResult.failure("技能执行出错，请联系管理员"));
        }
    }

}
