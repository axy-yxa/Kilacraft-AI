package com.zm.kilacraftAI.compat.folia;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 封装所有 Folia API 的反射调用逻辑。
 *
 * <p>兼容原版 Folia（Paper API）和 lophine（Folia 分支）的 API 差异：
 * <ul>
 *   <li>lophine 的 Scheduler 和 ScheduledTask 均为接口（interface），原 Folia 为类</li>
 *   <li>lophine 的 ScheduledTask.cancel() 返回 CancelledState，原 Folia 返回 void</li>
 * </ul>
 * </p>
 *
 * @author Zm_Mmm
 * @since 2026-04-14
 */
class FoliaReflection {

    // ---- Folia 内部类全限定名 ----
    private static final String CLASS_GLOBAL_SCHEDULER = "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler";
    private static final String CLASS_ENTITY_SCHEDULER = "io.papermc.paper.threadedregions.scheduler.EntityScheduler";
    private static final String CLASS_ASYNC_SCHEDULER = "io.papermc.paper.threadedregions.scheduler.AsyncScheduler";
    private static final String CLASS_SCHEDULED_TASK = "io.papermc.paper.threadedregions.scheduler.ScheduledTask";

    // ---- 缓存的 Folia Class 对象 ----
    private final Class<?> globalSchedulerClass;
    private final Class<?> entitySchedulerClass;
    private final Class<?> asyncSchedulerClass;
    private final Class<?> scheduledTaskClass;

    // ---- 缓存的 MethodHandle ----
    private final MethodHandle mhGetGlobalScheduler;
    private final MethodHandle mhGetEntityScheduler;
    private final MethodHandle mhGetAsyncScheduler;
    private final MethodHandle mhGlobalRun;
    private final MethodHandle mhGlobalExecute;
    private final MethodHandle mhGlobalRunDelayed;
    private final MethodHandle mhEntityRun;
    private final MethodHandle mhAsyncRunAtFixedRate;
    private final MethodHandle mhTaskCancel;
    private final MethodHandle mhTaskIsCancelled;

    /**
     * ticks → 毫秒的转换常量
     */
    private static final long TICK_TO_MS = 50L;

    FoliaReflection() throws ReflectiveOperationException {
        globalSchedulerClass = Class.forName(CLASS_GLOBAL_SCHEDULER);
        entitySchedulerClass = Class.forName(CLASS_ENTITY_SCHEDULER);
        asyncSchedulerClass = Class.forName(CLASS_ASYNC_SCHEDULER);
        scheduledTaskClass = Class.forName(CLASS_SCHEDULED_TASK);

        MethodHandles.Lookup lookup = MethodHandles.publicLookup();

        // Bukkit 静态方法：获取调度器实例
        mhGetGlobalScheduler = lookup.findStatic(Bukkit.class, "getGlobalRegionScheduler", MethodType.methodType(globalSchedulerClass));
        mhGetAsyncScheduler = lookup.findStatic(Bukkit.class, "getAsyncScheduler", MethodType.methodType(asyncSchedulerClass));

        // GlobalRegionScheduler 方法（接口/类均可，findVirtual 兼容两者）
        mhGlobalRun = lookup.findVirtual(globalSchedulerClass, "run", MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class));
        mhGlobalRunDelayed = lookup.findVirtual(globalSchedulerClass, "runDelayed", MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class, long.class));

        // execute(Plugin, Runnable) — lophine 和新版 Folia 有此方法，旧版可能没有
        MethodHandle globalExecute;
        try {
            globalExecute = lookup.findVirtual(globalSchedulerClass, "execute", MethodType.methodType(void.class, Plugin.class, Runnable.class));
        } catch (NoSuchMethodException e) {
            globalExecute = null;
        }
        mhGlobalExecute = globalExecute;

        // EntityScheduler 方法：Entity.getScheduler()
        mhGetEntityScheduler = lookup.findVirtual(org.bukkit.entity.Entity.class, "getScheduler", MethodType.methodType(entitySchedulerClass));
        // EntityScheduler.run(Plugin, Consumer<ScheduledTask>, Runnable) — 第二个参数是 task，第三个是 retired
        mhEntityRun = lookup.findVirtual(entitySchedulerClass, "run", MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class, Runnable.class));

        // AsyncScheduler.runAtFixedRate(Plugin, Consumer, long, long, TimeUnit)
        mhAsyncRunAtFixedRate = lookup.findVirtual(asyncSchedulerClass, "runAtFixedRate", MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class));

        // ScheduledTask.cancel() — lophine 返回 CancelledState，原 Folia 返回 void
        // 兼容策略：先尝试 CancelledState 返回值，再回退到 void
        MethodHandle taskCancel;
        try {
            // lophine: cancel() returns CancelledState
            taskCancel = lookup.findVirtual(scheduledTaskClass, "cancel", MethodType.methodType(globalSchedulerClass.getClass().getClassLoader().loadClass("io.papermc.paper.threadedregions.scheduler.ScheduledTask$CancelledState")));
        } catch (Exception e1) {
            try {
                // 原版 Folia: cancel() returns void
                taskCancel = lookup.findVirtual(scheduledTaskClass, "cancel", MethodType.methodType(void.class));
            } catch (Exception e2) {
                taskCancel = null;
            }
        }
        mhTaskCancel = taskCancel;

        // ScheduledTask.isCancelled() — lophine 是 default 方法，原 Folia 是普通方法
        MethodHandle taskIsCancelled;
        try {
            taskIsCancelled = lookup.findVirtual(scheduledTaskClass, "isCancelled", MethodType.methodType(boolean.class));
        } catch (Exception e) {
            taskIsCancelled = null;
        }
        mhTaskIsCancelled = taskIsCancelled;
    }

    // ---- GlobalRegionScheduler 调用 ----

    void invokeGlobalRun(Plugin plugin, Runnable task) {
        try {
            Object scheduler = mhGetGlobalScheduler.invoke();
            Consumer<Object> consumer = t -> task.run();
            mhGlobalRun.invoke(scheduler, plugin, consumer);
        } catch (Throwable e) {
            throw new RuntimeException(I18nService.tr("[FoliaCompat] GlobalRegionScheduler.run 调用失败"), e);
        }
    }

    void invokeGlobalExecute(Plugin plugin, Runnable task) {
        if (mhGlobalExecute != null) {
            try {
                Object scheduler = mhGetGlobalScheduler.invoke();
                mhGlobalExecute.invoke(scheduler, plugin, task);
                return;
            } catch (Throwable e) {
                throw new RuntimeException(I18nService.tr("[FoliaCompat] GlobalRegionScheduler.execute 调用失败"), e);
            }
        }
        // 回退：execute 不可用时使用 run（Consumer 包装）
        invokeGlobalRun(plugin, task);
    }

    Object invokeGlobalRunDelayed(Plugin plugin, Runnable task, long delay) {
        try {
            Object scheduler = mhGetGlobalScheduler.invoke();
            Consumer<Object> consumer = t -> task.run();
            return mhGlobalRunDelayed.invoke(scheduler, plugin, consumer, delay);
        } catch (Throwable e) {
            throw new RuntimeException(I18nService.tr("[FoliaCompat] GlobalRegionScheduler.runDelayed 调用失败"), e);
        }
    }

    // ---- AsyncScheduler 调用 ----

    Object scheduleAsyncAtFixedRate(Plugin plugin, Runnable task, long delayTicks, long intervalTicks) {
        try {
            Object scheduler = mhGetAsyncScheduler.invoke();
            Consumer<Object> consumer = t -> task.run();
            return mhAsyncRunAtFixedRate.invoke(scheduler, plugin, consumer, delayTicks * TICK_TO_MS, intervalTicks * TICK_TO_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            throw new RuntimeException(I18nService.tr("[FoliaCompat] AsyncScheduler.runAtFixedRate 调用失败"), e);
        }
    }

    // ---- EntityScheduler 调用 ----

    /**
     * 在玩家实体所属的区域线程执行任务
     *
     * @param entity 目标实体（通常是 Player）
     * @param task   要执行的任务
     */
    void invokeEntityRun(org.bukkit.entity.Entity entity, Runnable task) {
        try {
            // entity.getScheduler()
            Object entityScheduler = mhGetEntityScheduler.invoke(entity);
            // scheduler.run(plugin, consumer, null) — Consumer<ScheduledTask>, retired 传 null
            Consumer<Object> consumer = t -> task.run();
            mhEntityRun.invoke(entityScheduler, KilacraftAI.getInstance(), consumer, (Runnable) null);
        } catch (Throwable e) {
            throw new RuntimeException(I18nService.tr("[FoliaCompat] EntityScheduler.run 调用失败"), e);
        }
    }

    // ---- ScheduledTask 操作 ----

    void cancelFoliaTask(Object task) {
        if (mhTaskCancel != null) {
            try {
                mhTaskCancel.invoke(task); // 返回值忽略（CancelledState 或 void）
            } catch (Throwable e) {
                PluginLoggerUtil.warn("Folia兼容", I18nService.tr("取消任务失败: {}", e.getMessage()), e);
            }
        }
    }

    boolean isFoliaTaskCancelled(Object task) {
        if (mhTaskIsCancelled != null) {
            try {
                return (boolean) mhTaskIsCancelled.invoke(task);
            } catch (Throwable e) {
                return true;
            }
        }
        return true;
    }
}
