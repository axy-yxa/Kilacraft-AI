package com.zm.kilacraftAI.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.util.PluginLogger;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * MySQL 数据库供应商实现（群组服远程共享模式）
 *
 * <p>MySQL 用于 BungeeCord 群组服场景，所有子服共享同一个 MySQL 实例。
 * 连接池参数采用自适应策略：maxPoolSize = min(max(4, CPU*2), 20)。</p>
 *
 * @author Zm_Mmm
 */
public class MySQLProvider implements DatabaseProvider {

    private HikariDataSource dataSource;

    @Override
    public void initialize(DatabaseConfig config) throws SQLException {
        HikariConfig hc = new HikariConfig();

        String jdbcUrl = "jdbc:mysql://" + config.getMysqlHost() + ":" + config.getMysqlPort() + "/" + config.getMysqlDatabase() + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true" + "&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";

        hc.setJdbcUrl(jdbcUrl);
        hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hc.setUsername(config.getMysqlUsername());
        hc.setPassword(config.getMysqlPassword());
        hc.setPoolName("KilacraftAI-MySQL");

        // 自适应连接池大小
        int cpu = Runtime.getRuntime().availableProcessors();
        int maxPool = config.getMaximumPoolSize() > 0 ? config.getMaximumPoolSize() : Math.min(Math.max(4, cpu * 2), 20);
        int minIdle = config.getMinimumIdle() > 0 ? config.getMinimumIdle() : Math.max(2, cpu / 2);

        hc.setMaximumPoolSize(maxPool);
        hc.setMinimumIdle(minIdle);
        hc.setConnectionTimeout(config.getConnectionTimeout());
        hc.setIdleTimeout(config.getIdleTimeout());
        hc.setMaxLifetime(config.getMaxLifetime());

        // MySQL 优化配置
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hc.addDataSourceProperty("useServerPrepStmts", "true");

        try {
            this.dataSource = new HikariDataSource(hc);
        } catch (Exception e) {
            throw new SQLException(I18nService.tr("MySQL 连接池初始化失败: {}", e.getMessage()), e);
        }

        PluginLogger.info("数据库", "MySQL 数据库已初始化，地址: {}:{}", config.getMysqlHost(), config.getMysqlPort());
        PluginLogger.info("数据库", "连接池配置: maxPoolSize={}, minIdle={}", maxPool, minIdle);
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            PluginLogger.info("数据库", "MySQL 连接池已关闭");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public DatabaseType getType() {
        return DatabaseType.MYSQL;
    }

    @Override
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            PluginLogger.error("数据库", "MySQL 连接测试失败: {}", e.getMessage());
            return false;
        }
    }
}
