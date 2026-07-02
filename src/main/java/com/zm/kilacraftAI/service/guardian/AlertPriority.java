package com.zm.kilacraftAI.service.guardian;

/**
 * 告警优先级。{@link GuardianCooldownHub} 用它决定抢占：{@link #CRITICAL} 打破全局/分类冷却立即发声
 * （溺水、致命威胁），但仍受静音列表约束（玩家显式反馈优先）。
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public enum AlertPriority {
    LOW,
    NORMAL,
    HIGH,
    /** 关键：打破全局/分类冷却与画像相关性过滤立即触发；不打破静音列表。 */
    CRITICAL;

    public boolean isCritical() {
        return this == CRITICAL;
    }
}
