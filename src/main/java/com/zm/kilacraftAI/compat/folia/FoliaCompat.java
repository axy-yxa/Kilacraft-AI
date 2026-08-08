package com.zm.kilacraftAI.compat.folia;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Folia 兼容调度工具类
 *
 * <p>统一封装 Spigot/Paper 和 Folia 之间的调度 API 差异。
 * 运行时自动检测是否为 Folia 环境，选择正确的调度方式。
 * 使用反射调用 Folia 特有 API，编译时无需依赖 Folia API。</p>
 *
 * <h3>Folia 核心差异：</h3>
 * <ul>
 *   <li>Folia 移除了 {@code Bukkit.getScheduler()} 的全局主线程概念</li>
 *   <li>命令执行需要通过 {@code GlobalRegionScheduler} 或 {@code EntityScheduler}</li>
 *   <li>定时任务需要使用 {@code GlobalRegionScheduler} / {@code AsyncScheduler}</li>
 *   <li>不存在"主线程"，每个区域有自己的线程</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-14
 */
public class FoliaCompat {

    /**
     * 是否运行在 Folia 环境下（延迟初始化）
     */
    private static boolean FOLIA;

    /**
     * 反射初始化结果（FOLIA=true 时非空，延迟初始化）
     */
    private static FoliaReflection REFLECTION;

    /**
     * 延迟初始化标记
     */
    private static boolean initialized = false;

    /**
     * 线程编号原子计数器（用于生成唯一线程名）
     */
    private static final AtomicInteger IO_THREAD_COUNTER = new AtomicInteger(0);

    /**
     * 插件全局异步 I/O 线程池，用于 LLM 调用、知识检索等 I/O 密集型异步操作
     *
     * <h3>容量推算示例：</h3>
     * <pre>
     *   核心线程 = CPU核数（不设下限）
     *   最大线程 = min(CPU*4, 128)（弹性4倍，封顶128）
     *   队列容量 = 最大线程数（与最大线程一致）
     *   -------------------------------------------------
     *   2核VPS:    核心=2,  最大=8,   队列=8   → 总容量 16
     *   4核服务器:  核心=4,  最大=16,  队列=16  → 总容量 32
     *   8核服务器:  核心=8,  最大=32,  队列=32  → 总容量 64
     *   16核服务器: 核心=16, 最大=64,  队列=64  → 总容量 128
     *   32核服务器: 核心=32, 最大=128, 队列=128 → 总容量 256
     *   64核服务器: 核心=64, 最大=128, 队列=128 → 总容量 256（封顶）
     * </pre>
     * <pre>
     *   超出后: 丢弃并记录警告（绝不允许阻塞 Bukkit 主线程）
     * </pre>
     */
    private static final int CPU = Runtime.getRuntime().availableProcessors();
    private static final int IO_MAX_THREADS = Math.min(CPU * 4, 128);
    private static final ThreadPoolExecutor IO_POOL = new ThreadPoolExecutor(CPU, IO_MAX_THREADS, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(IO_MAX_THREADS), r -> {
        Thread t = new Thread(r, "KilacraftAI-IO-" + IO_THREAD_COUNTER.incrementAndGet());
        t.setDaemon(true);
        return t;
    }, (r, executor) -> {
        PluginLoggerUtil.warn("I/O线程池", I18nService.tr("异步任务队列已满，丢弃任务（池状态: {}/{}，队列: {}）", executor.getActiveCount(), executor.getMaximumPoolSize(), executor.getQueue().size()));
    });

    /**
     * 获取全局异步 I/O 线程池
     */
    public static ExecutorService getIOPool() {
        return IO_POOL;
    }

    /**
     * 关闭全局 I/O 线程池（仅在插件 onDisable 时调用）
     */
    public static void shutdownIOPool() {
        PluginLoggerUtil.info("I/O线程池", I18nService.tr("正在关闭（活跃线程: {}，队列: {}）", IO_POOL.getActiveCount(), IO_POOL.getQueue().size()));
        IO_POOL.shutdown();
        try {
            if (!IO_POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                int remaining = IO_POOL.shutdownNow().size();
                PluginLoggerUtil.warn("I/O线程池", I18nService.tr("等待超时，强制关闭（残留任务: {}）", remaining));
            } else {
                PluginLoggerUtil.info("I/O线程池", I18nService.tr("已安全关闭"));
            }
        } catch (InterruptedException e) {
            IO_POOL.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 延迟初始化 Folia 检测（首次调用时执行，确保 I18nService 和 PluginLoggerUtil 已就绪）
     */
    private static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        // 精确检测：优先探测 Folia 核心类 RegionizedServer（仅真正的 Folia/lophine 存在）
        // Paper 系分支（Leaf、Purpur 等）已合并 Folia 调度器 API 接口，但不能仅凭接口判断
        boolean hasFoliaCore = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            hasFoliaCore = true;
        } catch (ClassNotFoundException ignored) {
            // 非 Folia 环境
        }

        if (hasFoliaCore) {
            try {
                REFLECTION = new FoliaReflection();
                FOLIA = true;
            } catch (ReflectiveOperationException e) {
                REFLECTION = null;
                FOLIA = false;
                PluginLoggerUtil.warn("Folia兼容", I18nService.tr("Folia API 反射初始化失败，回退到 Spigot 模式: {}", e.getMessage()), e);
            }
        } else {
            FOLIA = false;
            REFLECTION = null;
        }

        // 输出 I/O 线程池初始化日志（此处 PluginLoggerUtil 和 I18nService 已就绪）
        String serverName = Bukkit.getServer().getName();
        PluginLoggerUtil.info("I/O线程池", I18nService.tr("已初始化（核心: {}，最大: {}，队列: {}，服务端: {}，Folia: {}）", IO_POOL.getCorePoolSize(), IO_POOL.getMaximumPoolSize(), IO_POOL.getQueue().remainingCapacity() + IO_POOL.getQueue().size(), serverName, FOLIA));
    }

    private FoliaCompat() {
    }

    /**
     * 是否运行在 Folia 环境下
     */
    public static boolean isFolia() {
        ensureInitialized();
        return FOLIA;
    }

    /**
     * 在全局区域执行任务（相当于 Spigot 的 runTask）
     */
    public static void runTask(Plugin plugin, Runnable task) {
        ensureInitialized();
        if (FOLIA) {
            REFLECTION.invokeGlobalRun(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * 延迟执行任务（相当于 Spigot 的 runTaskLater）
     */
    public static ScheduledTask runTaskLater(Plugin plugin, Runnable task, long delay) {
        ensureInitialized();
        if (FOLIA) {
            Object handle = REFLECTION.invokeGlobalRunDelayed(plugin, task, delay);
            return handle != null ? new ScheduledTask(handle) : null;
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            return new ScheduledTask(bt);
        }
    }

    /**
     * 定时重复执行异步任务（用于轮询场景，如 CustomWatchTask）
     *
     * @return 可取消的任务句柄
     */
    public static ScheduledTask runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long intervalTicks) {
        ensureInitialized();
        if (FOLIA) {
            Object foliaTask = REFLECTION.scheduleAsyncAtFixedRate(plugin, task, delayTicks, intervalTicks);
            return new ScheduledTask(foliaTask);
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, intervalTicks);
            return new ScheduledTask(bukkitTask);
        }
    }

    /**
     * 执行命令（自动适配 Folia/Spigot 调度方式），fire-and-forget
     *
     * <p>lophine/Folia 特殊处理：如果 sender 是 Player，使用 EntityScheduler 而非 GlobalRegionScheduler</p>
     */
    public static void dispatchCommand(org.bukkit.command.CommandSender sender, String command) {
        ensureInitialized();
        if (FOLIA) {
            // 如果 sender 是 Player，使用 EntityScheduler
            if (sender instanceof org.bukkit.entity.Player player) {
                REFLECTION.invokeEntityRun(player, () -> Bukkit.dispatchCommand(sender, command));
            } else {
                REFLECTION.invokeGlobalExecute(KilacraftAI.getInstance(), () -> Bukkit.dispatchCommand(sender, command));
            }
        } else {
            if (Bukkit.isPrimaryThread()) {
                Bukkit.dispatchCommand(sender, command);
            } else {
                Bukkit.getScheduler().runTask(KilacraftAI.getInstance(), () -> Bukkit.dispatchCommand(sender, command));
            }
        }
    }

    /**
     * 同步执行命令并等待结果（阻塞）
     *
     * <p>lophine/Folia 特殊处理：如果 sender 是 Player，使用 EntityScheduler 而非 GlobalRegionScheduler</p>
     */
    public static boolean dispatchCommandSync(org.bukkit.command.CommandSender sender, String command, long timeoutSeconds) {
        ensureInitialized();
        if (FOLIA) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();

            // 如果 sender 是 Player，使用 EntityScheduler
            if (sender instanceof org.bukkit.entity.Player player) {
                REFLECTION.invokeEntityRun(player, () -> {
                    try {
                        future.complete(Bukkit.dispatchCommand(sender, command));
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
            } else {
                REFLECTION.invokeGlobalRun(KilacraftAI.getInstance(), () -> {
                    try {
                        future.complete(Bukkit.dispatchCommand(sender, command));
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
            }
            return awaitFuture(future, timeoutSeconds, I18nService.tr("命令执行超时: /{}", command), I18nService.tr("命令执行失败: /{}", command));
        } else {
            if (Bukkit.isPrimaryThread()) {
                return Bukkit.dispatchCommand(sender, command);
            }
            try {
                return Bukkit.getScheduler().callSyncMethod(KilacraftAI.getInstance(), () -> Bukkit.dispatchCommand(sender, command)).get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                return unwrapExecutionException(e, I18nService.tr("命令执行失败: /{}", command));
            } catch (TimeoutException e) {
                throw new RuntimeException(I18nService.tr("命令执行超时: /{}", command), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(I18nService.tr("命令执行被中断: /{}", command), e);
            }
        }
    }

    /**
     * 异步执行命令，返回 CompletableFuture
     *
     * <p>lophine/Folia 特殊处理：如果 sender 是 Player，使用 EntityScheduler 而非 GlobalRegionScheduler</p>
     */
    public static CompletableFuture<Boolean> dispatchCommandAsync(org.bukkit.command.CommandSender sender, String command) {
        ensureInitialized();
        if (FOLIA) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();

            // 如果 sender 是 Player，使用 EntityScheduler（lophine 要求）
            if (sender instanceof org.bukkit.entity.Player player) {
                REFLECTION.invokeEntityRun(player, () -> {
                    try {
                        result.complete(Bukkit.dispatchCommand(sender, command));
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    }
                });
            } else {
                // 其他发送者使用 GlobalRegionScheduler
                REFLECTION.invokeGlobalRun(KilacraftAI.getInstance(), () -> {
                    try {
                        result.complete(Bukkit.dispatchCommand(sender, command));
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    }
                });
            }
            return result;
        } else {
            if (Bukkit.isPrimaryThread()) {
                return CompletableFuture.completedFuture(Bukkit.dispatchCommand(sender, command));
            }
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(KilacraftAI.getInstance(), () -> {
                try {
                    result.complete(Bukkit.dispatchCommand(sender, command));
                } catch (Exception e) {
                    result.completeExceptionally(e);
                }
            });
            return result;
        }
    }

    /**
     * 在主线程/全局区域同步执行 Supplier 并返回结果
     */
    public static <T> T callSync(Plugin plugin, Supplier<T> supplier, long timeoutSeconds) {
        ensureInitialized();
        if (FOLIA) {
            CompletableFuture<T> future = new CompletableFuture<>();
            REFLECTION.invokeGlobalRun(plugin, () -> {
                try {
                    future.complete(supplier.get());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return awaitFuture(future, timeoutSeconds, I18nService.tr("同步调用超时"), I18nService.tr("同步调用失败"));
        } else {
            // 已在主线程时直接同步执行，避免 callSyncMethod 把任务排回主线程队列造成自死锁
            if (Bukkit.isPrimaryThread()) {
                return supplier.get();
            }
            try {
                return Bukkit.getScheduler().callSyncMethod(plugin, supplier::get).get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                return unwrapExecutionException(e, I18nService.tr("同步调用失败"));
            } catch (TimeoutException e) {
                throw new RuntimeException(I18nService.tr("同步调用超时"), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(I18nService.tr("同步调用被中断"), e);
            }
        }
    }

    /**
     * 在指定玩家实体所属的区域线程同步执行 Supplier 并返回结果
     *
     * <p>lophine/Folia 特殊要求：Player 相关的 API（如 getTargetBlock）必须在玩家所在区域线程执行，
     * 不能使用 GlobalRegionScheduler，否则会报 getCurrentWorldData() is null</p>
     *
     * @param player         目标玩家
     * @param supplier       要执行的任务
     * @param timeoutSeconds 超时时间（秒）
     * @return 执行结果
     */
    public static <T> T callSyncOnEntity(org.bukkit.entity.Player player, Supplier<T> supplier, long timeoutSeconds) {
        ensureInitialized();
        if (FOLIA) {
            CompletableFuture<T> future = new CompletableFuture<>();
            REFLECTION.invokeEntityRun(player, () -> {
                try {
                    future.complete(supplier.get());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return awaitFuture(future, timeoutSeconds, I18nService.tr("实体同步调用超时"), I18nService.tr("实体同步调用失败"));
        } else {
            // Spigot/Paper: 已在主线程时直接同步执行，避免 callSyncMethod 排队造成自死锁
            if (Bukkit.isPrimaryThread()) {
                return supplier.get();
            }
            try {
                return Bukkit.getScheduler().callSyncMethod(KilacraftAI.getInstance(), supplier::get).get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                return unwrapExecutionException(e, I18nService.tr("实体同步调用失败"));
            } catch (TimeoutException e) {
                throw new RuntimeException(I18nService.tr("实体同步调用超时"), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(I18nService.tr("实体同步调用被中断"), e);
            }
        }
    }

    /**
     * 检查当前是否可以安全执行主线程操作
     * <p>Folia 下始终返回 false（无全局主线程概念）</p>
     */
    public static boolean isPrimaryThread() {
        ensureInitialized();
        if (FOLIA) {
            return false;
        }
        return Bukkit.isPrimaryThread();
    }

    /**
     * 统一的可取消任务句柄，封装 Folia ScheduledTask 和 BukkitTask
     */
    public static class ScheduledTask {
        private final Object task;

        ScheduledTask(Object task) {
            this.task = task;
        }

        /**
         * 取消任务
         */
        public void cancel() {
            if (FOLIA) {
                REFLECTION.cancelFoliaTask(task);
            } else if (task instanceof BukkitTask bt) {
                bt.cancel();
            }
        }

        /**
         * 任务是否已取消
         */
        public boolean isCancelled() {
            if (FOLIA) {
                return REFLECTION.isFoliaTaskCancelled(task);
            } else if (task instanceof BukkitTask bt) {
                return bt.isCancelled();
            }
            return true;
        }
    }

    /**
     * 统一等待 CompletableFuture 并处理异常，消除重复的 catch 块
     */
    private static <T> T awaitFuture(CompletableFuture<T> future, long timeoutSeconds, String timeoutMsg, String failMsg) {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException(timeoutMsg, e);
        } catch (ExecutionException e) {
            return unwrapExecutionException(e, failMsg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(failMsg + I18nService.tr("（被中断）"), e);
        }
    }

    /**
     * 解包 ExecutionException，优先抛出 RuntimeException
     */
    @SuppressWarnings("unchecked")
    private static <T> T unwrapExecutionException(ExecutionException e, String msg) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException re) throw re;
        throw new RuntimeException(msg, cause);
    }
}
