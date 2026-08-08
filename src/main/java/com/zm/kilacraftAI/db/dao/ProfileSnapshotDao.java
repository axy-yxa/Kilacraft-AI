package com.zm.kilacraftAI.db.dao;

import com.google.gson.Gson;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/**
 * 画像快照 DAO
 *
 * <p>提供 {@code kca_profile_snapshot} 表的写入操作，用于记录玩家画像的历史版本。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-12
 */
public class ProfileSnapshotDao {

    private static final Gson GSON = new Gson();

    private final String tablePrefix;

    public ProfileSnapshotDao(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    /**
     * 插入一条画像快照
     *
     * @param conn         数据库连接
     * @param uuid         玩家 UUID
     * @param profileData  本次分析的画像数据（JSON 序列化）
     * @param messageCount 本次分析的消息数
     * @param windowStart  分析窗口起始时间（ms）
     * @param windowEnd    分析窗口截止时间（ms）
     * @param version      画像版本号
     * @param analyzedAt   分析时间戳（ms）
     */
    public void insert(Connection conn, UUID uuid, Map<String, Object> profileData, int messageCount, long windowStart, long windowEnd, int version, long analyzedAt) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "profile_snapshot " + "(player_uuid, snapshot_data, message_count, window_start, window_end, version, analyzed_at) " + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, GSON.toJson(profileData));
            ps.setInt(3, messageCount);
            ps.setLong(4, windowStart);
            ps.setLong(5, windowEnd);
            ps.setInt(6, version);
            ps.setLong(7, analyzedAt);
            ps.executeUpdate();
        }
    }
}
