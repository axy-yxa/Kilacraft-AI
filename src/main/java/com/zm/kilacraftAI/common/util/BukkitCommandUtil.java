package com.zm.kilacraftAI.common.util;

import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import org.bukkit.command.CommandSender;

import java.util.concurrent.CompletableFuture;

/**
 * Bukkit 命令调度工具类
 * <p>
 * 统一封装 dispatchCommand 的线程安全问题。
 * 委托 {@link FoliaCompat} 处理 Spigot/Paper 和 Folia 的调度差异。
 * </p>
 *
 * <p>提供三种调用方式：
 * <ul>
 *   <li>{@link #dispatchSync} - 同步阻塞，等待命令执行完成并返回结果</li>
 *   <li>{@link #dispatchAsync} - 异步非阻塞，调度到主线程执行后立即返回</li>
 *   <li>{@link #dispatchOnMainThread} - 最轻量，仅调度执行不关心结果（适合 fire-and-forget 场景）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-12
 */
public class BukkitCommandUtil {

    /**
     * 主线程任务执行超时时间（秒）
     */
    private static final long TIMEOUT_SECONDS = 10;

    private BukkitCommandUtil() {
    }

    /**
     * 在主线程同步执行命令（阻塞当前线程）
     * <p>
     * 委托 {@link FoliaCompat#dispatchCommandSync} 处理 Folia/Spigot 差异。
     * </p>
     *
     * @param sender  命令发送者
     * @param command 要执行的命令（不含前导 /）
     * @return 命令执行结果（true=命令被识别并执行）
     * @throws RuntimeException 命令执行失败或超时时抛出
     */
    public static boolean dispatchSync(CommandSender sender, String command) {
        return FoliaCompat.dispatchCommandSync(sender, command, TIMEOUT_SECONDS);
    }

    /**
     * 在主线程异步执行命令（非阻塞，返回 CompletableFuture）
     * <p>
     * 委托 {@link FoliaCompat#dispatchCommandAsync} 处理调度差异。
     * </p>
     *
     * @param sender  命令发送者
     * @param command 要执行的命令（不含前导 /）
     * @return CompletableFuture，命令执行完成后 complete(true/false)，异常时 completeExceptionally
     */
    public static CompletableFuture<Boolean> dispatchAsync(CommandSender sender, String command) {
        return FoliaCompat.dispatchCommandAsync(sender, command);
    }

    /**
     * 在主线程调度执行命令（fire-and-forget，不关心结果）
     * <p>
     * 最轻量的调用方式。委托 {@link FoliaCompat#dispatchCommand} 处理调度差异。
     * </p>
     *
     * @param sender  命令发送者
     * @param command 要执行的命令（不含前导 /）
     */
    public static void dispatchOnMainThread(CommandSender sender, String command) {
        FoliaCompat.dispatchCommand(sender, command);
    }
}
