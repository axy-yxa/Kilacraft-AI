package com.zm.kilacraftAI.db.dao;

import com.zm.kilacraftAI.event.ServerEvent;
import com.zm.kilacraftAI.event.ServerEventType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * 服务器事件 DAO
 *
 * <p>提供 {@code kca_server_event} 表的 CRUD 操作。</p>
 *
 * @author Zm_Mmm
 */
public class ServerEventDao {

    private final String tablePrefix;

    public ServerEventDao(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    /**
     * 插入事件记录
     */
    public void insert(Connection conn, ServerEvent event) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "server_event " + "(event_type, player_uuid, target_uuid, data, created_at, server_id) " + "VALUES (?, ?, ?, ?, ?, '')";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.getEventType().name());
            ps.setString(2, event.getPlayerUuid() != null ? event.getPlayerUuid().toString() : null);
            ps.setString(3, event.getTargetUuid() != null ? event.getTargetUuid().toString() : null);
            ps.setString(4, event.getData());
            ps.setLong(5, event.getCreatedAt());
            ps.executeUpdate();
        }
    }

    /**
     * 查询指定玩家在某个时间点之后的离线事件（排除登录/登出等生命周期事件）
     *
     * @param conn       数据库连接
     * @param playerUuid 玩家 UUID
     * @param afterTime  起始时间戳（ms）
     * @param limit      最大条数
     * @return 事件列表（时间倒序，最新的在前）
     */
    public List<ServerEvent> loadEventsAfter(Connection conn, UUID playerUuid, long afterTime, int limit) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "server_event " + "WHERE player_uuid = ? AND created_at > ? " + "AND event_type NOT IN ('PLAYER_LOGIN', 'PLAYER_LOGOUT', 'PLAYER_FIRST_JOIN') " + "ORDER BY created_at DESC LIMIT ?";

        List<ServerEvent> events = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, afterTime);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRow(rs));
                }
            }
        }
        return events;
    }

    /**
     * 查询指定玩家在某个时间点之前的事件（用于"上次游玩亮点"摘要）
     *
     * @param conn       数据库连接
     * @param playerUuid 玩家 UUID
     * @param beforeTime 截止时间戳（ms，不含此时间点）
     * @param limit      最大条数
     * @return 事件列表（时间倒序，最新的在前）
     */
    /**
     * 查询指定玩家在两个时间点之间的事件（用于上次游玩亮点）
     *
     * @param conn       数据库连接
     * @param playerUuid 玩家 UUID
     * @param afterTime  起始时间戳（ms，不含此时间点），0 表示不限
     * @param beforeTime 截止时间戳（ms，不含此时间点）
     * @param limit      最大条数
     * @return 事件列表（时间倒序，最新的在前）
     */
    public List<ServerEvent> loadEventsBetween(Connection conn, UUID playerUuid, long afterTime, long beforeTime, int limit) throws SQLException {
        String sql;
        if (afterTime > 0) {
            sql = "SELECT * FROM " + tablePrefix + "server_event "
                    + "WHERE player_uuid = ? AND created_at > ? AND created_at < ? "
                    + "AND event_type NOT IN ('PLAYER_LOGIN', 'PLAYER_LOGOUT', 'PLAYER_FIRST_JOIN') "
                    + "ORDER BY created_at DESC LIMIT ?";
        } else {
            sql = "SELECT * FROM " + tablePrefix + "server_event "
                    + "WHERE player_uuid = ? AND created_at < ? "
                    + "AND event_type NOT IN ('PLAYER_LOGIN', 'PLAYER_LOGOUT', 'PLAYER_FIRST_JOIN') "
                    + "ORDER BY created_at DESC LIMIT ?";
        }

        List<ServerEvent> events = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            if (afterTime > 0) {
                ps.setLong(2, afterTime);
                ps.setLong(3, beforeTime);
                ps.setInt(4, limit);
            } else {
                ps.setLong(2, beforeTime);
                ps.setInt(3, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRow(rs));
                }
            }
        }
        return events;
    }

    /**
     * 查询多个玩家在某个时间点之后的事件（用于好友动态），JOIN player_profile 获取玩家名
     *
     * @param conn       数据库连接
     * @param playerUuids 目标玩家 UUID 列表（好友）
     * @param afterTime  起始时间戳（ms）
     * @param limit      最大条数
     * @return 事件列表（时间倒序，最新的在前，playerName 已填充）
     */
    public List<ServerEvent> loadEventsForPlayers(Connection conn, List<UUID> playerUuids, long afterTime, int limit) throws SQLException {
        if (playerUuids == null || playerUuids.isEmpty()) {
            return Collections.emptyList();
        }

        // 动态构建 IN 子句占位符
        StringJoiner placeholders = new StringJoiner(",");
        for (int i = 0; i < playerUuids.size(); i++) {
            placeholders.add("?");
        }

        // LEFT JOIN player_profile 获取玩家名，排除市场事件（隐私）和生命周期事件
        String sql = "SELECT se.*, pp.name AS player_name FROM " + tablePrefix + "server_event se " + "LEFT JOIN " + tablePrefix + "player_profile pp ON se.player_uuid = pp.uuid " + "WHERE se.player_uuid IN (" + placeholders + ") " + "AND se.created_at > ? " + "AND se.event_type NOT IN ('PLAYER_LOGIN', 'PLAYER_LOGOUT', 'PLAYER_FIRST_JOIN', " + "'MARKET_ITEM_SOLD', 'MARKET_ITEM_LISTED', 'MARKET_MONEY_RECEIVED') " + "ORDER BY se.created_at DESC LIMIT ?";

        List<ServerEvent> events = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (UUID uuid : playerUuids) {
                ps.setString(idx++, uuid.toString());
            }
            ps.setLong(idx++, afterTime);
            ps.setInt(idx, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRowWithName(rs));
                }
            }
        }
        return events;
    }

    /**
     * 清理过期事件记录
     *
     * @param conn       数据库连接
     * @param beforeTime 截止时间戳（ms）
     * @param batchSize  单次删除上限
     * @return 实际删除条数
     */
    public int cleanExpired(Connection conn, long beforeTime, int batchSize) throws SQLException {
        String sql = "DELETE FROM " + tablePrefix + "server_event WHERE created_at < ? LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, beforeTime);
            ps.setInt(2, batchSize);
            return ps.executeUpdate();
        }
    }

    private ServerEvent mapRow(ResultSet rs) throws SQLException {
        String playerUuidStr = rs.getString("player_uuid");
        String targetUuidStr = rs.getString("target_uuid");

        return ServerEvent.builder().eventType(ServerEventType.valueOf(rs.getString("event_type"))).playerUuid(playerUuidStr != null ? UUID.fromString(playerUuidStr) : null).targetUuid(targetUuidStr != null ? UUID.fromString(targetUuidStr) : null).data(rs.getString("data")).createdAt(rs.getLong("created_at")).build();
    }

    /**
     * 映射结果行（含 JOIN 带来的 player_name 列）
     */
    private ServerEvent mapRowWithName(ResultSet rs) throws SQLException {
        String playerUuidStr = rs.getString("player_uuid");
        String targetUuidStr = rs.getString("target_uuid");
        String playerName = rs.getString("player_name");

        return ServerEvent.builder().eventType(ServerEventType.valueOf(rs.getString("event_type"))).playerUuid(playerUuidStr != null ? UUID.fromString(playerUuidStr) : null).targetUuid(targetUuidStr != null ? UUID.fromString(targetUuidStr) : null).data(rs.getString("data")).createdAt(rs.getLong("created_at")).playerName(playerName).build();
    }
}
