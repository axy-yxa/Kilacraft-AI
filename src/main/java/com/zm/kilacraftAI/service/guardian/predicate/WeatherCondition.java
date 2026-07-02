package com.zm.kilacraftAI.service.guardian.predicate;

/**
 * 玩家所在世界的天气状态，供 {@code WeatherPredicate} 使用。
 *
 * <p>独立于 {@link org.bukkit.WeatherType}（后者是玩家客户端视角的强制天气）；
 * 这里是世界真实天气的简明三态分类。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public enum WeatherCondition {
    /** 晴：无降水、无雷 */
    CLEAR,
    /** 雨：有降水、未打雷 */
    RAIN,
    /** 雷雨：打雷（通常伴随降水） */
    THUNDER
}
