package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.LLMCompatibilityResolver;
import com.zm.kilacraftAI.llm.ThinkingModelCapable;
import com.zm.kilacraftAI.llm.ThinkingModelConfig;
import com.zm.kilacraftAI.service.notification.NotificationService;
import lombok.Getter;
import okhttp3.OkHttpClient;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 服主管理功能独立配置管理器
 *
 * @author Zm_Mmm
 * @since 2026-05-19
 */
public class AdminConfigManager {

    private static final String CONFIG_FILE_NAME = "admin.yml";
    private static final String LOG_PREFIX = "服主管理";

    /**
     * 诊断模型来源（决定连接字段取自 admin.yml 还是回退 llm.yml）
     */
    public enum DiagnosticModelSource {
        /**
         * admin.yml 显式配置了有效 api_key
         */
        ADMIN_EXPLICIT,
        /**
         * 回退使用 llm.yml 基础模型（仅复用 url/key/model）
         */
        LLM_FALLBACK,
        /**
         * 均未配置，诊断功能不可用
         */
        NONE
    }

    private final KilacraftAI plugin;
    private final File configFile;
    private YamlConfiguration config;

    /*
    推理模型配置
     */
    private volatile String thinkingApiUrl;
    private volatile String thinkingApiKey;
    private volatile String thinkingModel;
    private volatile int thinkingMaxTokens;
    private volatile int thinkingTimeout;

    /**
     * 当前诊断模型来源（在 reload() 末尾计算并缓存，volatile 保证可见性）
     */
    private volatile DiagnosticModelSource diagnosticSource = DiagnosticModelSource.NONE;

    /*
    守护线程配置
     */
    @Getter
    private volatile boolean guardianEnabled;
    @Getter
    private volatile int guardianInterval;
    @Getter
    private volatile int guardianCooldown;
    @Getter
    private volatile int autoProfilerTimeout;
    @Getter
    private volatile long maxProfilerDownloadBytes;
    @Getter
    private volatile boolean downloadWhenExceeded;
    @Getter
    private volatile int maxAutoAnalysisPerWindow;
    @Getter
    private volatile int autoAnalysisWindowMinutes;
    @Getter
    private volatile int msptConsecutiveThreshold;
    @Getter
    private volatile Map<String, Double> alertThresholds;

    /*
    诊断报告配置
     */
    private volatile String reportOutputDir;
    @Getter
    private volatile boolean includeReasoning;

    /*
    外部通知配置
     */
    @Getter
    private volatile boolean notificationEnabled;
    @Getter
    private volatile List<NotificationService.ChannelConfig> notificationChannels;

    /*
    AI 诊断提示词配置
     */
    @Getter
    private volatile String diagnosticSystemPrompt;
    @Getter
    private volatile String diagnosticSystemPromptEn;
    @Getter
    private volatile String autoModeInstruction;
    @Getter
    private volatile String manualModeInstruction;
    @Getter
    private volatile String autoModeInstructionEn;
    @Getter
    private volatile String manualModeInstructionEn;

    // 推理模型专用 HTTP 客户端
    // 通过 GenericLLMProvider.httpClient.newBuilder() 创建，共享连接池但使用更长超时
    @Getter
    private volatile OkHttpClient thinkingHttpClient;

    public AdminConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);
    }

    /**
     * 初始化配置文件并加载
     */
    public void loadConfig() {
        // 首次运行时从 JAR 中提取默认配置
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                try (InputStream is = plugin.getResource(CONFIG_FILE_NAME)) {
                    if (is != null) {
                        Files.copy(is, configFile.toPath());
                        PluginLoggerUtil.info(LOG_PREFIX, "已生成默认配置文件: {}", CONFIG_FILE_NAME);
                    }
                }
            } catch (IOException e) {
                PluginLoggerUtil.error(LOG_PREFIX, I18nService.tr("无法创建默认配置文件: {}", e.getMessage()), e);
            }
        }

        reload();
    }

    /**
     * 热重载配置
     */
    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);

        // 加载推理模型配置
        thinkingApiUrl = LLMCompatibilityResolver.resolveApiUrl(config.getString("thinking_model.api_url", "https://api.deepseek.com/v1/chat/completions"));
        thinkingApiKey = config.getString("thinking_model.api_key", "");
        thinkingModel = LLMCompatibilityResolver.resolveModel(config.getString("thinking_model.model", "deepseek-v4-pro"));
        thinkingMaxTokens = config.getInt("thinking_model.max_tokens", 4096);
        thinkingTimeout = config.getInt("thinking_model.timeout_seconds", 120);

        // 加载守护线程配置
        guardianEnabled = config.getBoolean("health_guardian.enabled", true);
        guardianInterval = Math.max(1, config.getInt("health_guardian.interval_seconds", 10));
        guardianCooldown = Math.max(1, config.getInt("health_guardian.cooldown_minutes", 5));
        autoProfilerTimeout = Math.max(1, config.getInt("health_guardian.auto_profiler_timeout", 30));
        maxProfilerDownloadBytes = config.getLong("health_guardian.max_profiler_download_bytes", 52428800L); // 默认 50MB
        downloadWhenExceeded = config.getBoolean("health_guardian.download_when_exceeded", false);
        maxAutoAnalysisPerWindow = Math.max(1, config.getInt("health_guardian.max_auto_analysis_per_window", 5));
        autoAnalysisWindowMinutes = Math.max(1, config.getInt("health_guardian.auto_analysis_window_minutes", 60));
        msptConsecutiveThreshold = Math.max(1, config.getInt("health_guardian.mspt_consecutive_threshold", 3));

        // 加载告警阈值（先构建完整 Map 再赋值，避免 volatile 半发布）
        Map<String, Double> thresholds = new HashMap<>();
        thresholds.put("tps_threshold", config.getDouble("health_guardian.alerts.tps_threshold", 15.0));
        thresholds.put("mspt_max_threshold", config.getDouble("health_guardian.alerts.mspt_max_threshold", 50.0));
        thresholds.put("mspt_p95_threshold", config.getDouble("health_guardian.alerts.mspt_p95_threshold", 50.0));
        thresholds.put("cpu_threshold", config.getDouble("health_guardian.alerts.cpu_threshold", 80.0));
        this.alertThresholds = Collections.unmodifiableMap(thresholds);

        // 加载诊断报告配置
        reportOutputDir = config.getString("diagnostic_report.output_dir", "reports");
        includeReasoning = config.getBoolean("diagnostic_report.include_reasoning", true);

        // 加载外部通知配置
        loadNotificationConfig();

        // 加载 AI 诊断提示词配置（留空使用代码默认值）
        diagnosticSystemPrompt = config.getString("prompts.system_prompt", "");
        diagnosticSystemPromptEn = config.getString("prompts.system_prompt_en", "");
        autoModeInstruction = config.getString("prompts.auto_mode_instruction", "系统自动检测到服务器性能异常并触发了 Profiler 采样，请分析数据，定位根因并给出优化建议");
        manualModeInstruction = config.getString("prompts.manual_mode_instruction", "服主手动触发了性能采样，请分析数据，判断是否存在性能问题，如果有则定位根因并给出优化建议");
        autoModeInstructionEn = config.getString("prompts.auto_mode_instruction_en", "");
        manualModeInstructionEn = config.getString("prompts.manual_mode_instruction_en", "");

        // 计算诊断模型来源（优先级：admin 显式 > llm 回退 > 无）
        if (isApiKeyValid(thinkingApiKey)) {
            diagnosticSource = DiagnosticModelSource.ADMIN_EXPLICIT;
        } else if (plugin.getConfigManager() != null && isApiKeyValid(plugin.getConfigManager().getLlmApiKey())) {
            diagnosticSource = DiagnosticModelSource.LLM_FALLBACK;
        } else {
            diagnosticSource = DiagnosticModelSource.NONE;
        }

        // 仅在守护线程启用且诊断模型可用时，才重建 HTTP 客户端
        if (guardianEnabled && diagnosticSource != DiagnosticModelSource.NONE) {
            rebuildThinkingHttpClient();
            PluginLoggerUtil.info(LOG_PREFIX, "配置已加载（守护线程: 启用，诊断模型: {}）", getThinkingModelConfig().model());
        } else {
            PluginLoggerUtil.info(LOG_PREFIX, "配置已加载（守护线程: {}）", I18nService.tr(guardianEnabled ? "启用" : "禁用"));
        }
    }

    /**
     * 加载外部通知配置
     */
    private void loadNotificationConfig() {
        notificationEnabled = config.getBoolean("notification.enabled", false);

        List<NotificationService.ChannelConfig> channels = new ArrayList<>();
        for (var map : config.getMapList("notification.channels")) {
            String type = (String) map.get("type");
            String webhookUrl = (String) map.get("webhook_url");
            String secret = (String) map.get("secret");

            if (type != null && !type.isBlank() && webhookUrl != null && !webhookUrl.isBlank()) {
                channels.add(new NotificationService.ChannelConfig(type, webhookUrl, secret));
            }
        }
        this.notificationChannels = Collections.unmodifiableList(channels);
    }

    /**
     * 重建推理模型专用 HTTP 客户端
     *
     * <p>通过 ThinkingModelCapable 的共享 HTTP 客户端创建派生客户端，
     * 共享连接池但使用更长的超时时间（推理模型默认 120s）。</p>
     */
    private void rebuildThinkingHttpClient() {
        // 保存旧客户端引用，重建后清理其独有资源
        OkHttpClient oldClient = this.thinkingHttpClient;

        if (plugin.getLlmManager() != null && plugin.getLlmManager().getCurrentProvider() instanceof ThinkingModelCapable capable) {
            this.thinkingHttpClient = capable.getSharedHttpClient().newBuilder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(thinkingTimeout, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build();
            PluginLoggerUtil.info(LOG_PREFIX, "推理模型 HTTP 客户端已重建（超时: {}s）", thinkingTimeout);
        }

        // 清理旧客户端：取消待执行请求、清空空闲连接（共享连接池不受影响）
        if (oldClient != null) {
            oldClient.dispatcher().cancelAll();
            oldClient.connectionPool().evictAll();
        }
    }

    /**
     * 诊断模型是否可用（admin.yml 显式配置 或 回退 llm.yml 基础模型）
     *
     * <p>方法名保留以避免 3 处调用点连锁改动；语义已从"推理模型是否配置"扩展为"诊断功能是否有可用模型"。</p>
     */
    public boolean isThinkingModelConfigured() {
        return diagnosticSource != DiagnosticModelSource.NONE;
    }

    /**
     * 获取诊断模型配置
     *
     * <p>回退时连接字段（url/key/model）取自 llm.yml，max_tokens/timeout 始终用 admin.yml 诊断专用值
     * （不复用 llm.yml 的小配额，否则报告被截断）。</p>
     */
    public ThinkingModelConfig getThinkingModelConfig() {
        if (diagnosticSource == DiagnosticModelSource.LLM_FALLBACK) {
            var cm = plugin.getConfigManager();
            return new ThinkingModelConfig(cm.getLlmApiUrl(), cm.getLlmApiKey(), cm.getLlmModel(), thinkingMaxTokens, thinkingTimeout);
        }
        return new ThinkingModelConfig(thinkingApiUrl, thinkingApiKey, thinkingModel, thinkingMaxTokens, thinkingTimeout);
    }

    /**
     * 判断 API key 是否有效（非空且非默认占位符）
     */
    private boolean isApiKeyValid(String key) {
        return key != null && !key.isEmpty() && !"your-api-key".equals(key);
    }

    /**
     * 获取报告输出目录的绝对路径
     */
    public File getReportOutputDirectory() {
        File dir = new File(plugin.getDataFolder(), reportOutputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 按语言获取诊断 System Prompt
     *
     * @param isChinese       是否中文模式
     * @param fallbackDefault 回退默认值
     * @return 诊断 System Prompt
     */
    public String getDiagnosticSystemPromptByLanguage(boolean isChinese, String fallbackDefault) {
        if (!isChinese) {
            if (diagnosticSystemPromptEn != null && !diagnosticSystemPromptEn.isBlank()) {
                return diagnosticSystemPromptEn;
            }
        }
        if (diagnosticSystemPrompt != null && !diagnosticSystemPrompt.isBlank()) {
            return diagnosticSystemPrompt;
        }
        return fallbackDefault;
    }

    /**
     * 按语言获取 auto 模式职责描述
     *
     * @param isChinese       是否中文模式
     * @param fallbackDefault 回退默认值
     * @return auto 模式职责描述
     */
    public String getAutoModeInstructionByLanguage(boolean isChinese, String fallbackDefault) {
        if (!isChinese) {
            if (autoModeInstructionEn != null && !autoModeInstructionEn.isBlank()) {
                return autoModeInstructionEn;
            }
        }
        if (autoModeInstruction != null && !autoModeInstruction.isBlank()) {
            return autoModeInstruction;
        }
        return fallbackDefault;
    }

    /**
     * 按语言获取 manual 模式职责描述
     *
     * @param isChinese       是否中文模式
     * @param fallbackDefault 回退默认值
     * @return manual 模式职责描述
     */
    public String getManualModeInstructionByLanguage(boolean isChinese, String fallbackDefault) {
        if (!isChinese) {
            if (manualModeInstructionEn != null && !manualModeInstructionEn.isBlank()) {
                return manualModeInstructionEn;
            }
        }
        if (manualModeInstruction != null && !manualModeInstruction.isBlank()) {
            return manualModeInstruction;
        }
        return fallbackDefault;
    }
}
