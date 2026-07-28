package com.zm.kilacraftAI.db;

import com.zm.kilacraftAI.common.enums.DatabaseTypeEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;

import java.sql.*;

/**
 * Schema 版本管理器
 *
 * <p>负责同一数据库内的表结构演进（不是跨库数据迁移）。</p>
 * <ul>
 *   <li>在 DB 中创建 {@code kca_schema_version} 表，记录当前 schema 版本号</li>
 *   <li>每次 initialize() 时，对比 DB 版本与代码期望版本</li>
 *   <li>如果 DB 版本 &lt; 期望版本 → 依次执行增量 DDL</li>
 *   <li>所有 DDL 使用 IF NOT EXISTS / DEFAULT 值，确保幂等安全</li>
 * </ul>
 *
 * @author Zm_Mmm
 */
public class SchemaManager {

    /**
     * 当前代码期望的 Schema 版本号
     */
    private static final int CURRENT_VERSION = 2;

    private final DatabaseProvider provider;
    private final String tablePrefix;

    /**
     * 当前数据库类型（影响 DDL 语法选择）
     */
    private final DatabaseTypeEnum databaseTypeEnum;

    public SchemaManager(DatabaseProvider provider, String tablePrefix, DatabaseTypeEnum databaseTypeEnum) {
        this.provider = provider;
        this.tablePrefix = tablePrefix;
        this.databaseTypeEnum = databaseTypeEnum;
    }

    /**
     * 初始化 Schema：确保版本表存在 + 按需执行增量迁移
     */
    public void initialize() throws SQLException {
        try (Connection conn = provider.getConnection()) {
            createVersionTable(conn);

            int currentVersion = getCurrentVersion(conn);

            PluginLoggerUtil.info("数据库", "Schema 版本: 当前={}, 期望={}", currentVersion, CURRENT_VERSION);

            for (int v = currentVersion + 1; v <= CURRENT_VERSION; v++) {
                migrateToVersion(conn, v);
                updateVersion(conn, v);
                PluginLoggerUtil.info("数据库", "Schema 已迁移到版本 {}", v);
            }
        }
    }

    private void createVersionTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "schema_version (" + "version INT NOT NULL," + "updated_at BIGINT NOT NULL" + ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private int getCurrentVersion(Connection conn) throws SQLException {
        String sql = "SELECT MAX(version) FROM " + tablePrefix + "schema_version";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                int v = rs.getInt(1);
                return rs.wasNull() ? 0 : v;
            }
        }
        return 0;
    }

    private void updateVersion(Connection conn, int version) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "schema_version (version, updated_at) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    /**
     * 按版本号执行增量 DDL 迁移
     */
    private void migrateToVersion(Connection conn, int version) throws SQLException {
        switch (version) {
            case 1 -> migrateV1(conn);
            case 2 -> migrateV2(conn);
            default -> PluginLoggerUtil.warn("数据库", "未知的 Schema 版本: {}", version);
        }
    }

    /**
     * 创建索引（兼容 H2 和 MySQL）
     *
     * <p>H2 支持 {@code CREATE INDEX IF NOT EXISTS}，但 MySQL 不支持。
     * MySQL 模式下通过查询 INFORMATION_SCHEMA 判断索引是否存在。</p>
     */
    private void createIndex(Connection conn, String indexName, String tableSuffix, String columns) throws SQLException {
        if (databaseTypeEnum == DatabaseTypeEnum.H2) {
            // H2 支持 IF NOT EXISTS
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tablePrefix + tableSuffix + " (" + columns + ")");
        } else {
            // MySQL: 通过 INFORMATION_SCHEMA 检查索引是否存在
            String tableName = tablePrefix + tableSuffix;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?")) {
                ps.setString(1, tableName);
                ps.setString(2, indexName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) return; // 索引已存在
                }
            }
            conn.createStatement().execute("CREATE INDEX " + indexName + " ON " + tableName + " (" + columns + ")");
        }
    }

    /**
     * 版本 1：创建所有基础表（含列备注）
     *
     * <p>COMMENT 语法兼容 H2（MySQL 模式）和 MySQL。</p>
     */
    private void migrateV1(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {

            // ── 玩家画像表 ──────────────────────────────────────────
            stmt.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_profile (" + "uuid VARCHAR(36) PRIMARY KEY COMMENT '玩家UUID 主键 / Player UUID primary key'," + "name VARCHAR(16) COMMENT '玩家名称 / Player name'," + "first_login BIGINT NOT NULL DEFAULT 0 COMMENT '首次登录时间戳ms / First login timestamp ms'," + "last_login BIGINT NOT NULL DEFAULT 0 COMMENT '最近登录时间戳ms / Last login timestamp ms'," + "last_logout BIGINT NOT NULL DEFAULT 0 COMMENT '最近登出时间戳ms / Last logout timestamp ms'," + "login_count INT NOT NULL DEFAULT 0 COMMENT '累计登录次数 / Total login count'," + "total_playtime_ms BIGINT NOT NULL DEFAULT 0 COMMENT '累计在线时长ms / Total playtime ms'," + "last_world VARCHAR(64) DEFAULT '' COMMENT '最近登出所在世界 / Last world on logout'," + "last_x DOUBLE DEFAULT 0 COMMENT '最近登出X坐标 / Last X coordinate on logout'," + "last_y DOUBLE DEFAULT 0 COMMENT '最近登出Y坐标 / Last Y coordinate on logout'," + "last_z DOUBLE DEFAULT 0 COMMENT '最近登出Z坐标 / Last Z coordinate on logout'," + "last_greeting_time BIGINT NOT NULL DEFAULT 0 COMMENT '最近AI问候时间戳ms / Last greeting timestamp ms'," + "profile_data TEXT COMMENT 'LLM画像分析JSON / LLM profile analysis JSON'," + "profile_analyzed_at BIGINT NOT NULL DEFAULT 0 COMMENT '最近分析完成时间戳ms / Last analysis completion timestamp ms'," + "updated_at BIGINT NOT NULL DEFAULT 0 COMMENT '最后更新时间戳ms / Last update timestamp ms'" + ")");

            // ── 对话历史表 ──────────────────────────────────────────
            stmt.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "conversation (" + "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键 / Auto-increment ID'," + "player_uuid VARCHAR(36) NOT NULL COMMENT '玩家UUID / Player UUID'," + "role VARCHAR(16) NOT NULL COMMENT '消息角色 user/assistant/system / Message role'," + "content TEXT NOT NULL COMMENT '消息内容 / Message content'," + "personality VARCHAR(32) NOT NULL DEFAULT '' COMMENT '人格标识（普通AI为空） / Personality ID (empty for default AI)'," + "source VARCHAR(16) NOT NULL DEFAULT 'chat' COMMENT '消息来源 chat/command/plugin等 / Message source'," + "created_at BIGINT NOT NULL COMMENT '消息创建时间戳ms / Message creation timestamp ms'," + "server_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '服务器ID（群组服区分） / Server ID (for network)'" + ")");

            createIndex(conn, "idx_conv_player_pers_time", "conversation", "player_uuid, personality, created_at DESC");
            createIndex(conn, "idx_conv_player_src_time", "conversation", "player_uuid, source, created_at DESC");
            createIndex(conn, "idx_conv_created_at", "conversation", "created_at");

            // ── 服务器事件表 ────────────────────────────────────────
            stmt.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "server_event (" + "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键 / Auto-increment ID'," + "event_type VARCHAR(32) NOT NULL COMMENT '事件类型 join/quit/death等 / Event type'," + "player_uuid VARCHAR(36) COMMENT '触发玩家UUID / Triggering player UUID'," + "target_uuid VARCHAR(36) COMMENT '目标玩家UUID（可选） / Target player UUID (optional)'," + "data TEXT COMMENT '事件附加数据JSON / Event metadata JSON'," + "created_at BIGINT NOT NULL COMMENT '事件时间戳ms / Event timestamp ms'," + "server_id VARCHAR(64) DEFAULT '' COMMENT '服务器ID / Server ID'" + ")");

            createIndex(conn, "idx_event_type", "server_event", "event_type");
            createIndex(conn, "idx_event_player", "server_event", "player_uuid");
            createIndex(conn, "idx_event_created", "server_event", "created_at");

            // ── 社交关系表 ──────────────────────────────────────────
            stmt.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "social_relation (" + "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键 / Auto-increment ID'," + "player_uuid VARCHAR(36) NOT NULL COMMENT '玩家UUID（关系持有方） / Player UUID (owner)'," + "target_uuid VARCHAR(36) NOT NULL COMMENT '目标UUID（关系对象） / Target UUID'," + "relation_type VARCHAR(32) NOT NULL COMMENT '关系类型 friend/frequent_chat等 / Relation type'," + "interaction_count INT NOT NULL DEFAULT 0 COMMENT '交互次数 / Interaction count'," + "last_interaction BIGINT NOT NULL DEFAULT 0 COMMENT '最近交互时间戳ms / Last interaction timestamp ms'," + "strength DOUBLE NOT NULL DEFAULT 0.0 COMMENT '关系强度（每日衰减） / Relation strength (daily decay)'," + "updated_at BIGINT NOT NULL DEFAULT 0 COMMENT '最后更新时间戳ms / Last update timestamp ms'," + "UNIQUE (player_uuid, target_uuid)" + ")");

            createIndex(conn, "idx_social_player", "social_relation", "player_uuid");
            createIndex(conn, "idx_social_target", "social_relation", "target_uuid");

            // ── 技能执行审计表 ──────────────────────────────────────
            stmt.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "skill_log (" + "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键 / Auto-increment ID'," + "player_uuid VARCHAR(36) NOT NULL COMMENT '触发玩家UUID / Triggering player UUID'," + "skill_name VARCHAR(32) NOT NULL COMMENT '技能名称 / Skill name'," + "action VARCHAR(64) NOT NULL DEFAULT '' COMMENT '执行动作 / Executed action'," + "entities TEXT COMMENT '涉及实体JSON / Involved entities JSON'," + "success BOOLEAN NOT NULL COMMENT '是否成功 / Success flag'," + "result_message TEXT COMMENT '执行结果消息 / Result message'," + "trigger_message TEXT COMMENT '触发原文 / Trigger original message'," + "execution_ms BIGINT COMMENT '执行耗时ms / Execution time ms'," + "source VARCHAR(16) NOT NULL DEFAULT 'agent' COMMENT '触发源 agent/manual / Source agent/manual'," + "created_at BIGINT NOT NULL COMMENT '创建时间戳ms / Creation timestamp ms'," + "server_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '服务器ID / Server ID'" + ")");

            createIndex(conn, "idx_skill_player_time", "skill_log", "player_uuid, created_at DESC");
            createIndex(conn, "idx_skill_name_time", "skill_log", "skill_name, created_at DESC");
            createIndex(conn, "idx_skill_created", "skill_log", "created_at");

            // ── 水位标记表（分布式定时任务用） ──────────────────────────────
            stmt.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "watermark (" + "name VARCHAR(64) PRIMARY KEY COMMENT '水位名称（如 decay_date, extract_time）'," + "`value` VARCHAR(128) NOT NULL COMMENT '水位值（日期字符串或时间戳）'" + ")");
        }

        PluginLoggerUtil.info("数据库", "Schema v1 建表完成（6张表）");
    }

    /**
     * 版本 2：为隔离表的 server_id 列添加索引（群组服按子服过滤性能优化）
     *
     * <p>仅影响 conversation、server_event、skill_log 三张隔离表。
     * player_profile 和 social_relation 为共享表，不含 server_id 列。</p>
     */
    private void migrateV2(Connection conn) throws SQLException {
        createIndex(conn, "idx_conv_server", "conversation", "server_id");
        createIndex(conn, "idx_event_server", "server_event", "server_id");
        createIndex(conn, "idx_skill_server", "skill_log", "server_id");

        // ── 画像快照表（增量分析版本追踪） ──────────────────────────
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "profile_snapshot (" + "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键 / Auto-increment ID'," + "player_uuid VARCHAR(36) NOT NULL COMMENT '玩家UUID / Player UUID'," + "snapshot_data TEXT NOT NULL COMMENT '画像快照JSON / Profile snapshot JSON'," + "message_count INT NOT NULL DEFAULT 0 COMMENT '本次分析消息数 / Messages analyzed'," + "window_start BIGINT NOT NULL DEFAULT 0 COMMENT '分析窗口起始时间ms / Window start ms'," + "window_end BIGINT NOT NULL DEFAULT 0 COMMENT '分析窗口截止时间ms / Window end ms'," + "version INT NOT NULL DEFAULT 1 COMMENT '画像版本号 / Profile version'," + "analyzed_at BIGINT NOT NULL COMMENT '分析时间戳ms / Analysis timestamp ms'" + ")");
        }
        createIndex(conn, "idx_snapshot_player_time", "profile_snapshot", "player_uuid, analyzed_at DESC");

        PluginLoggerUtil.info("数据库", "Schema v2 群组服索引已创建（3个 server_id 索引）+ 画像快照表");
    }
}
