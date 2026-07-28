package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.Predicate;

/**
 * 容器打开原语：玩家正打开容器 GUI（箱子/熔炉/工作台等）时为 true。
 *
 * <p>仅用于内置 monitor 的「已感知抑制」组合：玩家在看容器说明他知道当前物品状态，
 * 此时背包快满告警多余。非 LLM 可发现，仅由内置套餐硬编码装配。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-22
 */
public final class InventoryOpenPredicate implements Predicate {

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        return state.inventoryOpen();
    }
}
