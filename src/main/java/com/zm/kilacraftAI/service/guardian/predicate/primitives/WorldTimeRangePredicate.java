package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.ValuePredicate;

/**
 * 世界时间区间原语：{@code worldTime}（0~23999）落在 {@code [start, end]} 闭区间时为 true。
 *
 * <p>支持跨午夜：{@code start > end} 时区间为 {@code [start, 24000) ∪ [0, end]}（如夜晚 18000~6000）。
 * 典型：夜里且有怪（配合 And）触发戒备。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class WorldTimeRangePredicate extends ValuePredicate {

    private static final long DAY = 24000L;

    private final long startTicks;
    private final long endTicks;

    public WorldTimeRangePredicate(long startTicks, long endTicks) {
        this.startTicks = normalize(startTicks);
        this.endTicks = normalize(endTicks);
    }

    private static long normalize(long t) {
        return ((t % DAY) + DAY) % DAY;
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        long t = normalize(state.worldTime());
        recordValue(t);
        if (startTicks <= endTicks) {
            return t >= startTicks && t <= endTicks;
        }
        // 跨午夜
        return t >= startTicks || t <= endTicks;
    }

    public long startTicks() {
        return startTicks;
    }

    public long endTicks() {
        return endTicks;
    }
}
