package com.zm.kilacraftAI.scheduler;

/**
 * 托管定时任务接口
 *
 * <p>所有由 {@link TaskScheduler} 统一管理的周期性任务必须实现此接口。
 * TaskScheduler 负责调度注册、CAS 互斥、统一日志和生命周期管理。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-06
 */
public interface ManagedTask {

    /**
     * 任务名称（唯一标识，用于日志和调试）
     *
     * @return 任务名称
     */
    String name();

    /**
     * 任务描述（启动日志展示）
     *
     * @return 任务描述
     */
    String description();

    /**
     * 首次执行延迟（ticks）
     *
     * @return 延迟 ticks 数
     */
    long delayTicks();

    /**
     * 执行周期（ticks）
     *
     * @return 周期 ticks 数
     */
    long intervalTicks();

    /**
     * 执行任务逻辑
     *
     * @return 实际处理的数据条数；0 表示无数据可处理，调度器将跳过日志输出
     */
    int execute();

    /**
     * 是否启用（条件性任务可覆写，如 retentionDays=0 时不启动）
     *
     * @return true 表示启用并注册定时任务
     */
    default boolean enabled() {
        return true;
    }
}
