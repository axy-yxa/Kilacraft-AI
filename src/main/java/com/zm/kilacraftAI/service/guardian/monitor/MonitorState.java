package com.zm.kilacraftAI.service.guardian.monitor;

/**
 * 监听单元状态机。
 *
 * <pre>
 *        ┌─────────── re-arm 间隔 ───────────┐
 *        ▼                                    │
 *   RUNNING ──一轮结束──► WAITING ──到点──► RUNNING
 *      │_transient fail        │_目标达成
 *      ▼                        ▼
 *   BLOCKED（退避重试，有预算）  DONE
 *      │_永久失败/超预算
 *      ▼
 *   FAILED ──► 通知玩家
 *   玩家下线 ──► PAUSED ──上线──► RUNNING
 *   玩家/系统 ──► CANCELLED
 * </pre>
 *
 * <p>{@link #isTerminal()}：{@link #FAILED} / {@link #DONE} / {@link #CANCELLED} 不再迁移；
 * {@link #canTransitionTo(MonitorState)} 编码合法迁移，供引擎守卫状态变更。</p>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public enum MonitorState {
    /** 求值中 / 一轮执行中。 */
    RUNNING,
    /** re-arm 间隔中（两轮之间）。 */
    WAITING,
    /** 瞬时失败退避重试中（有预算上限，超预算 → FAILED）。 */
    BLOCKED,
    /** 永久失败 / 超预算 → 通知玩家，终态。 */
    FAILED,
    /** 玩家下线挂起；上线恢复。 */
    PAUSED,
    /** 目标达成 / ONE_SHOT 完成，终态。 */
    DONE,
    /** 玩家/系统取消，终态。 */
    CANCELLED;

    public boolean isTerminal() {
        return this == FAILED || this == DONE || this == CANCELLED;
    }

    /** 自迁移（幂等重置）始终合法；终态不得离开。 */
    public boolean canTransitionTo(MonitorState next) {
        if (next == null || next == this) {
            return next != null;
        }
        return switch (this) {
            case RUNNING -> next == WAITING || next == BLOCKED || next == FAILED || next == PAUSED || next == DONE || next == CANCELLED;
            case WAITING -> next == RUNNING || next == PAUSED || next == CANCELLED;
            case BLOCKED -> next == RUNNING || next == FAILED || next == PAUSED || next == CANCELLED;
            case PAUSED -> next == RUNNING || next == CANCELLED;
            case FAILED, DONE, CANCELLED -> false;
        };
    }
}
