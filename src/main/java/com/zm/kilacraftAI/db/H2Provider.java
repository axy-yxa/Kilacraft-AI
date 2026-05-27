package com.zm.kilacraftAI.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.DatabaseTypeEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.db.model.DatabaseConfig;
import com.zm.kilacraftAI.i18n.I18nService;
import org.h2.tools.Server;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * H2 数据库供应商实现（单服内嵌模式）
 *
 * <p>H2 是零配置的内嵌数据库，数据存储在插件目录下的文件中。
 * 连接池参数采用自适应策略：maxPoolSize = max(2, CPU)。</p>
 *
 * @author Zm_Mmm
 */
public class H2Provider implements DatabaseProvider {

    private HikariDataSource dataSource;

    /**
     * H2 TCP Server，用于外部工具（如 DBeaver）在不停服时连接查看数据
     */
    private Server tcpServer;

    /**
     * H2 TCP 端口
     */
    private static final int DEFAULT_TCP_PORT = 9092;

    @Override
    public void initialize(DatabaseConfig config) throws SQLException {
        File dataDir = new File(KilacraftAI.getInstance().getDataFolder(), config.getH2File());
        // 确保父目录存在
        File parentDir = dataDir.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:" + dataDir.getAbsolutePath() + ";AUTO_SERVER=TRUE;MODE=MySQL");
        hc.setDriverClassName("org.h2.Driver");
        hc.setPoolName("KilacraftAI-H2");

        // 自适应连接池大小
        int cpu = Runtime.getRuntime().availableProcessors();
        int maxPool = config.getMaximumPoolSize() > 0 ? config.getMaximumPoolSize() : Math.max(2, cpu);
        int minIdle = config.getMinimumIdle() > 0 ? config.getMinimumIdle() : 1;

        hc.setMaximumPoolSize(maxPool);
        hc.setMinimumIdle(minIdle);
        hc.setConnectionTimeout(config.getConnectionTimeout());
        hc.setIdleTimeout(config.getIdleTimeout());
        hc.setMaxLifetime(config.getMaxLifetime());

        // H2 优化配置
        hc.addDataSourceProperty("CACHE_SIZE", "65536");
        hc.addDataSourceProperty("AUTO_RECONNECT", "TRUE");

        this.dataSource = new HikariDataSource(hc);

        // 启动 H2 TCP Server（允许 DBeaver 等外部工具在不停服时连接）
        startTcpServer();

        PluginLoggerUtil.info("数据库", "H2 数据库已初始化");
        PluginLoggerUtil.info("数据库", "连接池配置: maxPoolSize={}, minIdle={}", maxPool, minIdle);
    }

    @Override
    public void shutdown() {
        // 先关闭 TCP Server
        stopTcpServer();

        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            PluginLoggerUtil.info("数据库", "H2 连接池已关闭");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public DatabaseTypeEnum getType() {
        return DatabaseTypeEnum.H2;
    }

    @Override
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            PluginLoggerUtil.error("数据库", "H2 连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getPoolInfo() {
        if (dataSource == null || dataSource.isClosed()) return I18nService.tr("H2: 未初始化/已关闭");
        var mxBean = dataSource.getHikariPoolMXBean();
        return I18nService.tr("H2: 活跃={}, 空闲={}, 等待={}, 最大={}", mxBean.getActiveConnections(), mxBean.getIdleConnections(), mxBean.getThreadsAwaitingConnection(), dataSource.getMaximumPoolSize());
    }

    /**
     * 启动 H2 TCP Server
     *
     * <p>允许外部工具（如 DBeaver）通过 {@code jdbc:h2:tcp://localhost:9092/<dbPath>} 连接查看数据。</p>
     */
    private void startTcpServer() {
        try {
            tcpServer = Server.createTcpServer("-tcpPort", String.valueOf(DEFAULT_TCP_PORT), "-tcpAllowOthers", "-tcpDaemon").start();
            PluginLoggerUtil.info("数据库", "H2 TCP Server 已启动，端口: {}", DEFAULT_TCP_PORT);
        } catch (SQLException e) {
            PluginLoggerUtil.warn("数据库", "H2 TCP Server 启动失败（端口 {} 可能被占用）: {}", DEFAULT_TCP_PORT, e.getMessage());
        }
    }

    /**
     * 关闭 H2 TCP Server
     */
    private void stopTcpServer() {
        if (tcpServer != null && tcpServer.isRunning(false)) {
            tcpServer.stop();
            PluginLoggerUtil.info("数据库", "H2 TCP Server 已关闭");
        }
    }
}
