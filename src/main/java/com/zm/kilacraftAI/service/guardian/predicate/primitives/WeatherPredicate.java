package com.zm.kilacraftAI.service.guardian.predicate.primitives;

import com.zm.kilacraftAI.service.guardian.GuardianContext;
import com.zm.kilacraftAI.service.guardian.predicate.PlayerState;
import com.zm.kilacraftAI.service.guardian.predicate.Predicate;
import com.zm.kilacraftAI.service.guardian.predicate.WeatherCondition;

/**
 * 天气原语：当前天气匹配指定类型为 true。
 *
 * <p>典型：雷雨（THUNDER）时提醒收户外方块。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public final class WeatherPredicate implements Predicate {

    private final WeatherCondition expected;

    public WeatherPredicate(WeatherCondition expected) {
        this.expected = expected;
    }

    @Override
    public boolean test(PlayerState state, GuardianContext ctx) {
        return state.weather() == expected;
    }

    public WeatherCondition expected() {
        return expected;
    }
}
