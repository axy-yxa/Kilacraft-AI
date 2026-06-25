package com.zm.kilacraftAI.service.health;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 手动采样会话
 *
 * <p>管理 /kila profile 命令的采样生命周期。</p>
 *
 * <p>状态模型：IDLE ↔ RUNNING</p>
 * <ul>
 *   <li>IDLE：空闲，可接受新的 start</li>
 *   <li>RUNNING：从 start 到分析完成的整个过程，包括采样、URL 捕获、AI 诊断</li>
 * </ul>
 *
 * <p>中断（stop/掉线/超时）直接 reset 回 IDLE。分析正常完成也由 Guardian reset 回 IDLE。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-10
 */
public class ManualSession {

    private static final String LOG_PREFIX = "健康监控";
    /**
     * 手动采样超时自动清理时间（毫秒），10 分钟
     *
     * <p>正常流程由 reset() 清理（分析完成/stop/掉线），此超时仅为兜底。
     * 最大正常耗时约 5 分钟（采样 120s + URL 捕获 30s + AI 诊断 120s + 报告生成），
     * 10 分钟绰绰有余。</p>
     */
    private static final long SESSION_TIMEOUT_MS = 10 * 60 * 1000L;

    private final ReentrantLock lock = new ReentrantLock();
    private Status status = Status.IDLE;
    /**
     * 启动时间戳，用于超时清理
     */
    private long startTime;
    /**
     * 发起采样的玩家名
     */
    private String operatorName;
    /**
     * 采样时长（秒）
     */
    private int durationSeconds;
    /**
     * Profiler 完成后的 viewer URL（由 profile start 捕获）
     */
    private String profilerUrl;
    /**
     * 采样开始前的服务器活动快照（由 profile start 触发前采集）
     *
     * <p>用于和采样结束后采集的 after 快照对比，计算玩家移动距离和区块变化。</p>
     */
    private ServerActivitySnapshot activityBefore;

    /**
     * 获取当前状态
     */
    public Status getStatus() {
        lock.lock();
        try {
            return status;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 是否正在运行（IDLE → false，RUNNING → true）
     */
    public boolean isRunning() {
        lock.lock();
        try {
            return status == Status.RUNNING;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 尝试启动手动采样
     *
     * @param operatorName    操作者玩家名
     * @param durationSeconds 采样时长
     * @return true 表示启动成功
     */
    public boolean tryStart(String operatorName, int durationSeconds) {
        lock.lock();
        try {
            if (status != Status.IDLE) {
                return false;
            }
            this.operatorName = operatorName;
            this.durationSeconds = durationSeconds;
            this.status = Status.RUNNING;
            this.startTime = System.currentTimeMillis();
            PluginLoggerUtil.debug(LOG_PREFIX, "ManualSession 启动: operator={}, duration={}s", operatorName, durationSeconds);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 重置会话为 IDLE 状态（线程安全，外部调用）
     */
    public void reset() {
        lock.lock();
        try {
            resetInternal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 内部重置（调用方必须已持有 lock）
     *
     * <p>供已持锁的方法（如 {@link #checkAndCleanupTimeout()}）直接调用，
     * 避免不必要的锁重入。</p>
     */
    private void resetInternal() {
        this.status = Status.IDLE;
        this.operatorName = null;
        this.durationSeconds = 0;
        this.startTime = 0;
        this.profilerUrl = null;
        this.activityBefore = null;
    }

    /**
     * 检查并清理超时会话
     *
     * <p>如果 RUNNING 状态超过 30 分钟，自动重置为 IDLE。</p>
     *
     * @return true 表示已清理超时会话
     */
    public boolean checkAndCleanupTimeout() {
        lock.lock();
        try {
            if (status == Status.RUNNING && startTime > 0) {
                if (System.currentTimeMillis() - startTime > SESSION_TIMEOUT_MS) {
                    PluginLoggerUtil.warn(LOG_PREFIX, "ManualSession 超时自动清理: operator={}", operatorName);
                    resetInternal();
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public String getOperatorName() {
        lock.lock();
        try {
            return operatorName;
        } finally {
            lock.unlock();
        }
    }

    public int getDurationSeconds() {
        lock.lock();
        try {
            return durationSeconds;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取 session 启动时间戳（用于文件新鲜度校验）
     */
    public long getStartTime() {
        lock.lock();
        try {
            return startTime;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取 Profiler viewer URL
     */
    public String getProfilerUrl() {
        lock.lock();
        try {
            return profilerUrl;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 原子操作：如果当前状态为 RUNNING 且操作者为指定玩家，则重置会话
     *
     * <p>将 check + reset 合并为单次锁获取，避免 TOCTOU 竞态。</p>
     *
     * @param expectedOperator 期望的操作者名称
     * @return true 表示匹配成功并已重置
     */
    public boolean resetIfOperator(String expectedOperator) {
        lock.lock();
        try {
            if (status == Status.RUNNING && expectedOperator.equals(operatorName)) {
                PluginLoggerUtil.debug(LOG_PREFIX, "ManualSession: 操作者 {} 掉线，重置会话", expectedOperator);
                resetInternal();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 设置 Profiler viewer URL（由 profile start 命令捕获后调用）
     */
    public void setProfilerUrl(String profilerUrl) {
        lock.lock();
        try {
            this.profilerUrl = profilerUrl;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 设置采样开始前的活动快照
     */
    public void setActivityBefore(ServerActivitySnapshot snapshot) {
        lock.lock();
        try {
            this.activityBefore = snapshot;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取采样开始前的活动快照
     */
    public ServerActivitySnapshot getActivityBefore() {
        lock.lock();
        try {
            return activityBefore;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 手动采样状态枚举
     */
    public enum Status {
        /**
         * 空闲，可接受新的 start
         */
        IDLE,
        /**
         * 进行中（采样 → URL 捕获 → AI 诊断，全流程）
         */
        RUNNING
    }
}
