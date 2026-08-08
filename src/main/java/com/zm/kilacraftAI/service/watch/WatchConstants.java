package com.zm.kilacraftAI.service.watch;

/**
 * Watch 系统常量：事件归属半径、执行超时、容错阈值等。
 *
 * @author Zm_Mmm
 * @since 2026-07-24
 */
final class WatchConstants {

    private WatchConstants() {
    }

    /**
     * 熔炉烧好事件归属半径（方块）。
     */
    static final double FURNACE_RADIUS = 16.0;
    /**
     * 作物成熟事件归属半径（方块）。
     */
    static final double CROP_RADIUS = 32.0;
    /**
     * 实体生成事件归属半径（方块）。
     */
    static final double ENTITY_RADIUS = 64.0;

    /**
     * skill action 执行超时（秒），防止卡死拖死轮询定时器。
     */
    static final long SKILL_EXECUTION_TIMEOUT_SECONDS = 5L;

    /**
     * 连续失败上限（skill 执行失败/字段缺失），超过自动删除 watch 并通知 AI。
     */
    static final int MAX_CONSECUTIVE_FAILURES = 3;
}
