package com.zm.kilacraftAI.compat.folia;

import com.zm.kilacraftAI.KilacraftAI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.*;

/**
 * Folia 兼容调度工具类（纯反射实现）
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
     * 是否运行在 Folia 环境下
     */
    private static final boolean FOLIA;

    /**
     * 反射初始化结果（FOLIA=true 时非空）
     */
    private static final FoliaReflection REFLECTION;

    static {
        FoliaReflection reflection;
        boolean folia;
        try {
            reflection = new FoliaReflection();
            folia = true;
        } catch (ReflectiveOperationException e) {
            reflection = null;
            folia = false;
            // 诊断日志：帮助排查 Folia 检测失败的原因
            System.getLogger("Kilacraft-AI").log(System.Logger.Level.WARNING, "[FoliaCompat] Folia API 反射初始化失败，回退到 Spigot 模式: " + e.getMessage(), e);
        }
        FOLIA = folia;
        REFLECTION = reflection;
    }

    private FoliaCompat() {
        // 工具类禁止实例化
    }

    /**
     * 是否运行在 Folia 环境下
     */
    public static boolean isFolia() {
        return FOLIA;
    }

    // ==================== 全局调度（无区域依赖） ====================

    /**
     * 在全局区域执行任务（相当于 Spigot 的 runTask）
     */
    public static void runTask(Plugin plugin, Runnable task) {
        if (FOLIA) {
            REFLECTION.invokeGlobalRun(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * 延迟执行任务（相当于 Spigot 的 runTaskLater）
     */
    public static void runTaskLater(Plugin plugin, Runnable task, long delay) {
        if (FOLIA) {
            REFLECTION.invokeGlobalRunDelayed(plugin, task, delay);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    /**
     * 定时重复执行异步任务（用于轮询场景，如 CustomWatchTask）
     *
     * @return 可取消的任务句柄
     */
    public static ScheduledTask runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long intervalTicks) {
        if (FOLIA) {
            Object foliaTask = REFLECTION.scheduleAsyncAtFixedRate(plugin, task, delayTicks, intervalTicks);
            return new ScheduledTask(foliaTask);
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, intervalTicks);
            return new ScheduledTask(bukkitTask);
        }
    }

    // ==================== 命令调度 ====================

    /**
     * 执行命令（自动适配 Folia/Spigot 调度方式），fire-and-forget
     * 
     * <p>lophine/Folia 特殊处理：如果 sender 是 Player，使用 EntityScheduler 而非 GlobalRegionScheduler</p>
     */
    public static void dispatchCommand(org.bukkit.command.CommandSender sender, String command) {
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
            return awaitFuture(future, timeoutSeconds, "命令执行超时: /" + command, "命令执行失败: /" + command);
        } else {
            if (Bukkit.isPrimaryThread()) {
                return Bukkit.dispatchCommand(sender, command);
            }
            try {
                return Bukkit.getScheduler().callSyncMethod(KilacraftAI.getInstance(), () -> Bukkit.dispatchCommand(sender, command)).get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                return unwrapExecutionException(e, "命令执行失败: /" + command);
            } catch (TimeoutException e) {
                throw new RuntimeException("命令执行超时: /" + command, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("命令执行被中断: /" + command, e);
            }
        }
    }

    /**
     * 异步执行命令，返回 CompletableFuture
     * 
     * <p>lophine/Folia 特殊处理：如果 sender 是 Player，使用 EntityScheduler 而非 GlobalRegionScheduler</p>
     */
    public static CompletableFuture<Boolean> dispatchCommandAsync(org.bukkit.command.CommandSender sender, String command) {
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

    // ==================== 主线程同步调用 ====================

    /**
     * 在主线程/全局区域同步执行 Supplier 并返回结果
     */
    public static <T> T callSync(Plugin plugin, java.util.function.Supplier<T> supplier, long timeoutSeconds) {
        if (FOLIA) {
            CompletableFuture<T> future = new CompletableFuture<>();
            REFLECTION.invokeGlobalRun(plugin, () -> {
                try {
                    future.complete(supplier.get());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return awaitFuture(future, timeoutSeconds, "同步调用超时", "同步调用失败");
        } else {
            try {
                return Bukkit.getScheduler().callSyncMethod(plugin, supplier::get).get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                return unwrapExecutionException(e, "同步调用失败");
            } catch (TimeoutException e) {
                throw new RuntimeException("同步调用超时", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("同步调用被中断", e);
            }
        }
    }

    /**
     * 在指定玩家实体所属的区域线程同步执行 Supplier 并返回结果
     * 
     * <p>lophine/Folia 特殊要求：Player 相关的 API（如 getTargetBlock）必须在玩家所在区域线程执行，
     * 不能使用 GlobalRegionScheduler，否则会报 getCurrentWorldData() is null</p>
     * 
     * @param player 目标玩家
     * @param supplier 要执行的任务
     * @param timeoutSeconds 超时时间（秒）
     * @return 执行结果
     */
    public static <T> T callSyncOnEntity(org.bukkit.entity.Player player, java.util.function.Supplier<T> supplier, long timeoutSeconds) {
        if (FOLIA) {
            CompletableFuture<T> future = new CompletableFuture<>();
            REFLECTION.invokeEntityRun(player, () -> {
                try {
                    future.complete(supplier.get());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return awaitFuture(future, timeoutSeconds, "实体同步调用超时", "实体同步调用失败");
        } else {
            // Spigot/Paper: 直接调度到主线程
            try {
                return Bukkit.getScheduler().callSyncMethod(KilacraftAI.getInstance(), supplier::get).get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                return unwrapExecutionException(e, "实体同步调用失败");
            } catch (TimeoutException e) {
                throw new RuntimeException("实体同步调用超时", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("实体同步调用被中断", e);
            }
        }
    }

    /**
     * 检查当前是否可以安全执行主线程操作
     * <p>Folia 下始终返回 false（无全局主线程概念）</p>
     */
    public static boolean isPrimaryThread() {
        if (FOLIA) {
            return false;
        }
        return Bukkit.isPrimaryThread();
    }

    // ==================== 统一任务句柄 ====================

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

    // ==================== 通用工具方法 ====================

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
            throw new RuntimeException(failMsg + "（被中断）", e);
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
