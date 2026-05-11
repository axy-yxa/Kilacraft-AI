package com.zm.kilacraftAI.profile;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.SocialRelationDao;
import com.zm.kilacraftAI.db.dao.SocialRelationDao.SocialRelation;
import com.zm.kilacraftAI.db.dao.WatermarkDao;
import com.zm.kilacraftAI.util.PluginLogger;
import lombok.Getter;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 社交关系管理器
 *
 * <p>管理玩家之间的社交关系（交互计数、关系强度衰减）。</p>
 *
 * @author Zm_Mmm
 */
public class SocialGraph {

    /**
     * 交互类型 → 强度增量映射
     *
     * <p>作为 SocialGraph 的内部枚举，封装业务语义。
     * DAO 层只接收 double weight 参数，不感知业务含义。</p>
     */
    @Getter
    public enum InteractionWeight {
        PRIVATE_CHAT(0.01, "私聊"), TPA_INTERACTION(0.02, "传送"), SKILL_INTERACTION(0.005, "Skill交互");

        private final double weight;
        private final String description;

        InteractionWeight(double weight, String description) {
            this.weight = weight;
            this.description = description;
        }

    }

    private final KilacraftAI plugin;
    private final DatabaseManager databaseManager;
    private final SocialRelationDao socialDao;
    private volatile WatermarkDao watermarkDao;

    /**
     * 每日衰减因子（衰减 5%）
     */
    private static final double DAILY_DECAY_FACTOR = 0.95;

    /**
     * 上次执行衰减的日期（内存缓存，单服内快速跳过）
     *
     * <p>分布式安全由 DB 水位标记（kca_watermark 表）保证，
     * 此变量仅用于避免已确认衰减的当天重复提交 IO 任务。</p>
     */
    private volatile LocalDate lastDecayDate;

    /**
     * 当前服务器标识（群组服区分，影响水位名称后缀）
     */
    private volatile String serverId;

    /**
     * 社交关系是否共享（共享时水位不带 server_id 后缀）
     */
    private volatile boolean socialRelationShared;

    public SocialGraph(KilacraftAI plugin, DatabaseManager databaseManager, String serverId, boolean socialRelationShared) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.socialDao = new SocialRelationDao(databaseManager.getTablePrefix());
        this.watermarkDao = new WatermarkDao(databaseManager.getTablePrefix());
        this.serverId = serverId != null ? serverId : "";
        this.socialRelationShared = socialRelationShared;
    }

    /**
     * 热重载配置
     *
     * @param serverId              新的 server_id
     * @param socialRelationShared  社交关系是否共享
     */
    public void refreshConfig(String serverId, boolean socialRelationShared) {
        this.serverId = serverId != null ? serverId : "";
        this.socialRelationShared = socialRelationShared;
        String prefix = databaseManager.getTablePrefix();
        this.watermarkDao = new WatermarkDao(prefix);
    }

    /**
     * 记录交互（使用权重感知的增量递增）
     *
     * @param playerUuid   玩家 A UUID
     * @param targetUuid   玩家 B UUID
     * @param relationType 关系类型
     * @param weight       权重
     */
    public void recordInteraction(UUID playerUuid, UUID targetUuid, String relationType, double weight) {
        PluginLogger.debug("社交关系", "提交交互记录: {} ↔ {}, type={}, weight={}", playerUuid, targetUuid, relationType, weight);
        FoliaCompat.getIOPool().submit(() -> {
            try (var conn = databaseManager.getConnection()) {
                // 双向记录
                socialDao.incrementInteraction(conn, playerUuid, targetUuid, relationType, weight);
                socialDao.incrementInteraction(conn, targetUuid, playerUuid, relationType, weight);
                PluginLogger.debug("社交关系", "交互记录成功: {} ↔ {} ({})", playerUuid, targetUuid, relationType);
            } catch (Exception e) {
                PluginLogger.error("数据库", "记录社交交互失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 记录交互（使用默认权重 — 按交互类型枚举查找）
     */
    public void recordInteraction(UUID playerUuid, UUID targetUuid, String relationType) {
        double weight = resolveWeight(relationType);
        recordInteraction(playerUuid, targetUuid, relationType, weight);
    }

    /**
     * 根据关系类型解析权重
     */
    private double resolveWeight(String relationType) {
        try {
            return InteractionWeight.valueOf(relationType.toUpperCase()).getWeight();
        } catch (IllegalArgumentException e) {
            return 0.01; // 默认增量
        }
    }

    /**
     * 每日衰减社交关系强度
     *
     * @return 实际清理的弱关系条数（0 表示今日已衰减或跳过）
     */
    public int performDailyDecay() {
        LocalDate today = LocalDate.now();

        // 内存快速检查（单服内防重复）
        if (today.equals(lastDecayDate)) {
            return 0;
        }

        String watermarkName = buildDecayWatermarkName();

        // 直接在 TaskScheduler 的异步线程上执行（runAsyncTimer 回调已在异步线程）
        try (var conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 分布式锁：锁定水位行
                String dbDate = getWatermarkForUpdate(conn, watermarkName);
                if (today.toString().equals(dbDate)) {
                    // DB 中已记录今天的衰减，跳过（其他子服已执行）
                    lastDecayDate = today;
                    conn.rollback(); // 释放行锁
                    return 0;
                }

                // 执行衰减
                socialDao.decayStrength(conn, DAILY_DECAY_FACTOR);
                int cleaned = socialDao.cleanWeakRelations(conn);
                if (cleaned > 0) {
                    PluginLogger.info("数据库", "社交关系衰减完成，清理 {} 条弱关系", cleaned);
                }

                // 原子提交：衰减+水位同时生效
                putWatermark(conn, watermarkName, today.toString());
                conn.commit();
                lastDecayDate = today;
                return Math.max(1, cleaned);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            PluginLogger.error("数据库", "社交关系衰减失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 构建 decay_date 水位名称
     *
     * <p>共享模式下不带 server_id 后缀（全局只衰减一次），
     * 隔离模式下带 server_id 后缀（各服独立衰减）。</p>
     */
    private String buildDecayWatermarkName() {
        if (socialRelationShared) {
            return "decay_date";
        }
        return serverId.isEmpty() ? "decay_date" : "decay_date:" + serverId;
    }

    /**
     * 锁定并读取水位标记值（委托 WatermarkDao）
     *
     * <p>FOR UPDATE 在显式事务中获取的行锁会持续持有到 commit/rollback，
     * 实现跨子服的分布式互斥。</p>
     */
    private String getWatermarkForUpdate(Connection conn, String name) throws SQLException {
        return watermarkDao.getForUpdate(conn, name);
    }

    /**
     * 写入或更新水位标记（委托 WatermarkDao）
     */
    private void putWatermark(Connection conn, String name, String value) throws SQLException {
        watermarkDao.put(conn, name, value);
    }
}
