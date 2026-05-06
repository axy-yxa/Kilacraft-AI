package com.zm.kilacraftAI.db;

import com.zm.kilacraftAI.db.dao.ServerEventDao;
import com.zm.kilacraftAI.db.dao.SkillLogDao;
import com.zm.kilacraftAI.util.PluginLogger;

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
 */
public class DataCleanupService {

    /**
     * 过期清理单次删除上限（避免长事务锁表）
     */
    private static final int CLEANUP_BATCH_SIZE = 10000;

    private final DatabaseManager databaseManager;
    private final ServerEventDao serverEventDao;
    private final SkillLogDao skillLogDao;
    /**
     * 服务器事件保留天数（0=永久保留）
     */
    private final int eventRetentionDays;
    /**
     * 技能审计日志保留天数（0=永久保留）
     */
    private final int skillLogRetentionDays;

    public DataCleanupService(DatabaseManager databaseManager, int eventRetentionDays, int skillLogRetentionDays) {
        this.databaseManager = databaseManager;
        this.serverEventDao = new ServerEventDao(databaseManager.getTablePrefix());
        this.skillLogDao = new SkillLogDao(databaseManager.getTablePrefix());
        this.eventRetentionDays = eventRetentionDays;
        this.skillLogRetentionDays = skillLogRetentionDays;
    }

    /**
     * 定时过期清理任务（由 TaskScheduler 调度）
     *
     * @return 实际清理的记录条数
     */
    public int scheduledCleanup() {
        int totalDeleted = 0;

        try (Connection conn = databaseManager.getConnection()) {
            // 清理 server_event
            if (eventRetentionDays > 0) {
                long cutoffTime = System.currentTimeMillis() - (eventRetentionDays * 24L * 60 * 60 * 1000);
                totalDeleted += cleanupTable(conn, serverEventDao::cleanExpired, "服务器事件", cutoffTime);
            }

            // 清理 skill_log
            if (skillLogRetentionDays > 0) {
                long cutoffTime = System.currentTimeMillis() - (skillLogRetentionDays * 24L * 60 * 60 * 1000);
                totalDeleted += cleanupTable(conn, skillLogDao::cleanExpired, "技能审计", cutoffTime);
            }
        } catch (SQLException e) {
            PluginLogger.warn("数据库", "事件数据清理失败: {}", e.getMessage());
            return 0;
        }

        if (totalDeleted > 0) {
            PluginLogger.info("数据库", "已清理 {} 条过期事件数据", totalDeleted);
        }
        return totalDeleted;
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
            PluginLogger.debug("数据库", "清理 {} 表 {} 条过期记录", tableName, totalDeleted);
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
