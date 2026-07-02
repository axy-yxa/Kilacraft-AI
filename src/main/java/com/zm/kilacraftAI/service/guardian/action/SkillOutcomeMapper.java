package com.zm.kilacraftAI.service.guardian.action;

import com.zm.kilacraftAI.common.util.LLMResponseUtil;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.skills.framework.SkillStatus;

/**
 * {@link SkillResult} → {@link Outcome} 映射（§3.5，§4.3 SkillAction 内部使用）。
 *
 * <p>FAILURE 默认判 {@link Outcome#PERMANENT_FAIL}（保守：永久失败重试 = 死循环，停+通知更安全）；
 * 仅 §c 标记的 LLM 错误（超时/限流/网络，由 {@code LLMResponseUtil.errorResponse} 构造）判
 * {@link Outcome#TRANSIENT_FAIL}，允许策略退避重试。不动 {@link SkillResult} 本身（避免影响全局 skill）。</p>
 *
 * <p>新增任何 skill 不改本映射——它只看 SkillResult.status 与 §c 标记。这是「换 skill 报不同错」的根治。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class SkillOutcomeMapper {

    private SkillOutcomeMapper() {
    }

    public static Outcome map(SkillResult result) {
        if (result == null) {
            return Outcome.PERMANENT_FAIL;
        }
        return switch (result.getStatus()) {
            case SUCCESS -> Outcome.SUCCESS;
            case NEED_INFO -> Outcome.NEED_INFO;
            case SKIPPED -> Outcome.IN_PROGRESS;
            case FAILURE -> LLMResponseUtil.isErrorResponse(result.getMessage())
                    ? Outcome.TRANSIENT_FAIL
                    : Outcome.PERMANENT_FAIL;
        };
    }
}
