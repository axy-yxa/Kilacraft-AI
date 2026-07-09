package com.zm.kilacraftAI.service.guardian.action;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillIntent;
import com.zm.kilacraftAI.skills.framework.SkillManager;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * L2 动作：经 {@code executeSkillByIntent} 调用 skill，映射 {@link com.zm.kilacraftAI.skills.framework.SkillResult}
 * → {@link Outcome}。
 *
 * <p>关键不变量：
 * <ul>
 *   <li>{@code SkillSecurityFilter} 始终运行（{@code executeSkillByIntent} 内部咽喉，玩家数据隔离不可跳过）；</li>
 *   <li>{@code NEED_INFO} 经框架自动 {@code PendingResumeManager.save}（沿用单步重放有意设计）；</li>
 *   <li>守护调用的 skill 经框架打 {@code source='guardian'} 审计标签（仅审计可见性，不过滤社交提取）；</li>
 *   <li>SkillIntent confidence=1.0（守护已决断，非 LLM 猜测），满足 {@code isValid} 阈值。</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class SkillAction implements GuardianAction {

    private final SkillManager skillManager;
    private final String skillName;
    private final String action;
    private final Map<String, String> entities;

    public SkillAction(SkillManager skillManager, String skillName, String action, Map<String, String> entities) {
        this.skillManager = Objects.requireNonNull(skillManager, "skillManager");
        this.skillName = Objects.requireNonNull(skillName, "skillName");
        this.action = Objects.requireNonNull(action, "action");
        this.entities = entities;
    }

    @Override
    public CompletableFuture<Outcome> perform(GuardianContext ctx) {
        String trigger = ctx.monitorId() != null ? "guardian:" + ctx.monitorId() : "guardian";
        SkillIntent intent = new SkillIntent(skillName, action, entities, 1.0, trigger);
        SkillContext sc = new SkillContext(ctx.player(), action, entities)
                .withAudit(trigger, "guardian");
        return skillManager.executeSkillByIntent(intent, sc)
                .thenApply(SkillOutcomeMapper::map);
    }

    public String skillName() {
        return skillName;
    }

    public String action() {
        return action;
    }

    public Map<String, String> entities() {
        return entities;
    }
}
