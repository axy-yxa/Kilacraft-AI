package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.db.DatabaseConfig;
import com.zm.kilacraftAI.db.DatabaseType;
import com.zm.kilacraftAI.util.ConfigResourceUtil;
import com.zm.kilacraftAI.util.PluginLogger;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * 数据库配置管理器
 *
 * <p>管理独立的 database.yml 配置文件，支持热重载。</p>
 * <p>与 PersonalitiesConfigManager 遵循相同的配置管理模式。</p>
 *
 * @author Zm_Mmm
 * @since 2026-05-01
 */
public class DatabaseConfigManager {

    private static final String CONFIG_FILE = "database.yml";

    private final KilacraftAI plugin;
    private File configFile;
    /**
     * 数据库配置
     */
    @Getter
    private DatabaseConfig config;

    public DatabaseConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);

        // 确保默认配置文件存在
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);

        loadConfig();
    }

    /**
     * 加载数据库配置
     */
    public void loadConfig() {
        if (!configFile.exists()) {
            PluginLogger.warn("数据库", "数据库配置文件不存在，使用默认 H2 配置");
            this.config = buildDefaultConfig();
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        try {
            this.config = parseConfig(yaml);
            PluginLogger.info("数据库", "数据库配置已加载，类型: {}", config.getType());
        } catch (Exception e) {
            PluginLogger.error("数据库", "数据库配置解析失败，使用默认 H2 配置: {}", e.getMessage());
            this.config = buildDefaultConfig();
        }
    }

    /**
     * 热重载配置
     */
    public void reload() {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        loadConfig();
    }

    /**
     * 解析 YAML 配置为 DatabaseConfig
     */
    private DatabaseConfig parseConfig(FileConfiguration yaml) {
        String typeStr = yaml.getString("type", "H2").toUpperCase();
        DatabaseType type;
        try {
            type = DatabaseType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            PluginLogger.warn("数据库", "未知的数据库类型: {}，回退到 H2", typeStr);
            type = DatabaseType.H2;
        }

        return DatabaseConfig.builder().type(type).h2File(yaml.getString("h2.file", "data/kilacraft")).mysqlHost(yaml.getString("mysql.host", "localhost")).mysqlPort(yaml.getInt("mysql.port", 3306)).mysqlDatabase(yaml.getString("mysql.database", "kilacraft_ai")).mysqlUsername(yaml.getString("mysql.username", "root")).mysqlPassword(yaml.getString("mysql.password", "password")).tablePrefix(yaml.getString("mysql.table_prefix", "kca_")).maximumPoolSize(yaml.getInt("pool.maximum_pool_size", 0)).minimumIdle(yaml.getInt("pool.minimum_idle", 0)).connectionTimeout(yaml.getLong("pool.connection_timeout", 10000)).idleTimeout(yaml.getLong("pool.idle_timeout", 300000)).maxLifetime(yaml.getLong("pool.max_lifetime", 1800000)).conversationRetentionDays(yaml.getInt("retention.conversation_retention_days", 60)).eventRetentionDays(yaml.getInt("retention.event_retention_days", 90)).skillLogRetentionDays(yaml.getInt("retention.skill_log_retention_days", 60)).loadHistoryOnLogin(yaml.getBoolean("conversation.load_history_on_login", true)).profileAnalysisIntervalDays(yaml.getInt("profile.analysis_interval_days", 1)).profileMinMessagesToTrigger(yaml.getInt("profile.min_messages_to_trigger", 10)).profileAnalysisTimeoutSeconds(yaml.getInt("profile.analysis_timeout_seconds", 60)).profileAnalysisSystemPrompt(yaml.getString("profile.analysis_system_prompt", "")).profileAnalysisSystemPromptEn(yaml.getString("profile.analysis_system_prompt_en", "")).profileIncrementalSystemPrompt(yaml.getString("profile.incremental_system_prompt", "")).profileIncrementalSystemPromptEn(yaml.getString("profile.incremental_system_prompt_en", "")).serverId(yaml.getString("group.server_id", "")).build();
    }

    /**
     * 构建默认 H2 配置（零配置可用）
     */
    private DatabaseConfig buildDefaultConfig() {
        return DatabaseConfig.builder().type(DatabaseType.H2).build();
    }
}
