package com.zm.kilacraftAI.service.guardian.monitor;

/**
 * 监听单元的信号来源（§4.1）。三实现：
 * <ul>
 *   <li>{@link EventTriggerSource} — Bukkit 事件 push（危险、看门狗、登录）</li>
 *   <li>{@link PollingTriggerSource} — 引擎心跳按 cadence 拉取（资源、目标、循环）</li>
 *   <li>{@link ScheduledTriggerSource} — 定时器（每日、一次性）</li>
 * </ul>
 *
 * <p>通用性：每种源既能独立驱动一个 monitor，也能与不同 Policy/Action 组合；
 * 接口窄（bind/unbind），实现各自决定如何接入 {@link GuardianRuntime}。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public interface TriggerSource {

    /** 接入运行时：事件源注册监听 / 轮询源声明 cadence（引擎轮询时读取）/ 定时源排程。 */
    void bind(GuardianRuntime runtime, Monitor monitor);

    /** 优雅摘除（玩家下线 / monitor 取消 / reload）。 */
    void unbind(GuardianRuntime runtime, Monitor monitor);
}
