package com.zm.kilacraftAI.service.guardian.monitor;

/**
 * 监听单元的重新武装策略：决定条件命中后何时触发、何时收尾。
 *
 * <ul>
 *   <li>{@link #WATCH_EDGE} — 条件假→真跃迁时触发一次，带去抖/冷却。看门狗、危险告警。</li>
 *   <li>{@link #WHILE_TRUE} — 条件保持真期间反复执行。收小麦、熔炉循环。</li>
 *   <li>{@link #UNTIL_GOAL} — 反复执行直到目标谓词为真。凑够 64 铁、钓到鱼。</li>
 *   <li>{@link #RECURRING} — 固定间隔重复，不依赖状态。每日查商店、定时喂动物。</li>
 *   <li>{@link #ONE_SHOT} — 触发→执行→结束。一次性事件回调。</li>
 * </ul>
 *
 * <p>边沿（{@link #WATCH_EDGE}）与电平（{@link #WHILE_TRUE}）必须显式区分：
 * 前者记忆上一态、仅跃迁触发；后者电平触发。混淆是刷屏的隐藏来源——电平策略若误用边沿语义，
 * 会在条件持续为真期间反复开火。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public enum Policy {
    WATCH_EDGE,
    WHILE_TRUE,
    UNTIL_GOAL,
    RECURRING,
    ONE_SHOT;

    /** 边沿型：仅 {@link #WATCH_EDGE}。 */
    public boolean isEdgeTriggered() {
        return this == WATCH_EDGE;
    }

    /** 触发即终态：{@link #ONE_SHOT} 成功后直接 DONE。 */
    public boolean isOneShot() {
        return this == ONE_SHOT;
    }
}
