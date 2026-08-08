package com.zm.kilacraftAI.db.service;

import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.ServerEventDao;
import com.zm.kilacraftAI.db.dao.SkillLogDao;
import com.zm.kilacraftAI.db.dao.WatermarkDao;
import com.zm.kilacraftAI.db.model.DatabaseConfig;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据保留清理服务
 *
 * <p>定期清理 {@code kca_server_event} 和 {@code kca_skill_log} 表中的过期记录，
 * 两张表各自有独立的保留天数配置（0=永久保留）。</p>
 *
 * <p>清理频率：每 6 小时（432000 ticks）执行一次，启动后延迟 2 分钟（2400 ticks）首次执行。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-07
 */
public class DataCleanupService {

    /**
     * 过期清理单次删除上限（避免长事务锁表）
     */
    private static final int CLEANUP_BATCH_SIZE = 10000;

    /**
     * 清理周期水位名称（分布式锁标识）
     *
     * <p>清理任务不分 server_id，由分布式锁保证全局只执行一次，替所有子服清理过期数据。</p>
     */
    private static final String CLEANUP_WATERMARK_NAME = "cleanup_events";

    /**
     * 清理周期时长（6 小时 = 21600000ms）
     */
    private static final long CLEANUP_INTERVAL_MS = 21600000L;

    private final DatabaseManager databaseManager;
    private volatile ServerEventDao serverEventDao;
    private volatile SkillLogDao skillLogDao;
    private volatile WatermarkDao watermarkDao;
    /**
     * 服务器事件保留天数（0=永久保留）
     */
    private volatile int eventRetentionDays;
    /**
     * 技能审计日志保留天数（0=永久保留）
     */
    private volatile int skillLogRetentionDays;

    public DataCleanupService(DatabaseManager databaseManager, int eventRetentionDays, int skillLogRetentionDays) {
        this.databaseManager = databaseManager;
        this.serverEventDao = new ServerEventDao(databaseManager.getTablePrefix());
        this.skillLogDao = new SkillLogDao(databaseManager.getTablePrefix());
        this.watermarkDao = new WatermarkDao(databaseManager.getTablePrefix());
        this.eventRetentionDays = eventRetentionDays;
        this.skillLogRetentionDays = skillLogRetentionDays;
    }

    /**
     * 热重载配置（由 /kila reload 触发）
     *
     * <p>更新可热重载的配置项：事件保留天数、审计日志保留天数、表前缀（DAO 重建）。</p>
     *
     * @param config 新的数据库配置
     */
    public void refreshConfig(DatabaseConfig config) {
        this.eventRetentionDays = config.getEventRetentionDays();
        this.skillLogRetentionDays = config.getSkillLogRetentionDays();
        String prefix = databaseManager.getTablePrefix();
        this.serverEventDao = new ServerEventDao(prefix);
        this.skillLogDao = new SkillLogDao(prefix);
        this.watermarkDao = new WatermarkDao(prefix);
        PluginLoggerUtil.info("数据库", "数据清理服务配置已刷新（事件保留: {}天, 审计保留: {}天）", eventRetentionDays, skillLogRetentionDays);
    }

    /**
     * 定时过期清理任务（由 TaskScheduler 调度）
     *
     * <p>分布式安全：使用 watermark 行锁（SELECT FOR UPDATE）保证群组服中只有一个子服执行清理。</p>
     *
     * @return 实际清理的记录条数
     */
    public int scheduledCleanup() {
        try (var conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 分布式锁：锁定水位行
                String lastCleanupStr = watermarkDao.getForUpdate(conn, CLEANUP_WATERMARK_NAME);
                long now = System.currentTimeMillis();

                // 检查是否在当前周期内已清理
                if (lastCleanupStr != null) {
                    try {
                        long lastTime = Long.parseLong(lastCleanupStr);
                        if (now - lastTime < CLEANUP_INTERVAL_MS) {
                            conn.rollback();
                            return 0; // 其他子服已清理
                        }
                    } catch (NumberFormatException ignored) {
                        // 水位值异常，视为需要重新清理
                    }
                }

                // 执行清理
                int totalDeleted = 0;
                if (eventRetentionDays > 0) {
                    long cutoffTime = now - (eventRetentionDays * 24L * 60 * 60 * 1000);
                    totalDeleted += cleanupTable(conn, serverEventDao::cleanExpired, "服务器事件", cutoffTime);
                }
                if (skillLogRetentionDays > 0) {
                    long cutoffTime = now - (skillLogRetentionDays * 24L * 60 * 60 * 1000);
                    totalDeleted += cleanupTable(conn, skillLogDao::cleanExpired, "技能审计", cutoffTime);
                }

                // 原子提交：清理 + 水位
                watermarkDao.put(conn, CLEANUP_WATERMARK_NAME, String.valueOf(now));
                conn.commit();

                if (totalDeleted > 0) {
                    PluginLoggerUtil.info("数据库", "已清理 {} 条过期事件数据", totalDeleted);
                }
                return totalDeleted;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            PluginLoggerUtil.warn("数据库", "事件数据清理失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 循环删除直到单次删除量小于批次大小
     *
     * @return 实际删除总条数
     */
    private int cleanupTable(Connection conn, CleanupDao dao, String tableName, long cutoffTime) throws SQLException {
        int totalDeleted = 0;
        int deleted;
        do {
            deleted = dao.cleanExpired(conn, cutoffTime, CLEANUP_BATCH_SIZE);
            totalDeleted += deleted;
        } while (deleted >= CLEANUP_BATCH_SIZE);

        if (totalDeleted > 0) {
            PluginLoggerUtil.debug("数据库", "清理 {} 表 {} 条过期记录", tableName, totalDeleted);
        }
        return totalDeleted;
    }

    /**
     * 是否需要清理（供 TaskScheduler 条件注册使用）
     *
     * @return true 表示至少有一张表需要清理
     */
    public boolean needsCleanup() {
        return eventRetentionDays > 0 || skillLogRetentionDays > 0;
    }

    /**
     * 清理 DAO 的统一接口（ServerEventDao 和 SkillLogDao 都有 cleanExpired 方法）
     */
    @FunctionalInterface
    private interface CleanupDao {
        int cleanExpired(Connection conn, long beforeTime, int batchSize) throws SQLException;
    }
}
