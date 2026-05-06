package com.zm.kilacraftAI.db.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 水位标记 DAO
 *
 * <p>提供 {@code kca_watermark} 表的读写操作，用于分布式定时任务的水位标记。</p>
 *
 * <h3>分布式锁支持</h3>
 * <p>{@link #getForUpdate(Connection, String)} 使用 {@code SELECT ... FOR UPDATE} 在显式事务中
 * 获取行锁，实现群组服多子服间的分布式互斥。</p>
 *
 * <h3>H2 / MySQL 兼容性</h3>
 * <ul>
 *   <li>列名 {@code value} 使用反引号包裹，兼容 H2（MySQL 模式）和 MySQL</li>
 *   <li>UPDATE-first + INSERT-fallback 模式，兼容 H2 和 MySQL（不使用 ON DUPLICATE KEY UPDATE）</li>
 *   <li>FOR UPDATE 在 H2（MySQL 模式）和 MySQL 中均支持</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-05-06
 */
public class WatermarkDao {

    private final String tablePrefix;

    public WatermarkDao(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    /**
     * 读取水位标记值（不带锁）
     *
     * @param name 水位名称
     * @return 水位值，不存在时返回 null
     */
    public String get(Connection conn, String name) throws SQLException {
        String sql = "SELECT `value` FROM " + tablePrefix + "watermark WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("value") : null;
            }
        }
    }

    /**
     * 锁定并读取水位标记值（SELECT ... FOR UPDATE）
     *
     * <p><b>必须在显式事务中调用</b>（conn.setAutoCommit(false)），否则行锁随语句完成立即释放。</p>
     *
     * @param name 水位名称
     * @return 水位值，不存在时返回 null
     */
    public String getForUpdate(Connection conn, String name) throws SQLException {
        String sql = "SELECT `value` FROM " + tablePrefix + "watermark WHERE name = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("value") : null;
            }
        }
    }

    /**
     * 写入或更新水位标记（UPDATE-first + INSERT-fallback，兼容 H2 和 MySQL）
     *
     * <p>利用 PRIMARY KEY(name) 天然防重复。</p>
     *
     * @param name  水位名称
     * @param value 水位值
     */
    public void put(Connection conn, String name, String value) throws SQLException {
        // 先尝试 UPDATE
        String updateSql = "UPDATE " + tablePrefix + "watermark SET `value` = ? WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, value);
            ps.setString(2, name);
            if (ps.executeUpdate() > 0) return;
        }

        // 行不存在，INSERT
        String insertSql = "INSERT INTO " + tablePrefix + "watermark (name, `value`) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, name);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }
}
