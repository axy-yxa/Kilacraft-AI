package com.zm.kilacraftAI.db.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 技能执行审计 DAO
 *
 * <p>提供 {@code kca_skill_log} 表的写入和查询操作。</p>
 *
 * @author Zm_Mmm
 */
public class SkillLogDao {

    private final String tablePrefix;

    public SkillLogDao(String tablePrefix) {
        this.tablePrefix = tablePrefix;
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
    public void insert(Connection conn, String playerUuid, String skillName, String action, String entitiesJson, boolean success, String resultMessage, String triggerMessage, long executionMs, String source) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "skill_log " + "(player_uuid, skill_name, action, entities, success, result_message, " + "trigger_message, execution_ms, source, created_at, server_id) " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '')";

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
            ps.executeUpdate();
        }
    }

    /**
     * 清理过期审计记录
     *
     * @param conn       数据库连接
     * @param beforeTime 截止时间戳（ms）
     * @param batchSize  单次删除上限
     * @return 实际删除条数
     */
    public int cleanExpired(Connection conn, long beforeTime, int batchSize) throws SQLException {
        String sql = "DELETE FROM " + tablePrefix + "skill_log WHERE created_at < ? LIMIT ?";
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
}
