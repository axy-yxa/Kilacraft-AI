package com.zm.kilacraftAI.db.dao;

import com.zm.kilacraftAI.service.conversation.ConversationManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 对话历史 DAO
 *
 * <p>提供 {@code kca_conversation} 表的 CRUD 操作。</p>
 *
 * @author Zm_Mmm
 */
public class ConversationDao {

    private final String tablePrefix;

    public ConversationDao(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    /**
     * 批量插入对话记录
     *
     * @param conn     数据库连接
     * @param messages 待写入的消息列表，每个元素为 [playerUuid, role, content, personality, source, createdAt]
     */
    public void batchInsert(Connection conn, List<String[]> messages, String serverId) throws SQLException {
        if (messages == null || messages.isEmpty()) return;

        String sql = "INSERT INTO " + tablePrefix + "conversation " + "(player_uuid, role, content, personality, source, created_at, server_id) " + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] msg : messages) {
                ps.setString(1, msg[0]); // playerUuid
                ps.setString(2, msg[1]); // role
                ps.setString(3, msg[2]); // content
                ps.setString(4, msg[3]); // personality
                ps.setString(5, msg[4]); // source
                ps.setLong(6, Long.parseLong(msg[5])); // createdAt
                ps.setString(7, serverId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * 加载对话历史（按来源过滤）
     *
     * @param conn         数据库连接
     * @param playerUuid   玩家 UUID
     * @param personality  人格标识（普通AI为空串）
     * @param sourceFilter 来源过滤（IN 子句值，如 "'chat','command'"）
     * @param limit        最大加载条数
     * @return 消息列表（时间正序）
     */
    public Deque<ConversationManager.Message> loadHistory(Connection conn, String playerUuid, String personality, String sourceFilter, int limit) throws SQLException {
        // 使用 id 作为二级排序，确保相同时间戳的记录按插入顺序排列
        String sql = "SELECT role, content FROM " + tablePrefix + "conversation " + "WHERE player_uuid = ? AND personality = ? AND source IN (" + sourceFilter + ") " + "ORDER BY created_at DESC, id DESC LIMIT ?";

        Deque<ConversationManager.Message> messages = new ArrayDeque<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setString(2, personality);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<ConversationManager.Message> temp = new ArrayList<>();
                while (rs.next()) {
                    temp.add(new ConversationManager.Message(rs.getString("role"), rs.getString("content")));
                }
                // DESC → 反转为时间正序
                Collections.reverse(temp);
                for (ConversationManager.Message msg : temp) {
                    messages.addLast(msg);
                }
            }
        }
        return messages;
    }

    /**
     * 清理过期对话记录（兼容 H2 和 MySQL）
     *
     * <p>H2 不支持 {@code DELETE ... LIMIT}，MySQL 不允许 {@code IN} 子查询中使用 {@code LIMIT}。
     * 使用派生表包裹 LIMIT，H2 和 MySQL 均可执行。</p>
     */
    public int cleanExpired(Connection conn, long beforeTime, int batchSize) throws SQLException {
        String sql = "DELETE FROM " + tablePrefix + "conversation WHERE id IN (" + "SELECT id FROM (SELECT id FROM " + tablePrefix + "conversation WHERE created_at < ? LIMIT ?) AS tmp" + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, beforeTime);
            ps.setInt(2, batchSize);
            return ps.executeUpdate();
        }
    }

    public int countMessagesSince(Connection conn, String playerUuid, String sourceFilter, long afterTimestamp) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tablePrefix + "conversation " + "WHERE player_uuid = ? AND source IN (" + sourceFilter + ") AND created_at > ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setLong(2, afterTimestamp);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public List<ConversationManager.Message> loadMessagesForAnalysis(Connection conn, String playerUuid, String sourceFilter, long afterTimestamp) throws SQLException {
        String sql = "SELECT role, content FROM " + tablePrefix + "conversation " + "WHERE player_uuid = ? AND source IN (" + sourceFilter + ") AND created_at > ? " + "ORDER BY created_at ASC, id ASC";
        List<ConversationManager.Message> messages = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setLong(2, afterTimestamp);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(new ConversationManager.Message(rs.getString("role"), rs.getString("content")));
                }
            }
        }
        return messages;
    }

    /**
     * 统计玩家发言轮数（role='user'）。playerUuid=null 时统计全服。
     *
     * @param conn       数据库连接
     * @param playerUuid 玩家 UUID，null=全服
     * @param afterTime  起始时间戳（ms，不含）
     * @param beforeTime 截止时间戳（ms，含）
     * @return 返回发言轮数
     */
    public int countUserTurns(Connection conn, String playerUuid, long afterTime, long beforeTime) throws SQLException {
        boolean global = playerUuid == null;
        String sql = global ? "SELECT COUNT(*) FROM " + tablePrefix + "conversation WHERE role = ? AND created_at > ? AND created_at <= ?" : "SELECT COUNT(*) FROM " + tablePrefix + "conversation WHERE role = ? AND created_at > ? AND created_at <= ? AND player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "user");
            ps.setLong(2, afterTime);
            ps.setLong(3, beforeTime);
            if (!global) {
                ps.setString(4, playerUuid);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * 分页查询玩家对话历史（按时间倒序，含时间戳）。
     *
     * @param conn       数据库连接
     * @param playerUuid 玩家 UUID
     * @param offset     偏移量（页码×页大小）
     * @param limit      每页条数
     * @return 返回历史条目列表（时间倒序）
     */
    public List<HistoryEntry> queryHistoryPage(Connection conn, String playerUuid, int offset, int limit) throws SQLException {
        String sql = "SELECT role, content, created_at FROM " + tablePrefix + "conversation " + "WHERE player_uuid = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        List<HistoryEntry> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new HistoryEntry(rs.getString("role"), rs.getString("content"), rs.getLong("created_at")));
                }
            }
        }
        return results;
    }

    /**
     * 统计玩家对话历史总条数（用于分页总页数）。
     */
    public int countByPlayer(Connection conn, String playerUuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tablePrefix + "conversation WHERE player_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * 对话历史条目（queryHistoryPage 返回值）
     */
    public record HistoryEntry(String role, String content, long createdAt) {
    }
}
