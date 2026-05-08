package com.zm.kilacraftAI.db.dao;

import com.zm.kilacraftAI.event.ServerEvent;
import com.zm.kilacraftAI.event.ServerEventType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

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
            sql = "SELECT * FROM " + tablePrefix + "server_event " + "WHERE player_uuid = ? AND created_at > ? AND created_at < ? " + "AND event_type NOT IN ('PLAYER_LOGIN', 'PLAYER_LOGOUT', 'PLAYER_FIRST_JOIN') " + "ORDER BY created_at DESC LIMIT ?";
        } else {
            sql = "SELECT * FROM " + tablePrefix + "server_event " + "WHERE player_uuid = ? AND created_at < ? " + "AND event_type NOT IN ('PLAYER_LOGIN', 'PLAYER_LOGOUT', 'PLAYER_FIRST_JOIN') " + "ORDER BY created_at DESC LIMIT ?";
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
     * 查询多个玩家在某个时间点之后的事件（用于好友动态）
     *
     * @param conn        数据库连接
     * @param playerUuids 目标玩家 UUID 列表（好友）
     * @param afterTime   起始时间戳（ms）
     * @param limit       最大条数
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

        // 排除市场事件（隐私）和生命周期事件
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
     * 查询玩家上次会话时长（上次 LOGOUT - 上上次 LOGIN）
     *
     * @param conn       数据库连接
     * @param playerUuid 玩家 UUID
     * @return 会话时长 ms，0 表示无法计算
     */
    public long loadLastSessionDuration(Connection conn, UUID playerUuid) throws SQLException {
        Long lastLogout = queryLastEventTime(conn, playerUuid, "PLAYER_LOGOUT", 0);
        if (lastLogout == null || lastLogout <= 0) return 0;
        Long prevLogin = queryLastEventTime(conn, playerUuid, "PLAYER_LOGIN", lastLogout);
        if (prevLogin == null || prevLogin <= 0) return 0;
        long duration = lastLogout - prevLogin;
        return duration > 0 ? duration : 0;
    }

    private Long queryLastEventTime(Connection conn, UUID playerUuid, String eventType, long beforeTime) throws SQLException {
        String sql;
        if (beforeTime > 0) {
            sql = "SELECT created_at FROM " + tablePrefix + "server_event " + "WHERE player_uuid = ? AND event_type = ? AND created_at < ? " + "ORDER BY created_at DESC LIMIT 1";
        } else {
            sql = "SELECT created_at FROM " + tablePrefix + "server_event " + "WHERE player_uuid = ? AND event_type = ? " + "ORDER BY created_at DESC LIMIT 1";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, eventType);
            if (beforeTime > 0) ps.setLong(3, beforeTime);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /**
     * 统计指定时间窗口内全服事件数（排除生命周期和市场事件）
     *
     * @param conn       数据库连接
     * @param afterTime  起始时间戳（ms，不含）
     * @param beforeTime 截止时间戳（ms，含）
     * @return 事件总数
     */
    public int countGlobalEventsBetween(Connection conn, long afterTime, long beforeTime) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tablePrefix + "server_event " + "WHERE created_at > ? AND created_at <= ? " + "AND event_type NOT IN ('PLAYER_LOGIN','PLAYER_LOGOUT','PLAYER_FIRST_JOIN'," + "'MARKET_ITEM_SOLD','MARKET_ITEM_LISTED','MARKET_MONEY_RECEIVED')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, afterTime);
            ps.setLong(2, beforeTime);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * 统计多个好友在指定时间之后的登录次数
     *
     * @param conn        数据库连接
     * @param friendUuids 好友 UUID 列表
     * @param afterTime   起始时间戳（ms）
     * @return Map<玩家名, 登录次数>（按次数降序，LinkedHashMap 保序）
     */
    public Map<String, Integer> countFriendLogins(Connection conn, List<UUID> friendUuids, long afterTime) throws SQLException {
        if (friendUuids == null || friendUuids.isEmpty()) return Collections.emptyMap();

        StringJoiner placeholders = new StringJoiner(",");
        for (int i = 0; i < friendUuids.size(); i++) placeholders.add("?");

        String sql = "SELECT pp.name, COUNT(*) as cnt FROM " + tablePrefix + "server_event se " + "JOIN " + tablePrefix + "player_profile pp ON se.player_uuid = pp.uuid " + "WHERE se.player_uuid IN (" + placeholders + ") " + "AND se.event_type = 'PLAYER_LOGIN' AND se.created_at > ? " + "GROUP BY se.player_uuid, pp.name ORDER BY cnt DESC";

        Map<String, Integer> result = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (UUID uuid : friendUuids) ps.setString(idx++, uuid.toString());
            ps.setLong(idx, afterTime);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("name"), rs.getInt("cnt"));
                }
            }
        }
        return result;
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
