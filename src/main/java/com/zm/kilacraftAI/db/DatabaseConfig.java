package com.zm.kilacraftAI.db;

import com.zm.kilacraftAI.config.DatabaseConfigManager;
import lombok.Builder;
import lombok.Getter;

/**
 * 数据库配置数据类
 *
 * <p>由 {@link DatabaseConfigManager} 从 database.yml 解析后构建。</p>
 *
 * @author Zm_Mmm
 */
@Getter
@Builder
public class DatabaseConfig {

    /**
     * 数据库类型
     */
    private final DatabaseType type;

    // ==================== H2 配置 ====================

    /**
     * H2 数据文件路径（相对于插件目录）
     */
    @Builder.Default
    private final String h2File = "data/kilacraft";

    // ==================== MySQL 配置 ====================

    @Builder.Default
    private final String mysqlHost = "localhost";
    @Builder.Default
    private final int mysqlPort = 3306;
    @Builder.Default
    private final String mysqlDatabase = "kilacraft_ai";
    @Builder.Default
    private final String mysqlUsername = "root";
    @Builder.Default
    private final String mysqlPassword = "password";
    /**
     * 表名前缀（群组服多插件共用DB时区分）
     */
    @Builder.Default
    private final String tablePrefix = "kca_";

    // ==================== 连接池配置 ====================

    /**
     * 最大连接数（0=自适应）
     */
    @Builder.Default
    private final int maximumPoolSize = 0;
    /**
     * 最小空闲连接数（0=自适应）
     */
    @Builder.Default
    private final int minimumIdle = 0;
    /**
     * 连接超时（毫秒）
     */
    @Builder.Default
    private final long connectionTimeout = 10000;
    /**
     * 空闲超时（毫秒）
     */
    @Builder.Default
    private final long idleTimeout = 300000;
    /**
     * 最大存活时间（毫秒）
     */
    @Builder.Default
    private final long maxLifetime = 1800000;

    // ==================== 数据保留策略 ====================

    /**
     * 对话历史保留天数（0=永久保留）
     */
    @Builder.Default
    private final int conversationRetentionDays = 60;
    /**
     * 服务器事件保留天数（0=永久保留）
     */
    @Builder.Default
    private final int eventRetentionDays = 90;
    /**
     * 技能审计日志保留天数（0=永久保留）
     */
    @Builder.Default
    private final int skillLogRetentionDays = 60;

    // ==================== 对话历史加载策略 ====================

    /**
     * 是否在玩家触发AI对话时从数据库加载历史到缓存
     */
    @Builder.Default
    private final boolean loadHistoryOnLogin = true;

    // ==================== 玩家画像分析配置 ====================

    /**
     * 分析触发间隔天数（玩家距离上次分析至少间隔N天才再次触发）
     */
    @Builder.Default
    private final int profileAnalysisIntervalDays = 1;
    /**
     * 触发分析所需最少消息数
     */
    @Builder.Default
    private final int profileMinMessagesToTrigger = 10;
    /**
     * LLM 分析超时时间（秒）
     */
    @Builder.Default
    private final int profileAnalysisTimeoutSeconds = 60;

    /**
     * 画像分析系统提示词（中文，留空使用默认）
     */
    @Builder.Default
    private final String profileAnalysisSystemPrompt = "";

    /**
     * 画像分析系统提示词（英文，留空使用默认）
     */
    @Builder.Default
    private final String profileAnalysisSystemPromptEn = "";

    // ==================== 群组服配置 ====================

    /**
     * 本服唯一标识（群组服中区分不同子服，如 survival / minigame / rpg）
     *
     * <p>单机服或独立 H2 数据库留空即可。server_id 仅影响隔离表的数据过滤和水位标记后缀。</p>
     */
    @Builder.Default
    private final String serverId = "";
}
