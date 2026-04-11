package com.zm.kilacraftAI.skills.afktask;

/**
 * 挂机任务状态
 *
 * @author Zm_Mmm
 * @since 2026-04-09
 */
public enum AFKTaskStatus {

    /**
     * 等待启动
     */
    PENDING,

    /**
     * 运行中
     */
    RUNNING,

    /**
     * 已完成（条件满足，自动结束）
     */
    COMPLETED,

    /**
     * 已取消（玩家主动取消或下线）
     */
    CANCELLED
}
