package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.ValuePredicate;

/**
 * 装备耐久临界原语：检测玩家耐久最低的可损坏装备剩余耐久是否 ≤ 阈值。
 *
 * <p>用于内置 {@code _low_durability} 轮询型 monitor 的触发谓词。从 {@link PlayerState#lowestDurabilityItem()}
 * 取采集到的最低耐久装备，按剩余百分比与 {@link #maxRemainingRatio} 比较。
 * {@link #recordValue} 记录剩余百分比（0-100），供 LLM 消息渲染「剩余 4%」。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-26
 */
public final class LowDurabilityPredicate extends ValuePredicate {

    /**
     * 触发阈值：剩余耐久百分比 ≤ 此值才告警（如 0.05 = 剩余 ≤5%）。
     */
    private final double maxRemainingRatio;

    public LowDurabilityPredicate(double maxRemainingRatio) {
        if (maxRemainingRatio < 0 || maxRemainingRatio > 1) {
            throw new IllegalArgumentException(I18nService.tr("maxRemainingRatio 必须在 [0,1] 区间: {}", maxRemainingRatio));
        }
        this.maxRemainingRatio = maxRemainingRatio;
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        PlayerState.LowDurabilityItem item = state.lowestDurabilityItem();
        if (item == null) {
            return false;
        }
        double ratio = item.remainingRatio();
        recordValue(ratio * 100.0); // 供模板渲染「剩余 X%」
        return ratio <= maxRemainingRatio;
    }
}
