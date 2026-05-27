package com.zm.kilacraftAI.db.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 社交关系 DAO
 *
 * <p>提供 {@code kca_social_relation} 表的 CRUD 操作。</p>
 *
 * @author Zm_Mmm
 */
public class SocialRelationDao {

    private final String tablePrefix;

    /**
     * UPDATE social_relation SQL（交互增量）
     *
     * <p>在构造时拼接一次，避免每次调用重复构建。</p>
     */
    private final String updateInteractionSql;

    public SocialRelationDao(String tablePrefix) {
        this.tablePrefix = tablePrefix;
        this.updateInteractionSql = "UPDATE " + tablePrefix + "social_relation SET " + "interaction_count = interaction_count + 1, " + "last_interaction = ?, " + "strength = LEAST(strength + ? * SQRT(1.0 - strength), 0.99), " + "relation_type = ?, " + "updated_at = ? " + "WHERE player_uuid = ? AND target_uuid = ?";
    }

    /**
     * 增加两个玩家之间的交互计数（不存在则创建）
     *
     * <p>使用 UPDATE-first + INSERT-if-absent 模式。当两个线程并发为同一对玩家首次插入时，
     * UNIQUE(player_uuid, target_uuid) 约束会导致 INSERT 失败，此时 fallback 到 UPDATE。</p>
     *
     * @param conn         数据库连接
     * @param playerUuid   玩家 A UUID
     * @param targetUuid   玩家 B UUID
     * @param relationType 关系类型
     * @param weight       权重（开方递减增量）
     */
    public void incrementInteraction(Connection conn, UUID playerUuid, UUID targetUuid, String relationType, double weight) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(updateInteractionSql)) {
            bindUpdateParams(ps, playerUuid, targetUuid, relationType, weight);
            int updated = ps.executeUpdate();

            if (updated == 0) {
                // 不存在，尝试插入新记录
                try {
                    insert(conn, playerUuid, targetUuid, relationType, 1, Math.min(weight, 0.99));
                } catch (SQLException e) {
                    // 并发场景：另一个线程已先插入，UNIQUE 约束冲突
                    // fallback 到 UPDATE
                    if (isUniqueViolation(e)) {
                        try (PreparedStatement ps2 = conn.prepareStatement(updateInteractionSql)) {
                            bindUpdateParams(ps2, playerUuid, targetUuid, relationType, weight);
                            ps2.executeUpdate();
                        }
                    } else {
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * 绑定 UPDATE 交互增量 SQL 的参数（消除首次 UPDATE 和 fallback UPDATE 的重复代码）
     */
    private void bindUpdateParams(PreparedStatement ps, UUID playerUuid, UUID targetUuid, String relationType, double weight) throws SQLException {
        long now = System.currentTimeMillis();
        ps.setLong(1, now);
        ps.setDouble(2, weight);
        ps.setString(3, relationType);
        ps.setLong(4, now);
        ps.setString(5, playerUuid.toString());
        ps.setString(6, targetUuid.toString());
    }

    /**
     * 判断是否为 UNIQUE 约束冲突异常
     *
     * <p>H2 的 SQLState: 23505（唯一约束违反）
     * MySQL 的 SQLState: 23000（通用完整性约束违反）
     * 两者均属于 integrity constraint violation 系列。</p>
     */
    private boolean isUniqueViolation(SQLException e) {
        String sqlState = e.getSQLState();
        return sqlState != null && sqlState.startsWith("23");
    }

    /**
     * 查询玩家的社交关系（按关系强度降序）
     *
     * @param conn       数据库连接
     * @param playerUuid 玩家 UUID
     * @param limit      最大条数
     * @return 社交关系列表
     */
    public List<SocialRelation> loadRelations(Connection conn, UUID playerUuid, int limit) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "social_relation " + "WHERE player_uuid = ? ORDER BY strength DESC LIMIT ?";

        List<SocialRelation> relations = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    relations.add(mapRow(rs));
                }
            }
        }
        return relations;
    }

    /**
     * 查询玩家的社交关系（按关系强度降序，带最低强度过滤）
     *
     * @param conn        数据库连接
     * @param playerUuid  玩家 UUID
     * @param minStrength 最低关系强度
     * @param limit       最大条数
     * @return 社交关系列表
     */
    public List<SocialRelation> loadRelationsAbove(Connection conn, UUID playerUuid, double minStrength, int limit) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "social_relation " + "WHERE player_uuid = ? AND strength >= ? " + "ORDER BY strength DESC LIMIT ?";

        List<SocialRelation> relations = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setDouble(2, minStrength);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    relations.add(mapRow(rs));
                }
            }
        }
        return relations;
    }

    /**
     * 衰减所有关系的强度（每天调用一次）
     *
     * @param conn        数据库连接
     * @param decayFactor 衰减因子（如 0.95 表示衰减 5%）
     */
    public void decayStrength(Connection conn, double decayFactor) throws SQLException {
        String sql = "UPDATE " + tablePrefix + "social_relation SET " + "strength = strength * ?, updated_at = ? " + "WHERE strength > 0.01";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, decayFactor);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    /**
     * 删除强度极低的关系记录
     */
    public int cleanWeakRelations(Connection conn) throws SQLException {
        String sql = "DELETE FROM " + tablePrefix + "social_relation WHERE strength < 0.01";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    /**
     * 社交统计（social_insights Action）
     *
     * <p>按玩家聚合社交关系数、平均强度、最大强度、总交互次数。</p>
     *
     * @param conn  数据库连接
     * @param limit 最大条数
     * @return 社交统计列表（按 relation_count 降序）
     */
    public List<SocialStats> querySocialStats(Connection conn, int limit) throws SQLException {
        String sql = "SELECT player_uuid, " + "COUNT(*) AS relation_count, " + "AVG(strength) AS avg_strength, " + "MAX(strength) AS max_strength, " + "SUM(interaction_count) AS total_interactions " + "FROM " + tablePrefix + "social_relation " + "GROUP BY player_uuid " + "ORDER BY relation_count DESC LIMIT ?";

        List<SocialStats> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new SocialStats(UUID.fromString(rs.getString("player_uuid")), rs.getInt("relation_count"), rs.getDouble("avg_strength"), rs.getDouble("max_strength"), rs.getInt("total_interactions")));
                }
            }
        }
        return results;
    }

    /**
     * 查询孤立玩家（有画像但无社交关系的玩家）
     *
     * <p>使用 LEFT JOIN + IS NULL 模式（比 NOT IN 更友好于查询优化器）。</p>
     *
     * @param conn      数据库连接
     * @param afterTime 起始时间戳（ms）—— 仅返回 last_login > afterTime 的玩家
     * @param limit     最大条数
     * @return 孤立玩家列表（uuid, name）
     */
    public List<IsolatedPlayer> queryIsolatedPlayers(Connection conn, long afterTime, int limit) throws SQLException {
        String sql = "SELECT p.uuid, p.name " + "FROM " + tablePrefix + "player_profile p " + "LEFT JOIN " + tablePrefix + "social_relation sr ON p.uuid = sr.player_uuid " + "WHERE sr.player_uuid IS NULL AND p.last_login > ? " + "LIMIT ?";

        List<IsolatedPlayer> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, afterTime);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new IsolatedPlayer(UUID.fromString(rs.getString("uuid")), rs.getString("name")));
                }
            }
        }
        return results;
    }

    /**
     * 查询社交关系总数
     */
    public int countTotalRelations(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tablePrefix + "social_relation";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void insert(Connection conn, UUID playerUuid, UUID targetUuid, String relationType, int count, double strength) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "social_relation " + "(player_uuid, target_uuid, relation_type, interaction_count, " + "last_interaction, strength, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            ps.setString(1, playerUuid.toString());
            ps.setString(2, targetUuid.toString());
            ps.setString(3, relationType);
            ps.setInt(4, count);
            ps.setLong(5, now);
            ps.setDouble(6, strength);
            ps.setLong(7, now);
            ps.executeUpdate();
        }
    }

    private SocialRelation mapRow(ResultSet rs) throws SQLException {
        return new SocialRelation(UUID.fromString(rs.getString("player_uuid")), UUID.fromString(rs.getString("target_uuid")), rs.getString("relation_type"), rs.getInt("interaction_count"), rs.getLong("last_interaction"), rs.getDouble("strength"));
    }

    /**
     * 社交关系记录
     */
    public record SocialRelation(UUID playerUuid, UUID targetUuid, String relationType, int interactionCount,
                                 long lastInteraction, double strength) {
    }

    /**
     * 社交统计（querySocialStats 返回值）
     */
    public record SocialStats(UUID playerUuid, int relationCount, double avgStrength, double maxStrength,
                              int totalInteractions) {
    }

    /**
     * 孤立玩家（queryIsolatedPlayers 返回值）
     */
    public record IsolatedPlayer(UUID uuid, String name) {
    }
}
