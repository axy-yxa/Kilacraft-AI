package com.zm.kilacraftAI.common.util;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.PlayerProfileDao;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import com.zm.kilacraftAI.skills.framework.SkillSecurityFilter;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
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
     * 关系强度等级阈值
     */
    private static final double[] STRENGTH_THRESHOLDS = {0.1, 0.3, 0.5, 0.7};

    /**
     * 格式化时间戳为可读日期字符串
     *
     * @param epochMs 毫秒时间戳
     * @return 格式化后的日期字符串（如 "2026-05-18 14:30"）
     */
    public static String formatTimestamp(long epochMs) {
        if (epochMs <= 0) return "-";
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    /**
     * 将事件类型转换为可读名称
     */
    public static String translateEventType(String eventType) {
        return switch (eventType) {
            case "PLAYER_LOGIN" -> I18nService.tr("登录");
            case "PLAYER_LOGOUT" -> I18nService.tr("登出");
            case "PLAYER_FIRST_JOIN" -> I18nService.tr("首次加入");
            default -> eventType;
        };
    }

    /**
     * 将关系强度数值转换为等级描述
     *
     * @param strength 关系强度 (0.0~1.0)
     * @return 等级描述（如 "密友(0.85)"）
     */
    public static String formatStrengthLevel(double strength) {
        String[] i18nLevels = {I18nService.tr("陌生"), I18nService.tr("点头之交"), I18nService.tr("普通朋友"), I18nService.tr("好友"), I18nService.tr("密友")};
        String level;
        if (strength < STRENGTH_THRESHOLDS[0]) level = i18nLevels[0];
        else if (strength < STRENGTH_THRESHOLDS[1]) level = i18nLevels[1];
        else if (strength < STRENGTH_THRESHOLDS[2]) level = i18nLevels[2];
        else if (strength < STRENGTH_THRESHOLDS[3]) level = i18nLevels[3];
        else level = i18nLevels[4];
        return level + "(" + String.format("%.2f", strength) + ")";
    }

    /**
     * UUID→玩家名反查（在线缓存优先 + 库查询兜底）
     *
     * @param uuidStr UUID 字符串
     * @param conn    数据库连接
     * @param cache   本地缓存（可复用，避免重复查询）
     * @return 玩家名，未找到则返回 UUID 前8位
     */
    public static String resolvePlayerName(String uuidStr, Connection conn, Map<String, String> cache) throws SQLException {
        if (uuidStr == null || uuidStr.isEmpty()) return "-";
        String cached = cache.get(uuidStr);
        if (cached != null) return cached;

        // 尝试在线缓存
        try {
            UUID uuid = UUID.fromString(uuidStr);
            Map<UUID, String> onlineCache = SkillSecurityFilter.getOnlineUuidToName();
            String onlineName = onlineCache.get(uuid);
            if (onlineName != null) {
                cache.put(uuidStr, onlineName);
                return onlineName;
            }
        } catch (Exception ignored) {
        }

        // 库查询
        DatabaseManager dbManager = KilacraftAI.getInstance().getDatabaseManager();
        PlayerProfileDao profileDao = new PlayerProfileDao(dbManager.getTablePrefix());
        try {
            UUID uuid = UUID.fromString(uuidStr);
            var profile = profileDao.loadByUuid(conn, uuid);
            String name = profile != null ? profile.getName() : uuidStr.substring(0, 8) + "...";
            cache.put(uuidStr, name);
            return name;
        } catch (Exception e) {
            String fallback = uuidStr.length() >= 8 ? uuidStr.substring(0, 8) + "..." : uuidStr;
            cache.put(uuidStr, fallback);
            return fallback;
        }
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
