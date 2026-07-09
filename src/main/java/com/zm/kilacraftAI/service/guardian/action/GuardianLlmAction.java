package com.zm.kilacraftAI.service.guardian.action;

import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.task.AnalysisSummary;
import com.zm.kilacraftAI.skills.framework.task.LLMOutputCoordinator;
import com.zm.kilacraftAI.service.guardian.GuardianContext;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * L3 动作：LLM 判断/陪聊。模糊信号经 LLM 组织语言，走 {@link LLMOutputCoordinator}
 * 的预算/熔断治理（{@link OutputScenarioEnum#GUARDIAN} → 被动优先级，熔断时可降级/丢弃）。
 *
 * <p>输出经 LLM 生成（信号模糊、措辞需个性化），写 conversation（source=guardian），计入画像上下文。</p>
 *
 * <p>经 coordinator 统一治理，不绕过预算层。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-07
 */
public final class GuardianLlmAction implements GuardianAction {

    private final LLMOutputCoordinator coordinator;

    public GuardianLlmAction(LLMOutputCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public CompletableFuture<Outcome> perform(GuardianContext ctx) {
        Player player = ctx.player();
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(Outcome.PERMANENT_FAIL);
        }
        String trigger = ctx.monitorId() != null ? "guardian:" + ctx.monitorId() : "guardian";
        String tv = ctx.triggerValue().map(Object::toString).orElse("");
        AnalysisSummary summary = new AnalysisSummary()
                .userMessage("守护系统主动输出")
                .taskGoal(tv.isEmpty() ? "monitor=" + ctx.monitorId() : "monitor=" + ctx.monitorId() + "，触发值=" + tv)
                .addResult("guardian", "SUCCESS", tv.isEmpty() ? "守护触发" : "守护触发：" + tv);
        SkillContext sc = new SkillContext(player, "guardian_proactive", java.util.Map.of()).withAudit(trigger, "guardian");
        return coordinator.outputAnalysisResult(player, summary, sc, new ArrayDeque<>(), OutputScenarioEnum.GUARDIAN, false)
                .thenApply(sr -> sr.isSuccess() ? Outcome.SUCCESS : Outcome.TRANSIENT_FAIL);
    }
}
