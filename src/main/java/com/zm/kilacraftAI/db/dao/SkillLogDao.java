package com.zm.kilacraftAI.db.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 技能执行审计 DAO
 *
 * <p>提供 {@code kca_skill_log} 表的写入和查询操作。</p>
 *
 * @author Zm_Mmm
 */
public class SkillLogDao {

    /**
     * 表名（构造时初始化，避免运行时拼接）
     */
    private final String tableName;

    public SkillLogDao(String tablePrefix) {
        this.tableName = tablePrefix + "skill_log";
    }

    /**
     * 插入技能执行审计记录
     *
     * @param conn           数据库连接
     * @param playerUuid     触发玩家 UUID
     * @param skillName      技能名称
     * @param action         动作名称
     * @param entitiesJson   实体参数 JSON
     * @param success        是否成功
     * @param resultMessage  执行结果消息（截断前500字符）
     * @param triggerMessage 触发消息
     * @param executionMs    执行耗时（ms）
     * @param source         触发来源
     */
    public void insert(Connection conn, String playerUuid, String skillName, String action, String entitiesJson, boolean success, String resultMessage, String triggerMessage, long executionMs, String source, String serverId) throws SQLException {
        String sql = "INSERT INTO " + tableName + " (player_uuid, skill_name, action, entities, success, result_message, " + "trigger_message, execution_ms, source, created_at, server_id) " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setString(2, skillName);
            ps.setString(3, action);
            ps.setString(4, entitiesJson);
            ps.setBoolean(5, success);
            ps.setString(6, truncate(resultMessage, 500));
            ps.setString(7, truncate(triggerMessage, 500));
            ps.setLong(8, executionMs);
            ps.setString(9, source);
            ps.setLong(10, System.currentTimeMillis());
            ps.setString(11, serverId);
            ps.executeUpdate();
        }
    }

    /**
     * 清理过期审计记录（兼容 H2 和 MySQL）
     *
     * <p>使用派生表包裹 LIMIT，H2 和 MySQL 均可执行。</p>
     */
    public int cleanExpired(Connection conn, long beforeTime, int batchSize) throws SQLException {
        String sql = "DELETE FROM " + tableName + " WHERE id IN (" + "SELECT id FROM (SELECT id FROM " + tableName + " WHERE created_at < ? LIMIT ?) AS tmp" + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, beforeTime);
            ps.setInt(2, batchSize);
            return ps.executeUpdate();
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /**
     * 动态设置 PreparedStatement 参数（支持 Long / Boolean / String / Integer）
     *
     * @param ps     PreparedStatement
     * @param params 参数列表
     */
    private void setParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param instanceof Long l) ps.setLong(i + 1, l);
            else if (param instanceof Boolean b) ps.setBoolean(i + 1, b);
            else if (param instanceof String s) ps.setString(i + 1, s);
            else if (param instanceof Integer n) ps.setInt(i + 1, n);
            else
                throw new SQLException("Unsupported parameter type: " + (param != null ? param.getClass().getName() : "null"));
        }
    }

    /**
     * 按条件查询技能执行日志
     *
     * @param conn          数据库连接
     * @param afterTime     起始时间戳（ms，不含）
     * @param beforeTime    截止时间戳（ms，含）
     * @param playerUuid    玩家 UUID 过滤（null 表示不过滤）
     * @param skillName     技能名过滤（null 表示不过滤）
     * @param successFilter 成功/失败过滤（null 表示不过滤，true=仅成功，false=仅失败）
     * @param limit         最大条数
     * @return 技能执行日志列表
     */
    public List<SkillLogEntry> queryLogs(Connection conn, long afterTime, long beforeTime, String playerUuid, String skillName, Boolean successFilter, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE created_at > ? AND created_at <= ?");
        List<Object> params = new ArrayList<>();
        params.add(afterTime);
        params.add(beforeTime);

        if (playerUuid != null) {
            sql.append(" AND player_uuid = ?");
            params.add(playerUuid);
        }
        if (skillName != null) {
            sql.append(" AND skill_name = ?");
            params.add(skillName);
        }
        if (successFilter != null) {
            sql.append(" AND success = ?");
            params.add(successFilter);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(limit);

        List<SkillLogEntry> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            setParameters(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapLogEntry(rs));
                }
            }
        }
        return results;
    }

    /**
     * 技能使用统计（按 skill_name + action 分组）
     *
     * @param conn       数据库连接
     * @param afterTime  起始时间戳（ms，不含）
     * @param beforeTime 截止时间戳（ms，含）
     * @param limit      最大条数
     * @return 技能使用统计列表
     */
    public List<SkillUsageStat> queryUsageStats(Connection conn, long afterTime, long beforeTime, int limit) throws SQLException {
        String sql = "SELECT skill_name, action, " + "COUNT(*) AS total_count, " + "SUM(CASE WHEN success = true THEN 1 ELSE 0 END) AS success_count, " + "SUM(CASE WHEN success = false THEN 1 ELSE 0 END) AS fail_count, " + "AVG(execution_ms) AS avg_duration_ms " + "FROM " + tableName + " " + "WHERE created_at > ? AND created_at <= ? " + "GROUP BY skill_name, action " + "ORDER BY total_count DESC LIMIT ?";

        List<SkillUsageStat> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, afterTime);
            ps.setLong(2, beforeTime);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new SkillUsageStat(rs.getString("skill_name"), rs.getString("action"), rs.getInt("total_count"), rs.getInt("success_count"), rs.getInt("fail_count"), rs.getDouble("avg_duration_ms")));
                }
            }
        }
        return results;
    }

    /**
     * 指定玩家的技能调用分解（按 skill_name + action 分组）。
     *
     * @param conn       数据库连接
     * @param playerUuid 玩家 UUID
     * @param afterTime  起始时间戳（ms，不含）
     * @param beforeTime 截止时间戳（ms，含）
     * @param limit      最大条数
     * @return 返回该玩家的技能使用统计列表
     */
    public List<SkillUsageStat> queryUsageByPlayer(Connection conn, String playerUuid, long afterTime, long beforeTime, int limit) throws SQLException {
        String sql = "SELECT skill_name, action, " + "COUNT(*) AS total_count, " + "SUM(CASE WHEN success = true THEN 1 ELSE 0 END) AS success_count, " + "SUM(CASE WHEN success = false THEN 1 ELSE 0 END) AS fail_count, " + "AVG(execution_ms) AS avg_duration_ms " + "FROM " + tableName + " " + "WHERE player_uuid = ? AND created_at > ? AND created_at <= ? " + "GROUP BY skill_name, action " + "ORDER BY total_count DESC LIMIT ?";

        List<SkillUsageStat> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setLong(2, afterTime);
            ps.setLong(3, beforeTime);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new SkillUsageStat(rs.getString("skill_name"), rs.getString("action"), rs.getInt("total_count"), rs.getInt("success_count"), rs.getInt("fail_count"), rs.getDouble("avg_duration_ms")));
                }
            }
        }
        return results;
    }

    /**
     * 全服活跃玩家 Top N（按技能调用次数）。仅返回 UUID，玩家名由调用方反查。
     *
     * @param conn       数据库连接
     * @param afterTime  起始时间戳（ms，不含）
     * @param beforeTime 截止时间戳（ms，含）
     * @param limit      最大条数
     * @return 返回玩家调用排行（仅 UUID）
     */
    public List<PlayerUsageStat> queryTopPlayers(Connection conn, long afterTime, long beforeTime, int limit) throws SQLException {
        String sql = "SELECT player_uuid, " + "COUNT(*) AS total_count, " + "SUM(CASE WHEN success = true THEN 1 ELSE 0 END) AS success_count " + "FROM " + tableName + " " + "WHERE created_at > ? AND created_at <= ? " + "GROUP BY player_uuid " + "ORDER BY total_count DESC LIMIT ?";

        List<PlayerUsageStat> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, afterTime);
            ps.setLong(2, beforeTime);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new PlayerUsageStat(rs.getString("player_uuid"), rs.getInt("total_count"), rs.getInt("success_count")));
                }
            }
        }
        return results;
    }

    /**
     * 失败执行日志（error_logs Action）
     *
     * @param conn       数据库连接
     * @param afterTime  起始时间戳（ms，不含）
     * @param beforeTime 截止时间戳（ms，含）
     * @param skillName  技能名过滤（null 表示不过滤）
     * @param limit      最大条数
     * @return 失败执行日志列表
     */
    public List<SkillLogEntry> queryErrorLogs(Connection conn, long afterTime, long beforeTime, String skillName, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE created_at > ? AND created_at <= ? AND success = false");
        List<Object> params = new ArrayList<>();
        params.add(afterTime);
        params.add(beforeTime);

        if (skillName != null) {
            sql.append(" AND skill_name = ?");
            params.add(skillName);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(limit);

        List<SkillLogEntry> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            setParameters(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapLogEntry(rs));
                }
            }
        }
        return results;
    }

    private SkillLogEntry mapLogEntry(ResultSet rs) throws SQLException {
        return new SkillLogEntry(rs.getString("player_uuid"), rs.getString("skill_name"), rs.getString("action"), rs.getString("entities"), rs.getBoolean("success"), rs.getString("result_message"), rs.getString("trigger_message"), rs.getLong("execution_ms"), rs.getString("source"), rs.getLong("created_at"));
    }

    /**
     * 技能执行日志条目
     */
    public record SkillLogEntry(String playerUuid, String skillName, String action, String entities, boolean success,
                                String resultMessage, String triggerMessage, long executionMs, String source,
                                long createdAt) {
    }

    /**
     * 技能使用统计
     */
    public record SkillUsageStat(String skillName, String action, int totalCount, int successCount, int failCount,
                                 double avgDurationMs) {
    }

    /**
     * 玩家调用统计（queryTopPlayers 返回值，仅含 UUID，名字由调用方反查）
     */
    public record PlayerUsageStat(String playerUuid, int totalCount, int successCount) {
    }
}
