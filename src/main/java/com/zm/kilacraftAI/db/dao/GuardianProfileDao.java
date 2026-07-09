package com.zm.kilacraftAI.db.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 守护系统配置 DAO
 *
 * <p>提供 {@code kca_guardian_profile} 表的读写操作（每玩家守护配置 + 静音列表，跨服共享）。</p>
 *
 * <h3>H2 / MySQL 兼容性</h3>
 * <ul>
 *   <li>upsert 采用 UPDATE-first + INSERT-fallback（不用 ON DUPLICATE KEY UPDATE，H2 不支持）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-07-07
 */
public class GuardianProfileDao {

    private final String tablePrefix;

    public GuardianProfileDao(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    /**
     * 查询玩家是否启用守护
     *
     * @param conn 数据库连接
     * @param uuid 玩家 UUID
     * @return enabled 字段；记录不存在时返回 false
     */
    public boolean isEnabled(Connection conn, String uuid) throws SQLException {
        String sql = "SELECT enabled FROM " + tablePrefix + "guardian_profile WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("enabled");
            }
        }
    }

    /**
     * 读取静音分类列表
     *
     * @param conn 数据库连接
     * @param uuid 玩家 UUID
     * @return 静音分类集合；记录不存在或字段为空时返回空集合
     */
    public Set<String> loadSilenceList(Connection conn, String uuid) throws SQLException {
        String sql = "SELECT silence_list FROM " + tablePrefix + "guardian_profile WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Set.of();
                }
                return parseSilenceList(rs.getString("silence_list"));
            }
        }
    }

    /**
     * 写入或更新玩家守护配置（UPDATE-first + INSERT-fallback，兼容 H2 和 MySQL）
     *
     * @param conn        数据库连接
     * @param uuid        玩家 UUID
     * @param enabled     是否启用守护
     * @param configJson  monitor 配置 JSON（可为 null）
     * @param silenceList 静音分类（逗号分隔，可为 null/空）
     * @param updatedAt   更新时间戳（ms）
     */
    public void upsert(Connection conn, String uuid, boolean enabled, String configJson,
                       String silenceList, long updatedAt) throws SQLException {
        String updateSql = "UPDATE " + tablePrefix + "guardian_profile SET "
                + "enabled = ?, config_json = ?, silence_list = ?, updated_at = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setBoolean(1, enabled);
            ps.setString(2, configJson);
            ps.setString(3, silenceList);
            ps.setLong(4, updatedAt);
            ps.setString(5, uuid);
            if (ps.executeUpdate() > 0) return;
        }

        String insertSql = "INSERT INTO " + tablePrefix + "guardian_profile "
                + "(uuid, enabled, config_json, silence_list, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, uuid);
            ps.setBoolean(2, enabled);
            ps.setString(3, configJson);
            ps.setString(4, silenceList);
            ps.setLong(5, updatedAt);
            ps.executeUpdate();
        }
    }

    private Set<String> parseSilenceList(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
