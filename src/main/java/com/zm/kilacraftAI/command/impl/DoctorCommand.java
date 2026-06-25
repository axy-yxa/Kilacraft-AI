package com.zm.kilacraftAI.command.impl;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.AdminSkillUtil;
import com.zm.kilacraftAI.common.util.LLMResponseUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.AdminConfigManager;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.LLMProvider;
import com.zm.kilacraftAI.service.health.SparkDataCollector;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /kila doctor：配置自检（管理员专用，admin.info）。
 * 17 项诊断分三组：基础（DB/LLM/Spark/环境）、AI 能力（知识库/Embedding/人格/Agent/画像/挂机/命令技能/续体/隔离）、
 * 可观测与集成（守护/通知/推理模型/问候）。
 * 游戏内输出分组诊断清单；控制台输出脱敏配置详情（不重复诊断结论，便于反馈）。
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
                dumpConsole(plugin);
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
        checks.add(pass(Group.AI, lm.getCommandDoctorCheckProfile(), boolLabel(lm, cm != null && cm.isProfileInjectionEnabled())));
        checks.add(pass(Group.AI, lm.getCommandDoctorCheckAfk(), boolLabel(lm, cm != null && cm.isAfkTaskEnabled())));
        checks.add(pass(Group.AI, lm.getCommandDoctorCheckCommandSkill(), boolLabel(lm, cm != null && cm.isCommandSkillEnabled())));
        checks.add(pass(Group.AI, lm.getCommandDoctorCheckPendingResume(), boolLabel(lm, cm != null && cm.isPendingResumeEnabled())));
        checks.add(pass(Group.AI, lm.getCommandDoctorCheckIsolation(), boolLabel(lm, cm != null && cm.isSecurityPlayerIsolationEnabled())));

        // ===== 可观测与集成 =====
        boolean guardian = admin != null && admin.isGuardianEnabled();
        checks.add(guardian ? pass(Group.OBS, lm.getCommandDoctorCheckGuardian(), lm.getCommandDoctorEnabled()) : warn(Group.OBS, lm.getCommandDoctorCheckGuardian(), lm.getCommandDoctorDisabled()));
        boolean notify = admin != null && admin.isNotificationEnabled();
        int channels = admin != null ? admin.getNotificationChannels().size() : 0;
        checks.add(pass(Group.OBS, lm.getCommandDoctorCheckNotify(), notify ? lm.replacePlaceholders(lm.getCommandDoctorNotifyOn(), "count", String.valueOf(channels)) : lm.getCommandDoctorDisabled()));
        boolean thinking = admin != null && admin.isThinkingModelConfigured();
        checks.add(thinking ? pass(Group.OBS, lm.getCommandDoctorCheckThinking(), lm.replacePlaceholders(lm.getCommandDoctorThinkingOn(), "model", admin.getThinkingModelConfig().model())) : warn(Group.OBS, lm.getCommandDoctorCheckThinking(), lm.getCommandDoctorThinkingOff()));
        checks.add(pass(Group.OBS, lm.getCommandDoctorCheckGreeting(), boolLabel(lm, cm != null && cm.isGreetingEnabled())));

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
            String result = provider.processRequestWithCustomSystemPrompt(I18nService.tr("请回复 ok"), "Doctor", null, silentHandler(), I18nService.tr("你是健康检查助手，只回复 ok。"), false, false, false).join();
            long latency = System.currentTimeMillis() - start;
            if (LLMResponseUtil.isErrorResponse(result)) {
                String hint = result.startsWith(LLMResponseUtil.ERROR_PREFIX) ? result.substring(LLMResponseUtil.ERROR_PREFIX.length()) : result;
                return fail(Group.BASE, lm.getCommandDoctorCheckLlm(), hint + "（" + latency + "ms）");
            }
            return pass(Group.BASE, lm.getCommandDoctorCheckLlm(), model + "（" + latency + "ms）");
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
        Group current = null;
        for (CheckResult c : checks) {
            if (c.group() != current) {
                current = c.group();
                lines.add("§e▌" + groupLabel(c.group(), lm));
            }
            String icon = switch (c.status()) {
                case PASS -> "§a✅";
                case FAIL -> "§c✗";
                case WARN -> "§e⚠";
            };
            lines.add(icon + " §f" + c.name() + "§7：§f" + c.detail());
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
     * 控制台输出脱敏配置详情（不重复游戏内诊断结论，便于服主复制反馈）。
     */
    private static void dumpConsole(KilacraftAI plugin) {
        ConfigManager cm = plugin.getConfigManager();
        AdminConfigManager admin = plugin.getAdminConfigManager();
        PluginLoggerUtil.info("自检", "========== Kilacraft-AI 配置详情 ==========");
        PluginLoggerUtil.info("自检", "版本：{}", plugin.getDescription().getVersion());
        PluginLoggerUtil.info("自检", "环境：{}{}", AdminSkillUtil.getServerPlatform(), FoliaCompat.isFolia() ? " (Folia)" : "");
        PluginLoggerUtil.info("自检", "----- {}", I18nService.tr(Group.BASE.label));
        if (cm != null) {
            PluginLoggerUtil.info("自检", "LLM API：{}", cm.getLlmApiUrl());
            PluginLoggerUtil.info("自检", "LLM 模型：{}", cm.getLlmModel());
            PluginLoggerUtil.info("自检", "LLM Key：{}", redactKey(cm.getLlmApiKey()));
        }
        DatabaseManager db = plugin.getDatabaseManager();
        PluginLoggerUtil.info("自检", "数据库类型：{}", (db != null && db.getConfig() != null) ? db.getConfig().getType() : "?");
        PluginLoggerUtil.info("自检", "Spark：{}", new SparkDataCollector().isSparkAvailable() ? I18nService.tr("可用") : I18nService.tr("不可用"));
        PluginLoggerUtil.info("自检", "----- {}", I18nService.tr(Group.AI.label));
        if (cm != null) {
            var pcm = plugin.getPersonalitiesConfigManager();
            PluginLoggerUtil.info("自检", "知识库：{} | Embedding：{} | 人格：{}套", cm.isKnowledgeEnabled(), cm.isEmbeddingEnabled(), pcm != null ? pcm.getAllPersonalities().size() : 0);
            PluginLoggerUtil.info("自检", "Agent：{} | 画像分析：{} | 挂机任务：{}", cm.isAgentEnabled(), cm.isProfileInjectionEnabled(), cm.isAfkTaskEnabled());
            PluginLoggerUtil.info("自检", "命令技能：{} | 待确认续体：{} | 玩家隔离：{}", cm.isCommandSkillEnabled(), cm.isPendingResumeEnabled(), cm.isSecurityPlayerIsolationEnabled());
        }
        PluginLoggerUtil.info("自检", "----- {}", I18nService.tr(Group.OBS.label));
        if (admin != null) {
            PluginLoggerUtil.info("自检", "健康守护：{} | 外部通知：{}（{}渠道）| 推理模型：{} | 登录问候：{}", admin.isGuardianEnabled(), admin.isNotificationEnabled(), admin.getNotificationChannels().size(), admin.isThinkingModelConfigured() ? admin.getThinkingModelConfig().model() : "N/A", cm != null && cm.isGreetingEnabled());
        }
        PluginLoggerUtil.info("自检", "================================================");
    }

    public static String redactKey(String key) {
        if (key == null || key.isEmpty()) return I18nService.tr("（未配置）");
        if (key.length() <= 4) return "****";
        return "****" + key.substring(key.length() - 4);
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
