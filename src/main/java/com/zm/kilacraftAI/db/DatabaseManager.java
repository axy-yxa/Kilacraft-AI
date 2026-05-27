package com.zm.kilacraftAI.db;

import com.zm.kilacraftAI.common.enums.DatabaseTypeEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.DatabaseConfigManager;
import com.zm.kilacraftAI.db.model.DatabaseConfig;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库统一管理器（适配器入口）
 *
 * @author Zm_Mmm
 */
public class DatabaseManager {

    private volatile DatabaseProvider provider;
    private SchemaManager schemaManager;
    private volatile DatabaseConfig currentConfig;

    /**
     * 初始化数据库
     *
     * <p>流程：创建 Provider → 建池 → 迁移 Schema → 验证连接</p>
     * <p>MySQL 连接失败时自动回退到 H2，确保持久化功能可用。</p>
     *
     * @param configManager 数据库配置管理器
     * @throws SQLException 初始化失败（包括 H2 回退也失败时）
     */
    public void initialize(DatabaseConfigManager configManager) throws SQLException {
        this.currentConfig = configManager.getConfig();

        try {
            createProvider(currentConfig);
        } catch (SQLException e) {
            // MySQL 连接失败，自动回退到 H2
            if (currentConfig.getType() == DatabaseTypeEnum.MYSQL) {
                PluginLoggerUtil.error("数据库", "MySQL 初始化失败: {}", e.getMessage());
                PluginLoggerUtil.warn("数据库", "自动回退到 H2 内置数据库，持久化功能正常可用");

                DatabaseConfig h2Config = DatabaseConfig.builder()
                        .type(DatabaseTypeEnum.H2)
                        .tablePrefix(currentConfig.getTablePrefix())
                        .build();
                this.currentConfig = h2Config;
                createProvider(h2Config);
            } else {
                throw e;
            }
        }

        // Schema 迁移
        this.schemaManager = new SchemaManager(provider, currentConfig.getTablePrefix(), currentConfig.getType());
        schemaManager.initialize();

        PluginLoggerUtil.info("数据库", "数据库初始化完成，类型: {}", currentConfig.getType());
    }

    /**
     * 热重载：关闭旧池 → 按新配置创建新池 → 验证连接
     *
     * <p>如果新配置连接测试失败，回退到旧连接池。
     * 如果新配置为 MySQL 且连接失败，自动回退到 H2（与启动行为一致）。</p>
     *
     * @param newConfig 新的数据库配置
     * @throws SQLException 新配置初始化失败（包括 H2 回退也失败时）
     */
    public void reload(DatabaseConfig newConfig) throws SQLException {
        DatabaseConfig oldConfig = this.currentConfig;

        // 配置完全相同则跳过重建（避免无意义的连接池销毁与创建）
        if (oldConfig != null && configEquals(oldConfig, newConfig)) {
            // 连接参数未变则跳过连接池重建，但非连接级字段（如 group.*）仍需更新
            this.currentConfig = newConfig;
            PluginLoggerUtil.info("数据库", "热重载跳过，配置未变化（类型: {}）", newConfig.getType());
            return;
        }

        DatabaseProvider oldProvider = this.provider;

        PluginLoggerUtil.info("数据库", "开始热重载，旧类型: {}，新类型: {}", oldConfig != null ? oldConfig.getType() : "无", newConfig.getType());

        try {
            // 创建新 Provider
            createProvider(newConfig);

            // Schema 迁移（新库）
            this.schemaManager = new SchemaManager(provider, newConfig.getTablePrefix(), newConfig.getType());
            schemaManager.initialize();

            // 验证连接
            if (!provider.testConnection()) {
                throw new SQLException("连接测试失败");
            }

            // 新配置成功，关闭旧 Provider
            if (oldProvider != null) {
                oldProvider.shutdown();
            }

            this.currentConfig = newConfig;
            PluginLoggerUtil.info("数据库", "热重载完成，新类型: {}", newConfig.getType());

        } catch (SQLException e) {
            // MySQL 失败，尝试 H2 回退（与 initialize 行为一致）
            if (newConfig.getType() == DatabaseTypeEnum.MYSQL) {
                PluginLoggerUtil.error("数据库", "MySQL 热重载失败: {}", e.getMessage());
                PluginLoggerUtil.warn("数据库", "自动回退到 H2 内置数据库，持久化功能正常可用");

                try {
                    // 先关闭旧 Provider（释放 H2 TCP Server 端口等资源），再创建新 H2
                    if (oldProvider != null) {
                        oldProvider.shutdown();
                    }

                    DatabaseConfig h2Config = DatabaseConfig.builder()
                            .type(DatabaseTypeEnum.H2)
                            .tablePrefix(newConfig.getTablePrefix())
                            .build();
                    createProvider(h2Config);
                    this.schemaManager = new SchemaManager(provider, h2Config.getTablePrefix(), h2Config.getType());
                    schemaManager.initialize();

                    this.currentConfig = h2Config;
                    PluginLoggerUtil.info("数据库", "热重载完成（已回退到 H2）");
                    return;
                } catch (SQLException h2Ex) {
                    // H2 回退也失败，旧 Provider 已关闭无法恢复
                    PluginLoggerUtil.error("数据库", "H2 回退也失败，无可用数据库: {}", h2Ex.getMessage());
                    throw h2Ex;
                }
            }

            // 非 MySQL 类型失败，回退到旧 Provider
            PluginLoggerUtil.error("数据库", "热重载失败，回退到旧配置: {}", e.getMessage());
            this.provider = oldProvider;
            this.currentConfig = oldConfig;
            throw e;
        }
    }

    /**
     * 优雅关闭连接池
     */
    public void shutdown() {
        if (provider != null) {
            provider.shutdown();
            provider = null;
        }
        PluginLoggerUtil.info("数据库", "数据库管理器已关闭");
    }

    /**
     * 获取数据库连接
     *
     * @return 数据库连接
     * @throws SQLException 获取连接失败
     */
    public Connection getConnection() throws SQLException {
        if (provider == null) {
            throw new SQLException("数据库未初始化");
        }
        return provider.getConnection();
    }

    /**
     * 获取表名前缀
     *
     * @return 表名前缀（如 "kca_"）
     */
    public String getTablePrefix() {
        return currentConfig != null ? currentConfig.getTablePrefix() : "kca_";
    }

    /**
     * 获取当前数据库配置
     *
     * @return 数据库配置
     */
    public DatabaseConfig getConfig() {
        return currentConfig;
    }

    /**
     * 获取连接池状态信息（用于诊断报告）
     *
     * @return 连接池状态字符串，未初始化时返回 "N/A"
     */
    public String getPoolInfo() {
        return provider != null ? provider.getPoolInfo() : "N/A";
    }

    /**
     * 根据配置创建对应的 DatabaseProvider
     */
    private void createProvider(DatabaseConfig config) throws SQLException {
        this.provider = switch (config.getType()) {
            case H2 -> new H2Provider();
            case MYSQL -> new MySQLProvider();
        };
        provider.initialize(config);
    }

    /**
     * 判断两个数据库配置是否等价（无需重建连接池）
     *
     * <p>比较数据库类型、连接参数、表前缀等关键字段。</p>
     */
    private boolean configEquals(DatabaseConfig a, DatabaseConfig b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getType() == b.getType() && equalsSafe(a.getH2File(), b.getH2File()) && equalsSafe(a.getTablePrefix(), b.getTablePrefix()) && equalsSafe(a.getMysqlHost(), b.getMysqlHost()) && a.getMysqlPort() == b.getMysqlPort() && equalsSafe(a.getMysqlDatabase(), b.getMysqlDatabase()) && equalsSafe(a.getMysqlUsername(), b.getMysqlUsername()) && equalsSafe(a.getMysqlPassword(), b.getMysqlPassword()) && a.getMaximumPoolSize() == b.getMaximumPoolSize() && a.getMinimumIdle() == b.getMinimumIdle() && a.getConnectionTimeout() == b.getConnectionTimeout() && a.getIdleTimeout() == b.getIdleTimeout() && a.getMaxLifetime() == b.getMaxLifetime();
    }

    private static boolean equalsSafe(Object a, Object b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }
}
