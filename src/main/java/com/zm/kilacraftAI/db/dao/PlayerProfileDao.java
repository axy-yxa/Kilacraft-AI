package com.zm.kilacraftAI.db.dao;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zm.kilacraftAI.profile.PlayerProfile;
import com.zm.kilacraftAI.util.PluginLogger;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家画像 DAO
 *
 * <p>提供 {@code kca_player_profile} 表的 CRUD 操作。</p>
 *
 * @author Zm_Mmm
 */
public class PlayerProfileDao {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private final String tablePrefix;

    public PlayerProfileDao(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    /**
     * 加载玩家画像，不存在则创建并返回默认画像
     *
     * @param conn 数据库连接
     * @param uuid 玩家 UUID
     * @param name 玩家名
     * @return 玩家画像
     */
    public PlayerProfile loadOrCreate(Connection conn, UUID uuid, String name) throws SQLException {
        PlayerProfile profile = loadByUuid(conn, uuid);
        if (profile != null) {
            return profile;
        }

        // 不存在，创建默认画像
        long now = System.currentTimeMillis();
        PlayerProfile newProfile = PlayerProfile.builder().uuid(uuid).name(name).firstLogin(now).lastLogin(now).lastLogout(0).loginCount(0).totalPlaytimeMs(0).lastWorld("").lastGreetingTime(0).build();

        insert(conn, newProfile);
        PluginLogger.debug("数据库", "创建新玩家画像: {} ({})", name, uuid);
        return newProfile;
    }

    /**
     * 按 UUID 加载玩家画像
     *
     * @param conn 数据库连接
     * @param uuid 玩家 UUID
     * @return 玩家画像，不存在返回 null
     */
    public PlayerProfile loadByUuid(Connection conn, UUID uuid) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "player_profile WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * 插入新玩家画像
     */
    public void insert(Connection conn, PlayerProfile profile) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "player_profile " + "(uuid, name, first_login, last_login, last_logout, login_count, " + "total_playtime_ms, last_world, " + "last_x, last_y, last_z, " + "last_greeting_time, profile_analyzed_at, profile_data, updated_at) " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            ps.setString(1, profile.getUuid().toString());
            ps.setString(2, profile.getName());
            ps.setLong(3, profile.getFirstLogin());
            ps.setLong(4, profile.getLastLogin());
            ps.setLong(5, profile.getLastLogout());
            ps.setInt(6, profile.getLoginCount());
            ps.setLong(7, profile.getTotalPlaytimeMs());
            ps.setString(8, nullToEmpty(profile.getLastWorld()));
            ps.setDouble(9, profile.getLastX());
            ps.setDouble(10, profile.getLastY());
            ps.setDouble(11, profile.getLastZ());
            ps.setLong(12, profile.getLastGreetingTime());
            ps.setLong(13, profile.getProfileAnalyzedAt());
            ps.setString(14, serializeExtendedData(profile.getExtendedData()));
            ps.setLong(15, now);
            ps.executeUpdate();
        }
    }

    /**
     * 更新玩家画像（登出时调用）
     */
    public void update(Connection conn, PlayerProfile profile) throws SQLException {
        String sql = "UPDATE " + tablePrefix + "player_profile SET " + "name = ?, last_login = ?, last_logout = ?, login_count = ?, " + "total_playtime_ms = ?, last_world = ?, " + "last_x = ?, last_y = ?, last_z = ?, " + "last_greeting_time = ?, profile_analyzed_at = ?, profile_data = ?, updated_at = ? " + "WHERE uuid = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, profile.getName());
            ps.setLong(2, profile.getLastLogin());
            ps.setLong(3, profile.getLastLogout());
            ps.setInt(4, profile.getLoginCount());
            ps.setLong(5, profile.getTotalPlaytimeMs());
            ps.setString(6, nullToEmpty(profile.getLastWorld()));
            ps.setDouble(7, profile.getLastX());
            ps.setDouble(8, profile.getLastY());
            ps.setDouble(9, profile.getLastZ());
            ps.setLong(10, profile.getLastGreetingTime());
            ps.setLong(11, profile.getProfileAnalyzedAt());
            ps.setString(12, serializeExtendedData(profile.getExtendedData()));
            ps.setLong(13, System.currentTimeMillis());
            ps.setString(14, profile.getUuid().toString());
            ps.executeUpdate();
        }
    }

    /**
     * 更新问候时间
     */
    public void updateGreetingTime(Connection conn, UUID uuid) throws SQLException {
        String sql = "UPDATE " + tablePrefix + "player_profile SET " + "last_greeting_time = ?, updated_at = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        }
    }

    /**
     * 更新画像数据和分析时间戳（画像分析服务专用）
     *
     * @param conn         数据库连接
     * @param uuid         玩家 UUID
     * @param extendedData 扩展数据 Map
     * @param analyzedAt   本次分析完成时间戳
     */
    public void updateProfileData(Connection conn, UUID uuid, Map<String, Object> extendedData, long analyzedAt) throws SQLException {
        String sql = "UPDATE " + tablePrefix + "player_profile SET " + "profile_data = ?, profile_analyzed_at = ?, updated_at = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serializeExtendedData(extendedData));
            ps.setLong(2, analyzedAt);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, uuid.toString());
            ps.executeUpdate();
        }
    }

    /**
     * 按玩家名加载画像（用于私聊监听等按名反查场景）
     *
     * @param conn 数据库连接
     * @param name 玩家名（不区分大小写）
     * @return 玩家画像，不存在返回 null
     */
    public PlayerProfile loadByName(Connection conn, String name) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "player_profile " + "WHERE LOWER(name) = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    private PlayerProfile mapRow(ResultSet rs) throws SQLException {
        return PlayerProfile.builder().uuid(UUID.fromString(rs.getString("uuid"))).name(rs.getString("name")).firstLogin(rs.getLong("first_login")).lastLogin(rs.getLong("last_login")).lastLogout(rs.getLong("last_logout")).loginCount(rs.getInt("login_count")).totalPlaytimeMs(rs.getLong("total_playtime_ms")).lastWorld(rs.getString("last_world")).lastX(rs.getDouble("last_x")).lastY(rs.getDouble("last_y")).lastZ(rs.getDouble("last_z")).lastGreetingTime(rs.getLong("last_greeting_time")).profileAnalyzedAt(rs.getLong("profile_analyzed_at")).extendedData(deserializeExtendedData(rs.getString("profile_data"))).build();
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private String serializeExtendedData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return "";
        return GSON.toJson(data);
    }

    private Map<String, Object> deserializeExtendedData(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return GSON.fromJson(json, MAP_TYPE);
        } catch (Exception e) {
            PluginLogger.warn("数据库", "解析 profile_data 失败: {}", e.getMessage());
            return null;
        }
    }
}
