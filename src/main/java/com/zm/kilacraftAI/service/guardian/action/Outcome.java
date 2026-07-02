package com.zm.kilacraftAI.service.guardian.action;

/**
 * 守护动作的统一结果契约（§3.5）。任务的唯一解读对象——新增任何 skill，驱动器不用改，
 * 它只认 Outcome 与目标谓词，这正是「换 skill 报不同错」的根治。
 *
 * <ul>
 *   <li>{@link #SUCCESS} — 动作成功 → 交 Policy 决定 re-arm / 收尾</li>
 *   <li>{@link #TRANSIENT_FAIL} — 瞬时失败（超时/限流/网络抖动）→ 退避重试，有预算上限</li>
 *   <li>{@link #PERMANENT_FAIL} — 永久失败（权限/不存在/参数非法）→ 置 FAILED，通知，停</li>
 *   <li>{@link #NEED_INFO} — 需玩家补全 → {@code PendingResumeManager} 已接管（executeSkillByIntent 内自动 save），暂停不重试</li>
 *   <li>{@link #IN_PROGRESS} — 多步中间态 / 依赖未满足 → 继续</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-07-01
 */
public enum Outcome {

    SUCCESS,
    TRANSIENT_FAIL,
    PERMANENT_FAIL,
    NEED_INFO,
    IN_PROGRESS;

    /** 是否成功（Policy 据此决定收尾或 re-arm）。 */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /** 是否可退避重试（仅瞬时失败）。 */
    public boolean isRetryable() {
        return this == TRANSIENT_FAIL;
    }

    /** 是否需玩家补全（暂停不重试，等续体恢复）。 */
    public boolean needsPlayerInput() {
        return this == NEED_INFO;
    }
}
