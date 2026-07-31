package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.AdminSkillUtil;
import com.zm.kilacraftAI.common.util.LLMResponseUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.*;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.LLMProvider;
import com.zm.kilacraftAI.llm.cache.CacheMetricsCollector;
import com.zm.kilacraftAI.llm.cache.CacheStatsSnapshot;
import com.zm.kilacraftAI.service.health.SparkDataCollector;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /kila doctor：执行配置自检并输出分层诊断报告。
 * <p>
 * 游戏内仅保留分组摘要和异常项，控制台输出完整逐项结果与脱敏配置摘要。
 *
 * @author Zm_Mmm
 * @since 2026-07-30
 */
public final class DoctorCommand {

    private DoctorCommand() {
    }

    public static void handle(KilacraftAI plugin, CommandSender sender, String[] args) {
        LanguageManager lm = plugin.getLanguageManager();
        if (!PluginPermissionEnum.ADMIN_INFO.hasPermission(sender)) {
            sender.sendMessage(lm.getCommandDoctorNoPermission());
            return;
        }
        sender.sendMessage(lm.getCommandDoctorRunning());

        FoliaCompat.getIOPool().execute(() -> {
            try {
                List<CheckResult> checks = runSyncChecks(plugin);
                dumpConsole(plugin, checks);
                List<String> summary = buildInGameSummary(checks, lm);
                FoliaCompat.runTask(plugin, () -> summary.forEach(sender::sendMessage));
            } catch (Exception e) {
                PluginLoggerUtil.error("自检", I18nService.tr("自检执行失败: {}", e.getMessage()), e);
                FoliaCompat.runTask(plugin, () -> sender.sendMessage(lm.getCommandDoctorError()));
            }
        });
    }

    public static List<CheckResult> runSyncChecks(KilacraftAI plugin) {
        LanguageManager lm = plugin.getLanguageManager();
        List<CheckResult> checks = new ArrayList<>();
        ConfigManager cm = plugin.getConfigManager();
        AdminConfigManager admin = plugin.getAdminConfigManager();

        // ===== 基础 =====
        checks.add(checkDatabase(plugin, lm));
        checks.add(checkLlmPing(plugin, lm));
        checks.add(checkSpark(lm));
        checks.add(pass(Group.BASE, lm.getCommandDoctorCheckRuntimeEnv(), AdminSkillUtil.getServerPlatform() + (FoliaCompat.isFolia() ? " (Folia)" : "")));

        // ===== AI 能力 =====
        checks.add(pass(Group.AI, lm.getCommandDoctorCheckKnowledge(), boolLabel(lm, cm != null && cm.isKnowledgeEnabled())));
        checks.add(pass(Group.AI, lm.getCommandDoctorCheckEmbedding(), boolLabel(lm, cm != null && cm.isEmbeddingEnabled())));
        var pcm = plugin.getPersonalitiesConfigManager();
        int personaCount = pcm != null ? pcm.getAllPersonalities().size() : 0;
        checks.add(personaCount > 0 ? pass(Group.AI, lm.getCommandDoctorCheckPersona(), lm.replacePlaceholders(lm.getCommandDoctorPersonaLoaded(), "count", String.valueOf(personaCount))) : warn(Group.AI, lm.getCommandDoctorCheckPersona(), lm.getCommandDoctorPersonaNone()));
        checks.add(checkAgent(cm, lm));
        checks.add(checkCache(lm));
        checks.add(pass(Group.AI, lm.getCommandDoctorCheckProfile(), boolLabel(lm, cm != null && cm.isProfileInjectionEnabled())));
        // 守护系统自检：GuardianManager 是否初始化 + 当前在线活跃守护玩家数
        var guardianManager = plugin.getGuardianManager();
        checks.add(guardianManager != null ? pass(Group.AI, lm.getCommandDoctorCheckGuardianSystem(), lm.replacePlaceholders(lm.getCommandDoctorGuardianActive(), "count", String.valueOf(guardianManager.activeCount()))) : warn(Group.AI, lm.getCommandDoctorCheckGuardianSystem(), lm.getCommandDoctorDisabled()));
        checks.add(checkWatch(plugin, lm));
        checks.add(pass(Group.AI, lm.getCommandDoctorCheckCommandSkill(), boolLabel(lm, cm != null && cm.isCommandSkillEnabled())));
        checks.add(pass(Group.AI, lm.getCommandDoctorCheckPendingResume(), boolLabel(lm, cm != null && cm.isPendingResumeEnabled())));
        checks.add(pass(Group.AI, lm.getCommandDoctorCheckIsolation(), boolLabel(lm, cm != null && cm.isSecurityPlayerIsolationEnabled())));

        // ===== 可观测与集成 =====
        // admin.yml 的 health_guardian（服务器健康监控，非守护系统）
        boolean guardian = admin != null && admin.isGuardianEnabled();
        checks.add(guardian ? pass(Group.OBS, lm.getCommandDoctorCheckHealthGuardian(), lm.getCommandDoctorEnabled()) : warn(Group.OBS, lm.getCommandDoctorCheckHealthGuardian(), lm.getCommandDoctorDisabled()));
        boolean notify = admin != null && admin.isNotificationEnabled();
        int channels = admin != null ? admin.getNotificationChannels().size() : 0;
        checks.add(pass(Group.OBS, lm.getCommandDoctorCheckNotify(), notify ? lm.replacePlaceholders(lm.getCommandDoctorNotifyOn(), "count", String.valueOf(channels)) : lm.getCommandDoctorDisabled()));
        boolean thinking = admin != null && admin.isThinkingModelConfigured();
        checks.add(thinking ? pass(Group.OBS, lm.getCommandDoctorCheckThinking(), lm.replacePlaceholders(lm.getCommandDoctorThinkingOn(), "model", admin.getThinkingModelConfig().model())) : warn(Group.OBS, lm.getCommandDoctorCheckThinking(), lm.getCommandDoctorThinkingOff()));
        checks.add(pass(Group.OBS, lm.getCommandDoctorCheckGreeting(), boolLabel(lm, cm != null && cm.isGreetingEnabled())));
        checks.add(checkSuggestion(plugin, lm));
        checks.add(checkWebSearch(plugin, lm));
        checks.add(checkWebFetch(plugin, lm));

        return checks;
    }

    private static CheckResult checkDatabase(KilacraftAI plugin, LanguageManager lm) {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager == null) {
            return fail(Group.BASE, lm.getCommandDoctorCheckDatabase(), lm.getCommandDoctorDbNotInit());
        }
        String type = dbManager.getConfig() != null ? String.valueOf(dbManager.getConfig().getType()) : "?";
        try (Connection conn = dbManager.getConnection()) {
            return pass(Group.BASE, lm.getCommandDoctorCheckDatabase(), type + lm.getCommandDoctorDbOk());
        } catch (Exception e) {
            return fail(Group.BASE, lm.getCommandDoctorCheckDatabase(), type + lm.replacePlaceholders(lm.getCommandDoctorDbFail(), "error", e.getMessage()));
        }
    }

    /**
     * Spark 检测复用 SparkDataCollector（按 Spark API 取实例），兼容 leaf 等内置 Spark 的服务端。
     */
    private static CheckResult checkSpark(LanguageManager lm) {
        boolean available = new SparkDataCollector().isSparkAvailable();
        return available ? pass(Group.BASE, lm.getCommandDoctorCheckSpark(), lm.getCommandDoctorSparkYes()) : warn(Group.BASE, lm.getCommandDoctorCheckSpark(), lm.getCommandDoctorSparkNo());
    }

    private static CheckResult checkAgent(ConfigManager cm, LanguageManager lm) {
        if (cm == null || !cm.isAgentEnabled()) {
            return warn(Group.AI, lm.getCommandDoctorCheckAgent(), lm.getCommandDoctorDisabled());
        }
        boolean chat = cm.isAgentEnableChatListener();
        boolean cmd = cm.isAgentEnableCommand();
        String scope;
        if (chat && cmd) scope = lm.getCommandDoctorAgentScopeBoth();
        else if (chat) scope = lm.getCommandDoctorAgentScopeChat();
        else if (cmd) scope = lm.getCommandDoctorAgentScopeCmd();
        else scope = lm.getCommandDoctorAgentScopeNone();
        return pass(Group.AI, lm.getCommandDoctorCheckAgent(), lm.replacePlaceholders(lm.getCommandDoctorAgentOn(), "scope", scope));
    }

    private static CheckResult checkWatch(KilacraftAI plugin, LanguageManager lm) {
        WatchConfigManager wcm = plugin.getWatchConfigManager();
        if (wcm == null || !wcm.isEnabled()) {
            return warn(Group.AI, lm.getCommandDoctorCheckWatch(), lm.getCommandDoctorDisabled());
        }
        return pass(Group.AI, lm.getCommandDoctorCheckWatch(), lm.getCommandDoctorEnabled());
    }

    private static CheckResult checkSuggestion(KilacraftAI plugin, LanguageManager lm) {
        SuggestionConfigManager scm = plugin.getSuggestionConfigManager();
        if (scm == null || !scm.isEnabled()) {
            return warn(Group.OBS, lm.getCommandDoctorCheckSuggestion(), lm.getCommandDoctorDisabled());
        }
        return pass(Group.OBS, lm.getCommandDoctorCheckSuggestion(), lm.getCommandDoctorEnabled());
    }

    private static CheckResult checkWebFetch(KilacraftAI plugin, LanguageManager lm) {
        WebConfigManager wcm = plugin.getWebConfigManager();
        if (wcm == null || !wcm.isFetchEnabled()) {
            return warn(Group.OBS, lm.getCommandDoctorCheckWebFetch(), lm.getCommandDoctorDisabled());
        }
        return pass(Group.OBS, lm.getCommandDoctorCheckWebFetch(), lm.getCommandDoctorEnabled());
    }

    /**
     * Web 搜索自检：启用开关与供应商配置是两码事。
     * 启用但未配置任何供应商 API Key → FAIL（误导性配置：开了却用不了）；
     * 启用且至少一个供应商已配置 → PASS（列出已配置供应商）。
     */
    private static CheckResult checkWebSearch(KilacraftAI plugin, LanguageManager lm) {
        WebConfigManager wcm = plugin.getWebConfigManager();
        if (wcm == null || !wcm.isSearchEnabled()) {
            return warn(Group.OBS, lm.getCommandDoctorCheckWebSearch(), lm.getCommandDoctorDisabled());
        }
        String providers = collectConfiguredProviders(wcm);
        if (providers.isEmpty()) {
            return fail(Group.OBS, lm.getCommandDoctorCheckWebSearch(), lm.getCommandDoctorSearchNoProvider());
        }
        return pass(Group.OBS, lm.getCommandDoctorCheckWebSearch(), lm.replacePlaceholders(lm.getCommandDoctorSearchOn(), "provider", providers));
    }

    private static String collectConfiguredProviders(WebConfigManager wcm) {
        List<String> configured = new ArrayList<>();
        if (wcm.isTavilyConfigured()) configured.add("tavily");
        if (wcm.isBraveConfigured()) configured.add("brave");
        if (wcm.isExaConfigured()) configured.add("exa");
        if (wcm.isYouComConfigured()) configured.add("you_com");
        if (wcm.isZhipuConfigured()) configured.add("zhipu");
        if (wcm.isBaiduQianfanConfigured()) configured.add("baidu_qianfan");
        if (wcm.isVolcengineDoubaoConfigured()) configured.add("volcengine_doubao");
        if (wcm.isQiniuBaiduConfigured()) configured.add("qiniu_baidu");
        if (wcm.isAlibabaIqsConfigured()) configured.add("alibaba_iqs");
        return String.join(", ", configured);
    }

    /**
     * LLM 连通性诊测：向配置端点发极小请求，复用错误分类输出可读提示。
     */
    private static CheckResult checkLlmPing(KilacraftAI plugin, LanguageManager lm) {
        LLMProvider provider = plugin.getLlmManager() != null ? plugin.getLlmManager().getCurrentProvider() : null;
        if (provider == null) {
            return fail(Group.BASE, lm.getCommandDoctorCheckLlm(), lm.getCommandDoctorLlmNoProvider());
        }
        String model = plugin.getConfigManager() != null ? plugin.getConfigManager().getLlmModel() : "?";
        long start = System.currentTimeMillis();
        try {
            String result = provider.processRequestWithCustomSystemPrompt(I18nService.tr("请回复 ok"), "Doctor", null, silentHandler(), I18nService.tr("你是健康检查助手，只回复 ok。"), false, false, false, null).join();
            long latency = System.currentTimeMillis() - start;
            if (LLMResponseUtil.isErrorResponse(result)) {
                String hint = result.startsWith(LLMResponseUtil.ERROR_PREFIX) ? result.substring(LLMResponseUtil.ERROR_PREFIX.length()) : result;
                return fail(Group.BASE, lm.getCommandDoctorCheckLlm(), lm.replacePlaceholders(lm.getCommandDoctorLlmErrorLatency(), "error", hint, "latency", String.valueOf(latency)));
            }
            return pass(Group.BASE, lm.getCommandDoctorCheckLlm(), lm.replacePlaceholders(lm.getCommandDoctorLlmLatency(), "model", model, "latency", String.valueOf(latency)));
        } catch (Exception e) {
            return fail(Group.BASE, lm.getCommandDoctorCheckLlm(), e.getMessage());
        }
    }

    private static String boolLabel(LanguageManager lm, boolean enabled) {
        return enabled ? lm.getCommandDoctorEnabled() : lm.getCommandDoctorDisabled();
    }

    public static List<String> buildInGameSummary(List<CheckResult> checks, LanguageManager lm) {
        List<String> lines = new ArrayList<>();
        lines.add(lm.getCommandDoctorReportTitle());
        for (Group group : Group.values()) {
            List<CheckResult> groupChecks = checks.stream().filter(check -> check.group() == group).toList();
            long passCount = groupChecks.stream().filter(check -> check.status() == Status.PASS).count();
            long warnCount = groupChecks.stream().filter(check -> check.status() == Status.WARN).count();
            long failCount = groupChecks.stream().filter(check -> check.status() == Status.FAIL).count();
            lines.add(lm.replacePlaceholders(lm.getCommandDoctorGroupSummary(), "group", groupLabel(group, lm), "pass", String.valueOf(passCount), "warn", String.valueOf(warnCount), "fail", String.valueOf(failCount)));

            if (warnCount == 0 && failCount == 0) {
                lines.add(lm.getCommandDoctorGroupAllNormal());
                continue;
            }
            for (CheckResult check : groupChecks) {
                if (check.status() == Status.PASS) {
                    continue;
                }
                String template = check.status() == Status.FAIL ? lm.getCommandDoctorIssueFail() : lm.getCommandDoctorIssueWarn();
                lines.add(lm.replacePlaceholders(template, "name", check.name(), "detail", check.detail()));
            }
        }
        lines.add(lm.getCommandDoctorConsoleHint());
        return lines;
    }

    private static String groupLabel(Group g, LanguageManager lm) {
        return switch (g) {
            case BASE -> lm.getCommandDoctorGroupBase();
            case AI -> lm.getCommandDoctorGroupAi();
            case OBS -> lm.getCommandDoctorGroupObs();
        };
    }

    /**
     * 控制台保留完整逐项诊断和脱敏配置摘要，便于服主复制反馈。
     */
    private static void dumpConsole(KilacraftAI plugin, List<CheckResult> checks) {
        ConfigManager cm = plugin.getConfigManager();
        AdminConfigManager admin = plugin.getAdminConfigManager();
        CacheMetricsCollector cacheCollector = CacheMetricsCollector.getInstance();
        CacheStatsSnapshot cacheSnapshot = cacheCollector.getSnapshot();

        PluginLoggerUtil.info("自检", "========== Kilacraft-AI 自检报告 ==========");
        PluginLoggerUtil.info("自检", "版本：{}", plugin.getDescription().getVersion());
        PluginLoggerUtil.info("自检", "环境：{}{}", AdminSkillUtil.getServerPlatform(), FoliaCompat.isFolia() ? " (Folia)" : "");
        PluginLoggerUtil.info("自检", "----- 配置摘要 -----");
        if (cm != null) {
            PluginLoggerUtil.info("自检", "LLM API：{}", cm.getLlmApiUrl());
            PluginLoggerUtil.info("自检", "LLM 模型：{}", cm.getLlmModel());
            PluginLoggerUtil.info("自检", "LLM Key：{}", redactKey(cm.getLlmApiKey()));
            var pcm = plugin.getPersonalitiesConfigManager();
            PluginLoggerUtil.info("自检", "知识库：{} | Embedding：{} | 人格：{}套", cm.isKnowledgeEnabled(), cm.isEmbeddingEnabled(), pcm != null ? pcm.getAllPersonalities().size() : 0);
            PluginLoggerUtil.info("自检", "Agent：{} | 画像分析：{} | 守护系统：{}", cm.isAgentEnabled(), cm.isProfileInjectionEnabled(), plugin.getGuardianManager() != null ? plugin.getGuardianManager().activeCount() : "N/A");
            PluginLoggerUtil.info("自检", "命令技能：{} | 待确认续体：{} | 玩家隔离：{}", cm.isCommandSkillEnabled(), cm.isPendingResumeEnabled(), cm.isSecurityPlayerIsolationEnabled());
        }
        DatabaseManager db = plugin.getDatabaseManager();
        PluginLoggerUtil.info("自检", "数据库类型：{}", (db != null && db.getConfig() != null) ? db.getConfig().getType() : "?");
        PluginLoggerUtil.info("自检", "Spark：{}", new SparkDataCollector().isSparkAvailable() ? I18nService.tr("可用") : I18nService.tr("不可用"));
        PluginLoggerUtil.info("自检", "大模型缓存：命中率={} 节省率={} | 请求={} | 输入={} Token | 支持类型 {}/{}", formatPercent(cacheSnapshot.getGlobalHitRate()), formatPercent(cacheSnapshot.getGlobalSaveRate()), cacheSnapshot.totalRequests, cacheSnapshot.totalPromptTokens, cacheCollector.getSupportedTypeCount(), cacheCollector.getTotalTypeCount());
        if (admin != null) {
            PluginLoggerUtil.info("自检", "健康监控：{} | 外部通知：{}（{}渠道）| 推理模型：{} | 登录问候：{}", admin.isGuardianEnabled(), admin.isNotificationEnabled(), admin.getNotificationChannels().size(), admin.isThinkingModelConfigured() ? admin.getThinkingModelConfig().model() : "N/A", cm != null && cm.isGreetingEnabled());
        }
        WebConfigManager webcm = plugin.getWebConfigManager();
        String suggestionStatus = onOff(plugin.getSuggestionConfigManager() != null && plugin.getSuggestionConfigManager().isEnabled());
        String watchStatus = onOff(plugin.getWatchConfigManager() != null && plugin.getWatchConfigManager().isEnabled());
        String searchStatus = getSearchStatus(webcm);
        String fetchStatus = onOff(webcm != null && webcm.isFetchEnabled());
        PluginLoggerUtil.info("自检", "对话推荐：{} | 监听系统：{} | Web 搜索：{} | Web 抓取：{}", suggestionStatus, watchStatus, searchStatus, fetchStatus);

        PluginLoggerUtil.info("自检", "----- 诊断结果 -----");
        for (Group group : Group.values()) {
            PluginLoggerUtil.info("自检", "----- {} -----", I18nService.tr(group.label));
            checks.stream().filter(check -> check.group() == group).forEach(check -> PluginLoggerUtil.info("自检", "[{}] {}：{}", check.status().name(), check.name(), check.detail()));
        }
        PluginLoggerUtil.info("自检", "================================================");
    }

    private static String getSearchStatus(WebConfigManager webConfigManager) {
        if (webConfigManager == null || !webConfigManager.isSearchEnabled()) {
            return onOff(false);
        }
        String providers = collectConfiguredProviders(webConfigManager);
        return providers.isEmpty() ? I18nService.tr("启用（未配置供应商）") : I18nService.tr("启用（{}）", providers);
    }

    private static String formatPercent(double value) {
        return String.format("%.1f%%", value * 100);
    }

    private static CheckResult checkCache(LanguageManager lm) {
        CacheMetricsCollector collector = CacheMetricsCollector.getInstance();
        if (collector.getTotalRequests() == 0) {
            return warn(Group.AI, lm.getCommandDoctorCheckCache(), lm.getCommandDoctorCacheNoData());
        }

        double hitRate = collector.getGlobalHitRate();
        long totalRequests = collector.getTotalRequests();
        int supportedCount = collector.getSupportedTypeCount();
        int totalCount = collector.getTotalTypeCount();

        if (!collector.isAnyTypeSupported()) {
            return warn(Group.AI, lm.getCommandDoctorCheckCache(), lm.getCommandDoctorCacheUnsupported());
        }

        String status = hitRate >= 0.50 ? lm.getCommandDoctorStatusNormal() : (hitRate >= 0.30 ? lm.getCommandDoctorStatusLow() : lm.getCommandDoctorStatusAbnormal());
        return pass(Group.AI, lm.getCommandDoctorCheckCache(), lm.replacePlaceholders(lm.getCommandDoctorCacheOk(), "hitrate", String.format("%.1f%%", hitRate * 100), "status", status, "requests", String.valueOf(totalRequests), "supported", String.valueOf(supportedCount), "total", String.valueOf(totalCount)));
    }

    public static String redactKey(String key) {
        if (key == null || key.isEmpty()) return I18nService.tr("（未配置）");
        if (key.length() <= 4) return "****";
        return "****" + key.substring(key.length() - 4);
    }

    private static String onOff(boolean enabled) {
        return enabled ? "on" : "off";
    }

    private static AIResponseHandler silentHandler() {
        return new AIResponseHandler() {
            @Override
            public UUID getPlayerId() {
                return null;
            }

            @Override
            public String getPlayerName() {
                return "Doctor";
            }

            @Override
            public void showResponse(String response) {
                PluginLoggerUtil.debug("自检", "LLM ping 响应: {}", response);
            }

            @Override
            public void showStreamChunk(String chunk, String currentMessage) {
            }

            @Override
            public void handleError(String errorMessage) {
                PluginLoggerUtil.debug("自检", "LLM ping 错误: {}", errorMessage);
            }

            @Override
            public boolean isStreamOutputEnabled() {
                return false;
            }
        };
    }

    public static CheckResult pass(Group group, String name, String detail) {
        return new CheckResult(Status.PASS, group, name, detail);
    }

    public static CheckResult fail(Group group, String name, String detail) {
        return new CheckResult(Status.FAIL, group, name, detail);
    }

    public static CheckResult warn(Group group, String name, String detail) {
        return new CheckResult(Status.WARN, group, name, detail);
    }

    public enum Status {PASS, FAIL, WARN}

    public enum Group {
        BASE("基础"), AI("AI 能力"), OBS("可观测与集成");
        final String label;

        Group(String label) {
            this.label = label;
        }
    }

    public record CheckResult(Status status, Group group, String name, String detail) {
    }
}
