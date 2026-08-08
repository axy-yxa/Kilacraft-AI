package com.zm.kilacraftAI.service.watch;

/**
 * 监听模式：按触发机制区分两类监听来源。
 *
 * <ul>
 *   <li>{@link #POLLING} — 条件监听（轮询型）：定时器周期执行 skill action，
 *       取返回值字段比较判定（如盯血量低于某值、盯背包物品到某数）</li>
 *   <li>{@link #EVENT} — 事件监听（事件型）：Bukkit Event Listener 被动触发，
 *       命中 filter 即通知（如盯熔炉烧好、盯作物成熟、盯实体死亡）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
public enum WatchMode {
    /**
     * 条件监听（轮询型）：定时执行 skill action 取值比较。
     */
    POLLING,
    /**
     * 事件监听（事件型）：Bukkit 事件命中 filter 即触发。
     */
    EVENT
}
