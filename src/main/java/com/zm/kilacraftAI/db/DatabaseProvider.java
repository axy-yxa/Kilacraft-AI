package com.zm.kilacraftAI.db;

import com.zm.kilacraftAI.common.enums.DatabaseTypeEnum;
import com.zm.kilacraftAI.db.model.DatabaseConfig;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库供应商接口
 *
 * <p>定义数据库连接池的生命周期管理。H2 和 MySQL 各自实现此接口。</p>
 *
 * @author Zm_Mmm
 */
public interface DatabaseProvider {

    /**
     * 初始化连接池
     *
     * @param config 数据库配置
     * @throws SQLException 连接池初始化失败
     */
    void initialize(DatabaseConfig config) throws SQLException;

    /**
     * 关闭连接池（优雅关闭，等待活跃连接完成）
     */
    void shutdown();

    /**
     * 获取数据库连接（由 HikariCP 管理）
     *
     * @return 数据库连接
     * @throws SQLException 获取连接失败
     */
    Connection getConnection() throws SQLException;

    /**
     * 获取数据库类型
     *
     * @return 数据库类型枚举
     */
    DatabaseTypeEnum getType();

    /**
     * 测试连接可用性
     *
     * @return true 表示连接正常
     */
    boolean testConnection();

    /**
     * 获取连接池状态信息（用于诊断报告）
     *
     * <p>返回格式化字符串，包含活跃/空闲/等待线程数等。
     * 如果连接池未初始化或已关闭，返回 "N/A"。</p>
     *
     * @return 连接池状态信息
     */
    default String getPoolInfo() {
        return "N/A";
    }
}
