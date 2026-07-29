package com.zm.kilacraftAI.db.dao;

import com.zm.kilacraftAI.common.enums.ServerEventTypeEnum;
import com.zm.kilacraftAI.model.event.ServerEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 服务器事件 DAO
 *
 * <p>提供 {@code kca_server_event} 表的 CRUD 操作。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-07
 */
public class ServerEventDao {

    private final String tablePrefix;
    /**
     * 缓存的排除列表：排除所有非 PLAYER 类别事件（用于社交/统计查询）
     */
    private final String playerFacingExcludeFilter;
    /**
     * 缓存的排除列表：仅排除 LIFECYCLE 类别事件（用于玩家上下文事件查询，保留市场事件）
     */
    private final String lifecycleExcludeFilter;

    public ServerEventDao(String tablePrefix) {
        this.tablePrefix = tablePrefix;
        // 排除所有非 PLAYER 类别事件（用于社交/统计查询：loadEventsForPlayers, countGlobalEventsBetween）
        this.playerFacingExcludeFilter = buildCategoryExcludeFilter(ServerEventTypeEnum.Category.PLAYER);
        // 仅排除 LIFECYCLE 类别事件（用于玩家上下文事件查询：loadEventsAfter, loadEventsBetween）
        this.lifecycleExcludeFilter = buildLifecycleExcludeFilter();
    }

    /**
     * 动态构建排除列表：排除所有 {@code Category != retainCategory} 的事件类型。
     *
     * <p>新增事件类型时无需修改此方法或任何 SQL。</p>
     *
     * @param retainCategory 要保留的事件类别，其他类别全部排除
     * @return NOT IN 子句值
     */
    private static String buildCategoryExcludeFilter(ServerEventTypeEnum.Category retainCategory) {
        return Arrays.stream(ServerEventTypeEnum.values()).filter(e -> e.getCategory() != retainCategory).map(e -> "'" + e.name() + "'").collect(Collectors.joining(","));
    }

    /**
     * 构建生命周期事件排除列表：仅排除 {@code Category == LIFECYCLE} 的事件类型。
     *
     * <p>与 {@link #buildCategoryExcludeFilter} 语义相反：
     * buildCategoryExcludeFilter 是排除「非指定类别」的所有事件（保留指定类别），
     * 此方法是排除「指定类别」的事件（保留其他所有类别）。</p>
     */
    private static String buildLifecycleExcludeFilter() {
        return Arrays.stream(ServerEventTypeEnum.values()).filter(e -> e.getCategory() == ServerEventTypeEnum.Category.LIFECYCLE).map(e -> "'" + e.name() + "'").collect(Collectors.joining(","));
    }

    /**
     * 插入事件记录
     */
    public void insert(Connection conn, ServerEvent event, String serverId) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "server_event " + "(event_type, player_uuid, target_uuid, data, created_at, server_id) " + "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.getEventType().name());
            ps.setString(2, event.getPlayerUuid() != null ? event.getPlayerUuid().toString() : null);
            ps.setString(3, event.getTargetUuid() != null ? event.getTargetUuid().toString() : null);
            ps.setString(4, event.getData());
            ps.setLong(5, event.getCreatedAt());
            ps.setString(6, serverId);
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
        String sql = "SELECT * FROM " + tablePrefix + "server_event " + "WHERE player_uuid = ? AND created_at > ? " + "AND event_type NOT IN (" + lifecycleExcludeFilter + ") " + "ORDER BY created_at DESC LIMIT ?";

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
            sql = "SELECT * FROM " + tablePrefix + "server_event " + "WHERE player_uuid = ? AND created_at > ? AND created_at < ? " + "AND event_type NOT IN (" + lifecycleExcludeFilter + ") " + "ORDER BY created_at DESC LIMIT ?";
        } else {
            sql = "SELECT * FROM " + tablePrefix + "server_event " + "WHERE player_uuid = ? AND created_at < ? " + "AND event_type NOT IN (" + lifecycleExcludeFilter + ") " + "ORDER BY created_at DESC LIMIT ?";
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
        String sql = "SELECT se.*, pp.name AS player_name FROM " + tablePrefix + "server_event se " + "LEFT JOIN " + tablePrefix + "player_profile pp ON se.player_uuid = pp.uuid " + "WHERE se.player_uuid IN (" + placeholders + ") " + "AND se.created_at > ? " + "AND se.event_type NOT IN (" + playerFacingExcludeFilter + ") " + "ORDER BY se.created_at DESC LIMIT ?";

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
        String sql = "SELECT COUNT(*) FROM " + tablePrefix + "server_event " + "WHERE created_at > ? AND created_at <= ? " + "AND event_type NOT IN (" + playerFacingExcludeFilter + ")";
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
     * 查询指定时间范围内的 HEALTH_ALERT 告警事件（query 模式使用）
     *
     * <p>返回数据量极小（每天 1~10 条），Java 层完成 data JSON 解析和过滤。</p>
     *
     * @param conn      数据库连接
     * @param afterTime 起始时间戳（ms，不含）
     * @param limit     最大条数
     * @return 告警事件列表（时间倒序，最新的在前）
     */
    public List<ServerEvent> loadHealthAlerts(Connection conn, long afterTime, int limit) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "server_event " + "WHERE event_type = 'HEALTH_ALERT' AND player_uuid IS NULL AND created_at > ? " + "ORDER BY created_at DESC LIMIT ?";

        List<ServerEvent> events = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, afterTime);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRow(rs));
                }
            }
        }
        return events;
    }

    /**
     * 查询该管理员尚未被问候通知的健康告警事件
     *
     * <p>与 {@link #loadHealthAlerts} 的区别：在离线期间时间过滤基础上，再用该管理员已有的
     * HEALTH_ALERT_NOTIFIED 标记（player_uuid = ?）过滤掉已通知过的告警。每个管理员每条告警
     * 通过问候最多收到一次。</p>
     *
     * @param conn       数据库连接
     * @param playerUuid 管理员 UUID
     * @param afterTime  起始时间戳（ms，不含）
     * @param limit      最大条数
     * @return 待通知的告警事件列表（时间倒序，最新的在前）
     */
    public List<ServerEvent> loadUnnotifiedHealthAlerts(Connection conn, UUID playerUuid, long afterTime, int limit) throws SQLException {
        String sql = "SELECT a.* FROM " + tablePrefix + "server_event a " + "WHERE a.event_type = 'HEALTH_ALERT' AND a.player_uuid IS NULL AND a.created_at > ? " + "AND NOT EXISTS (SELECT 1 FROM " + tablePrefix + "server_event n " + "WHERE n.event_type = 'HEALTH_ALERT_NOTIFIED' AND n.player_uuid = ? AND n.data = a.data) " + "ORDER BY a.created_at DESC LIMIT ?";

        List<ServerEvent> events = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, afterTime);
            ps.setString(2, playerUuid.toString());
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
     * 写入健康告警"已通知"标记，记录某管理员已被问候通知某条告警
     *
     * <p>data 字段与对应的 HEALTH_ALERT 事件保持一致，便于后续查询精确匹配。</p>
     *
     * @param conn       数据库连接
     * @param playerUuid 管理员 UUID
     * @param eventData  告警事件的 data JSON（与 HEALTH_ALERT 的 data 相同）
     * @param serverId   子服 ID
     */
    public void markHealthAlertNotified(Connection conn, UUID playerUuid, String eventData, String serverId) throws SQLException {
        ServerEvent event = ServerEvent.of(ServerEventTypeEnum.HEALTH_ALERT_NOTIFIED, playerUuid, eventData);
        insert(conn, event, serverId);
    }

    /**
     * 查询该管理员尚未被通知的新版本提醒事件
     *
     * <p>查全局存在的 UPDATE_AVAILABLE 事件（player_uuid IS NULL），再用该管理员已有的
     * UPDATE_NOTIFIED 标记（player_uuid = ?）过滤掉已通知过的版本。每个管理员每版本
     * 只会返回一次。</p>
     *
     * @param conn       数据库连接
     * @param playerUuid 管理员 UUID
     * @param limit      最大条数
     * @return 待通知的更新提醒事件列表（时间倒序，最新的在前）
     */
    public List<ServerEvent> loadUnnotifiedUpdateReminders(Connection conn, UUID playerUuid, int limit) throws SQLException {
        String sql = "SELECT a.* FROM " + tablePrefix + "server_event a " + "WHERE a.event_type = 'UPDATE_AVAILABLE' AND a.player_uuid IS NULL " + "AND NOT EXISTS (SELECT 1 FROM " + tablePrefix + "server_event n " + "WHERE n.event_type = 'UPDATE_NOTIFIED' AND n.player_uuid = ? AND n.data = a.data) " + "ORDER BY a.created_at DESC LIMIT ?";

        List<ServerEvent> events = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRow(rs));
                }
            }
        }
        return events;
    }

    /**
     * 写入"已通知"标记，记录某管理员已被通知某版本
     *
     * <p>data 字段与对应的 UPDATE_AVAILABLE 事件保持一致，便于后续查询精确匹配。</p>
     *
     * @param conn       数据库连接
     * @param playerUuid 管理员 UUID
     * @param eventData  版本信息 JSON（与 UPDATE_AVAILABLE 的 data 相同）
     * @param serverId   子服 ID
     */
    public void markUpdateNotified(Connection conn, UUID playerUuid, String eventData, String serverId) throws SQLException {
        ServerEvent event = ServerEvent.of(ServerEventTypeEnum.UPDATE_NOTIFIED, playerUuid, eventData);
        insert(conn, event, serverId);
    }

    /**
     * 去重查询：是否已存在指定 tag 的更新提醒事件
     *
     * <p>用于在写入前避免重复记录同一个发布版本。UPDATE_AVAILABLE 事件量极小
     * （一版本一条），用 data LIKE 匹配即可，无需额外索引。</p>
     *
     * @param conn    数据库连接
     * @param tagName 版本标签（如 v2.1.3）
     * @return true 表示该 tag 的提醒事件已存在
     */
    public boolean existsUpdateReminder(Connection conn, String tagName) throws SQLException {
        String sql = "SELECT 1 FROM " + tablePrefix + "server_event " + "WHERE event_type = 'UPDATE_AVAILABLE' AND player_uuid IS NULL AND data LIKE ? LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%\"tag\":\"" + tagName + "\"%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 按时间桶统计指定事件类型的数量（在线趋势/新玩家流入）
     *
     * <p>返回每个时间桶内各事件类型的计数，用于 PlayerAnalysisSkill 的 online_trend / new_players Action。</p>
     *
     * @param conn       数据库连接
     * @param eventTypes 事件类型列表（如 PLAYER_LOGIN, PLAYER_LOGOUT, PLAYER_FIRST_JOIN）
     * @param bucketMs   时间桶大小（ms）：hour=3600000, day=86400000
     * @param afterTime  起始时间戳（ms，不含）
     * @param beforeTime 截止时间戳（ms，含）
     * @return 列表，每条记录包含 time_bucket, event_type, cnt
     */
    public List<TimeBucketCount> countEventsByTypeBetween(Connection conn, List<String> eventTypes, long bucketMs, long afterTime, long beforeTime) throws SQLException {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return Collections.emptyList();
        }

        StringJoiner placeholders = new StringJoiner(",");
        for (int i = 0; i < eventTypes.size(); i++) placeholders.add("?");

        String sql = "SELECT FLOOR(created_at / ?) * ? AS time_bucket, event_type, COUNT(*) AS cnt " + "FROM " + tablePrefix + "server_event " + "WHERE event_type IN (" + placeholders + ") " + "AND created_at > ? AND created_at <= ? " + "GROUP BY time_bucket, event_type " + "ORDER BY time_bucket";

        List<TimeBucketCount> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setLong(idx++, bucketMs);
            ps.setLong(idx++, bucketMs);
            for (String et : eventTypes) ps.setString(idx++, et);
            ps.setLong(idx++, afterTime);
            ps.setLong(idx, beforeTime);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new TimeBucketCount(rs.getLong("time_bucket"), rs.getString("event_type"), rs.getInt("cnt")));
                }
            }
        }
        return results;
    }

    /**
     * 清理过期事件记录（兼容 H2 和 MySQL）
     *
     * <p>使用派生表包裹 LIMIT，H2 和 MySQL 均可执行。</p>
     */
    public int cleanExpired(Connection conn, long beforeTime, int batchSize) throws SQLException {
        String sql = "DELETE FROM " + tablePrefix + "server_event WHERE id IN (" + "SELECT id FROM (SELECT id FROM " + tablePrefix + "server_event WHERE created_at < ? LIMIT ?) AS tmp" + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, beforeTime);
            ps.setInt(2, batchSize);
            return ps.executeUpdate();
        }
    }

    private ServerEvent mapRow(ResultSet rs) throws SQLException {
        String playerUuidStr = rs.getString("player_uuid");
        String targetUuidStr = rs.getString("target_uuid");

        return ServerEvent.builder().eventType(ServerEventTypeEnum.valueOf(rs.getString("event_type"))).playerUuid(playerUuidStr != null ? UUID.fromString(playerUuidStr) : null).targetUuid(targetUuidStr != null ? UUID.fromString(targetUuidStr) : null).data(rs.getString("data")).createdAt(rs.getLong("created_at")).build();
    }

    /**
     * 映射结果行（含 JOIN 带来的 player_name 列）
     */
    private ServerEvent mapRowWithName(ResultSet rs) throws SQLException {
        String playerUuidStr = rs.getString("player_uuid");
        String targetUuidStr = rs.getString("target_uuid");
        String playerName = rs.getString("player_name");

        return ServerEvent.builder().eventType(ServerEventTypeEnum.valueOf(rs.getString("event_type"))).playerUuid(playerUuidStr != null ? UUID.fromString(playerUuidStr) : null).targetUuid(targetUuidStr != null ? UUID.fromString(targetUuidStr) : null).data(rs.getString("data")).createdAt(rs.getLong("created_at")).playerName(playerName).build();
    }

    /**
     * 时间桶事件计数（countEventsByTypeBetween 返回值）
     */
    public record TimeBucketCount(long timeBucket, String eventType, int count) {
    }
}
