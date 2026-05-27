package com.zm.kilacraftAI.common.util;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.skill.SkillResult;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Admin Skill 公共工具方法
 *
 * @author Zm_Mmm
 * @since 2026-05-18
 */
public final class AdminSkillUtil {

    private AdminSkillUtil() {
    }

    /**
     * 解析时间范围字符串为起始毫秒时间戳
     *
     * @param timeRange 时间范围字符串（支持: 1d/3d/7d/30d）
     * @return 起始时间戳（ms），-1 表示无效
     */
    public static long parseTimeRange(String timeRange) {
        try {
            int days = switch (timeRange.toLowerCase()) {
                case "1d" -> 1;
                case "3d" -> 3;
                case "7d" -> 7;
                case "30d" -> 30;
                default -> -1;
            };
            if (days < 0) return -1;
            return System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 解析时间范围字符串为 [afterTime, beforeTime] 区间
     *
     * @param timeRange 时间范围字符串（支持: 1d/3d/7d/30d）
     * @return 时间区间数组 [afterMs, nowMs]，null 表示无效
     */
    public static long[] parseTimeRangeFull(String timeRange) {
        try {
            int days = switch (timeRange.toLowerCase()) {
                case "1d" -> 1;
                case "3d" -> 3;
                case "7d" -> 7;
                case "30d" -> 30;
                default -> -1;
            };
            if (days < 0) return null;
            long now = System.currentTimeMillis();
            return new long[]{now - (long) days * 24 * 60 * 60 * 1000, now};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析数量限制字符串，限制范围 [1, 100]
     *
     * @param limitStr     限制字符串（可能为 null 或空）
     * @param defaultLimit 默认值
     * @return 解析后的限制值
     */
    public static int parseLimit(String limitStr, int defaultLimit) {
        if (limitStr == null || limitStr.isEmpty()) return defaultLimit;
        try {
            int val = Integer.parseInt(limitStr);
            return Math.max(1, Math.min(val, 100));
        } catch (NumberFormatException e) {
            return defaultLimit;
        }
    }

    /**
     * 在 IO 线程池中异步执行任务，自动处理 DB 初始化检查和异常
     *
     * @param task      要执行的任务
     * @param logPrefix 日志前缀
     * @return CompletableFuture
     */
    public static CompletableFuture<SkillResult> executeAsync(Callable<SkillResult> task, String logPrefix) {
        DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
        if (dbManager == null) {
            return SkillResult.failure("数据库未初始化").toFuture();
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                PluginLoggerUtil.error(logPrefix, I18nService.tr("执行失败: {}", e.getMessage()), e);
                return SkillResult.failure("执行失败", e);
            }
        }, FoliaCompat.getIOPool());
    }

    /**
     * 格式化文件大小为人类可读格式
     *
     * @param bytes 字节数
     * @return 格式化后的字符串（如 "50.2 MB"）
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 通过 Bukkit API 获取服务端平台信息
     *
     * @return 服务端描述（如 "Paper 1.21.11 (Leaf)"）
     */
    public static String getServerPlatform() {
        try {
            var server = KilacraftAI.getInstance().getServer();
            String bukkitVersion = server.getBukkitVersion();
            String mcVersion = bukkitVersion.contains("-") ? bukkitVersion.substring(0, bukkitVersion.indexOf('-')) : bukkitVersion;
            String name = server.getName();
            if (!"CraftBukkit".equals(name)) {
                return name + " " + mcVersion;
            }
            return "Bukkit " + mcVersion;
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
