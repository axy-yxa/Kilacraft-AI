package com.zm.kilacraftAI.service.guardian.action;

import com.zm.kilacraftAI.service.guardian.GuardianContext;

import java.util.concurrent.CompletableFuture;

/**
 * 守护动作。触发后做什么——分级落点：
 * <ul>
 *   <li>L1 模板通知（{@code TemplatedNotifyAction}，零 LLM）</li>
 *   <li>L2 执行 skill（{@link SkillAction}，经 {@code executeSkillByIntent}）</li>
 *   <li>L3 LLM 判断（{@code LlmJudgeAction}，门控）</li>
 * </ul>
 *
 * <p>在 IO 线程执行，返回统一 {@link Outcome}，由任务策略决定 re-arm/重试/收尾。
 * 单参 {@code perform(GuardianContext)}：监听单元提供的标识/触发数值已并入 context，
 * 动作不耦合 Monitor 类型。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public interface GuardianAction {

    /** 执行动作，返回统一 Outcome。 */
    CompletableFuture<Outcome> perform(GuardianContext ctx);
}
