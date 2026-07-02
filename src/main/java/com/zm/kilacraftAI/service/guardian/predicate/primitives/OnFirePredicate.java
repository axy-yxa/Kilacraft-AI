package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.Predicate;

/**
 * 着火原语：{@code onFire} 为 true（玩家 fireTicks &gt; 0）时触发。
 *
 * <p>典型：岩浆/火焰伤害告警。生产部署多走 {@code EntityDamageEvent} 事件源直推 Signal；
 * 此原语供轮询型守护复合条件使用（如「着火 AND 在下界」）。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class OnFirePredicate implements Predicate {

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        return state.onFire();
    }
}
