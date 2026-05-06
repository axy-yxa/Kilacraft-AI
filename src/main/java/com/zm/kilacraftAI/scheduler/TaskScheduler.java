package com.zm.kilacraftAI.scheduler;

import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 统一定时任务调度器
 *
 * <p>集中管理所有周期性任务的注册、互斥保护、统一日志和生命周期管理。</p>
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>注册：调用 {@link FoliaCompat#runAsyncTimer} 启动任务并保存句柄</li>
 *   <li>互斥：{@link AtomicBoolean} CAS 保护，上次未完成时跳过本次</li>
 *   <li>日志：统一通过 {@link PluginLogger}，模块名 "定时任务"</li>
 *   <li>关闭：{@link #shutdownAll()} 取消所有任务句柄</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-05-06
 */
public class TaskScheduler {

    private static final String LOG_MODULE = "定时任务";

    private final Plugin plugin;
    private final List<ManagedTaskHandle> tasks = new ArrayList<>();

    public TaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 注册并启动托管任务
     *
     * <p>如果 {@link ManagedTask#enabled()} 返回 false，跳过注册并记录日志。</p>
     *
     * @param task 托管任务
     */
    public void register(ManagedTask task) {
        if (!task.enabled()) {
            PluginLogger.info(LOG_MODULE, "跳过注册（已禁用）: {} ({})", task.name(), task.description());
            return;
        }

        ManagedTaskHandle handle = new ManagedTaskHandle(task);
        handle.handle = FoliaCompat.runAsyncTimer(plugin, () -> executeWrapped(handle), task.delayTicks(), task.intervalTicks());
        tasks.add(handle);

        PluginLogger.info(LOG_MODULE, "已启动: {} ({})", task.name(), task.description());
    }

    /**
     * 取消所有托管任务（onDisable 调用）
     */
    public void shutdownAll() {
        for (ManagedTaskHandle handle : tasks) {
            if (handle.handle != null) {
                handle.handle.cancel();
            }
        }
        PluginLogger.info(LOG_MODULE, "已关闭 {} 个定时任务", tasks.size());
    }

    /**
     * 获取所有任务运行状态摘要（调试命令用）
     *
     * @return 状态摘要行列表
     */
    public List<String> getStatusSummary() {
        List<String> lines = new ArrayList<>();
        lines.add("§f[TaskScheduler] §7已注册 §e" + tasks.size() + " §7个定时任务:");

        for (ManagedTaskHandle h : tasks) {
            String intervalStr = formatInterval(h.task.intervalTicks());
            String processed = "§f" + h.totalProcessed + "条";
            String lastExec = h.lastExecuteTime > 0 ? "§f" + formatElapsed(System.currentTimeMillis() - h.lastExecuteTime) + "前" : "§8未执行";
            String status = h.lastError != null ? "§c异常: " + truncate(h.lastError, 30) : h.lastExecuteTime == 0 ? "§7等待首次" : "§a正常";

            lines.add(String.format("  §f%-8s §7| §e%-5s §7| 累计 §e%s §7| 上次: %s §7| %s", h.task.name(), intervalStr, processed, lastExec, status));
        }
        return lines;
    }

    // ==================== 内部方法 ====================

    /**
     * 包装任务执行：CAS 互斥 + 日志 + 异常捕获
     */
    private void executeWrapped(ManagedTaskHandle handle) {
        if (!handle.running.compareAndSet(false, true)) {
            PluginLogger.debug(LOG_MODULE, "[{}] 上次尚未完成，跳过本次", handle.task.name());
            return;
        }

        long start = System.currentTimeMillis();
        try {
            int processed = handle.task.execute();
            long elapsed = System.currentTimeMillis() - start;
            handle.lastExecuteTime = System.currentTimeMillis();
            handle.totalProcessed += processed;
            if (processed > 0) {
                PluginLogger.debug(LOG_MODULE, "[{}] 执行完成 (耗时{}ms, 本轮{}条, 累计{}条)", handle.task.name(), elapsed, processed, handle.totalProcessed);
            } else {
                PluginLogger.debug(LOG_MODULE, "[{}] 执行完成 (耗时{}ms, 无新数据)", handle.task.name(), elapsed);
            }
        } catch (Exception e) {
            handle.lastErrorTime = System.currentTimeMillis();
            handle.lastError = e.getMessage();
            PluginLogger.error(LOG_MODULE, "[{}] 执行失败: {}", handle.task.name(), e.getMessage(), e);
        } finally {
            handle.running.set(false);
        }
    }

    /**
     * 将 ticks 转换为人类可读的时间间隔
     */
    private String formatInterval(long ticks) {
        long seconds = ticks / 20;
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "min";
        long hours = seconds / 3600;
        if (hours < 24) return hours + "h";
        return (hours / 24) + "d";
    }

    /**
     * 将毫秒转换为人类可读的经过时间
     */
    private String formatElapsed(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "min";
        long hours = seconds / 3600;
        if (hours < 24) return hours + "h";
        return (hours / 24) + "d";
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() > maxLen ? value.substring(0, maxLen) + "..." : value;
    }

    // ==================== 内部句柄 ====================

    /**
     * 托管任务句柄：包装 ScheduledTask + 运行状态
     */
    static class ManagedTaskHandle {
        final ManagedTask task;
        FoliaCompat.ScheduledTask handle;
        final AtomicBoolean running = new AtomicBoolean(false);
        volatile long lastExecuteTime;
        volatile long totalProcessed;
        volatile long lastErrorTime;
        volatile String lastError;

        ManagedTaskHandle(ManagedTask task) {
            this.task = task;
        }
    }
}
