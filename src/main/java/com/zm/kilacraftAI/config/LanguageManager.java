package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.CacheCallTypeEnum;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/**
 * 语言配置管理器
 * 负责加载和管理 language.yml 中的所有系统提示文本
 *
 * @author Zm_Mmm
 * @since 2026-03-29
 */
public class LanguageManager {

    private final JavaPlugin plugin;
    @Getter
    private FileConfiguration config;

    @Getter
    private List<String> helpMessages;
    @Getter
    private String helpReload;
    @Getter
    private String helpClearSelf;
    @Getter
    private String helpClearOther;
    @Getter
    private String helpKnowledge;
    @Getter
    private String helpPersonalities;
    @Getter
    private String helpSuggestion;
    @Getter
    private String helpProfile;
    @Getter
    private String helpProfileSubcommands;
    @Getter
    private String helpNotify;
    @Getter
    private String helpUsage;
    @Getter
    private String helpHistory;
    @Getter
    private String helpMemory;
    @Getter
    private String helpSkills;
    @Getter
    private String helpRun;
    @Getter
    private String helpDoctor;
    @Getter
    private String helpAbout;
    @Getter
    private String helpTasks;
    @Getter
    private String helpCache;
    @Getter
    private String helpCacheReset;

    @Getter
    private String commandTasksNoPermission;
    @Getter
    private String commandTasksNotInit;
    @Getter
    private String commandTasksTitle;
    @Getter
    private String commandTasksEmpty;
    @Getter
    private String commandTaskHeader;
    @Getter
    private String commandTasksStatsLine;
    @Getter
    private String commandTasksLastRunLine;
    @Getter
    private String commandTasksErrorLine;
    @Getter
    private String commandTasksStatsCount;
    @Getter
    private String commandTasksNoTrigger;
    @Getter
    private String commandTasksNeverRun;
    @Getter
    private String commandTasksTimeAgo;
    @Getter
    private String commandSuggestionNotInit;
    @Getter
    private String commandSuggestionAlreadyOn;
    @Getter
    private String commandSuggestionAlreadyOff;
    @Getter
    private String commandSuggestionOn;
    @Getter
    private String commandSuggestionOff;
    @Getter
    private String commandSuggestionStatusOn;
    @Getter
    private String commandSuggestionStatusOff;
    @Getter
    private String commandSuggestionUsage;
    @Getter
    private String commandNotifyNoPermission;
    @Getter
    private String commandNotifyUsage;
    @Getter
    private String commandNotifyNotReady;
    @Getter
    private String commandNotifyTesting;
    @Getter
    private String commandNotifyResultTitle;
    @Getter
    private String commandNotifySendSuccess;
    @Getter
    private String commandNotifySendFailed;
    @Getter
    private String commandProfileNoPermission;
    @Getter
    private String commandProfileNoModel;
    @Getter
    private String commandProfileGuardianDisabled;
    @Getter
    private String commandProfileNoSpark;
    @Getter
    private String commandProfileInvalidDuration;
    @Getter
    private String commandProfileStarted;
    @Getter
    private String commandProfileWillReport;
    @Getter
    private String commandProfileAlreadyRunning;
    @Getter
    private String commandProfileNoRunning;
    @Getter
    private String commandProfileStopped;
    @Getter
    private String commandProfileStatus;
    @Getter
    private String commandProfileOperatorInfo;
    @Getter
    private String commandProfileAnalyzing;
    @Getter
    private String commandProfileUnknownSub;
    @Getter
    private String commandProfileUsage;
    @Getter
    private String commandProfileUploadFailed;
    @Getter
    private String commandProfileUploadFailReason;
    @Getter
    private String commandDoctorNoPermission;
    @Getter
    private String commandDoctorRunning;
    @Getter
    private String commandDoctorError;
    @Getter
    private String commandDoctorReportTitle;
    @Getter
    private String commandDoctorGroupBase;
    @Getter
    private String commandDoctorGroupAi;
    @Getter
    private String commandDoctorGroupObs;
    @Getter
    private String commandDoctorConsoleHint;
    @Getter
    private String commandDoctorGroupTitle;
    @Getter
    private String commandDoctorCheckLine;
    @Getter
    private String commandDoctorGroupSummary;
    @Getter
    private String commandDoctorGroupAllNormal;
    @Getter
    private String commandDoctorIssueWarn;
    @Getter
    private String commandDoctorIssueFail;
    @Getter
    private String commandDoctorCheckRuntimeEnv;
    @Getter
    private String commandDoctorCheckDatabase;
    @Getter
    private String commandDoctorCheckLlm;
    @Getter
    private String commandDoctorCheckSpark;
    @Getter
    private String commandDoctorCheckKnowledge;
    @Getter
    private String commandDoctorCheckEmbedding;
    @Getter
    private String commandDoctorCheckPersona;
    @Getter
    private String commandDoctorCheckAgent;
    @Getter
    private String commandDoctorCheckProfile;
    @Getter
    private String commandDoctorCheckCommandSkill;
    @Getter
    private String commandDoctorCheckPendingResume;
    @Getter
    private String commandDoctorCheckIsolation;
    @Getter
    private String commandDoctorCheckHealthGuardian;
    @Getter
    private String commandDoctorCheckNotify;
    @Getter
    private String commandDoctorCheckThinking;
    @Getter
    private String commandDoctorCheckGreeting;
    @Getter
    private String commandDoctorEnabled;
    @Getter
    private String commandDoctorDisabled;
    @Getter
    private String commandDoctorPersonaLoaded;
    @Getter
    private String commandDoctorPersonaNone;
    @Getter
    private String commandDoctorNotifyOn;
    @Getter
    private String commandDoctorThinkingOn;
    @Getter
    private String commandDoctorThinkingOff;
    @Getter
    private String commandDoctorDbNotInit;
    @Getter
    private String commandDoctorDbOk;
    @Getter
    private String commandDoctorDbFail;
    @Getter
    private String commandDoctorSparkYes;
    @Getter
    private String commandDoctorSparkNo;
    @Getter
    private String commandDoctorAgentScopeBoth;
    @Getter
    private String commandDoctorAgentScopeChat;
    @Getter
    private String commandDoctorAgentScopeCmd;
    @Getter
    private String commandDoctorAgentScopeNone;
    @Getter
    private String commandDoctorAgentOn;
    @Getter
    private String commandDoctorLlmNoProvider;
    @Getter
    private String commandDoctorLlmLatency;
    @Getter
    private String commandDoctorLlmErrorLatency;
    @Getter
    private String commandDoctorCheckSuggestion;
    @Getter
    private String commandDoctorCheckWatch;
    @Getter
    private String commandDoctorCheckWebSearch;
    @Getter
    private String commandDoctorCheckWebFetch;
    @Getter
    private String commandCacheNoPermission;
    @Getter
    private String commandCacheResetSuccess;
    @Getter
    private String commandCacheHeader;
    @Getter
    private String commandCacheNoData;
    @Getter
    private String commandCacheFooter;
    @Getter
    private String commandCacheAvgHit;
    @Getter
    private String commandCacheUnknownModel;
    @Getter
    private List<String> commandCacheTypeNames;
    @Getter
    private String commandDoctorSearchOn;
    @Getter
    private String commandDoctorSearchNoProvider;
    @Getter
    private String commandAboutNoPermission;
    @Getter
    private String commandAboutTitle;
    @Getter
    private String commandAboutCurrent;
    @Getter
    private String commandAboutChecking;
    @Getter
    private String commandAboutCheckFailed;
    @Getter
    private String commandAboutLatest;
    @Getter
    private String commandAboutReleaseNotes;
    @Getter
    private String commandAboutNewVersion;
    @Getter
    private String commandAboutUpToDate;
    @Getter
    private String commandAboutPublished;
    @Getter
    private String commandAboutDownload;
    @Getter
    private String commandSkillsNotInit;
    @Getter
    private String commandSkillsEmpty;
    @Getter
    private String commandSkillsTitle;
    @Getter
    private String commandSkillsPagination;
    @Getter
    private String commandRunPlayerOnly;
    @Getter
    private String commandRunUsage;
    @Getter
    private String commandRunNotInit;
    @Getter
    private String commandRunSkillNotFound;
    @Getter
    private String commandRunExecuting;
    @Getter
    private String commandRunError;
    @Getter
    private String commandRunTaskEmpty;
    @Getter
    private String commandUsageNotInit;
    @Getter
    private String commandUsageQueryFailed;
    @Getter
    private String commandUsageTitle;
    @Getter
    private String commandUsageGlobalTitle;
    @Getter
    private String commandUsageTurns;
    @Getter
    private String commandUsageSkills;
    @Getter
    private String commandUsageTopSkills;
    @Getter
    private String commandUsageSkillLine;
    @Getter
    private String commandUsageScope;
    @Getter
    private String commandUsageActivePlayers;
    @Getter
    private String commandUsagePlayerLine;
    @Getter
    private String commandUsagePlayerNotFound;
    @Getter
    private String commandHistoryNotInit;
    @Getter
    private String commandHistoryPlayerNotFound;
    @Getter
    private String commandHistoryQueryFailed;
    @Getter
    private String commandHistoryEmpty;
    @Getter
    private String commandHistoryTitle;
    @Getter
    private String commandHistoryRoleUser;
    @Getter
    private String commandHistoryEntryLine;
    @Getter
    private String commandHistoryPagination;
    @Getter
    private String commandMemoryNotInit;
    @Getter
    private String commandMemoryPlayerNotFound;
    @Getter
    private String commandMemoryQueryFailed;
    @Getter
    private String commandMemoryTitle;
    @Getter
    private String commandMemoryEmpty;
    @Getter
    private String commandMemoryFirstLogin;
    @Getter
    private String commandMemoryLastLogin;
    @Getter
    private String commandMemoryLoginCount;
    @Getter
    private String commandMemoryPlaytime;
    @Getter
    private String commandMemoryAnalyzed;
    @Getter
    private String commandMemoryNotAnalyzed;
    @Getter
    private String commandMemoryProfileField;
    @Getter
    private List<String> commandMemoryProfileLabels;
    @Getter
    private String commandMemoryDurationMinutes;
    @Getter
    private String commandMemoryDurationDays;
    @Getter
    private String commandMemoryDurationHours;
    @Getter
    private String commandPersonalitiesLoaded;
    @Getter
    private String permissionReload;
    @Getter
    private String permissionClearSelf;
    @Getter
    private String permissionClearOther;
    @Getter
    private String permissionKnowledge;
    @Getter
    private String permissionPersonalities;

    @Getter
    private String featureChatModeDisabled;
    @Getter
    private String featureChatModePlayerOnly;
    @Getter
    private String featureChatModeEnter;
    @Getter
    private String featureChatModeEnterSubtitle;
    @Getter
    private String featureChatModeExit;

    @Getter
    private String commandReloadSuccess;
    @Getter
    private String commandReloadFailure;
    @Getter
    private String commandClearSelfSuccess;
    @Getter
    private String commandClearOtherSuccess;
    @Getter
    private String commandClearConsoleHint;
    @Getter
    private String commandKnowledgeReloadSuccess;
    @Getter
    private String commandKnowledgeReloadFailure;
    @Getter
    private String commandPersonalitiesReloadSuccess;
    @Getter
    private String commandPersonalitiesReloadFailure;
    @Getter
    private String commandUnknownSubcommand;
    @Getter
    private String commandAvailableSubcommands;
    @Getter
    private String cooldownWarning;
    @Getter
    private String pluginCommandCooldownWarning;
    @Getter
    private String worldBannedHint;

    @Getter
    private String pluginCommandPlayerBlocked;
    @Getter
    private String pluginCommandInsufficientArgs;
    @Getter
    private String pluginCommandUsageExample;
    @Getter
    private String pluginCommandCallbackHint;
    @Getter
    private String pluginCommandCallbackExample;
    @Getter
    private String pluginCommandCallbackPlaceholderHint;
    @Getter
    private String pluginCommandInvalidUuid;
    @Getter
    private String pluginCommandUuidFormatHint;
    @Getter
    private String pluginCommandPersonalityNotFound;
    @Getter
    private String pluginCommandPersonalityListHint;
    @Getter
    private String pluginCommandError;

    @Getter
    private String logConfigReloaded;
    @Getter
    private String logKnowledgeReloaded;
    @Getter
    private String logPersonalitiesReloaded;
    @Getter
    private String logChatModeEntered;
    @Getter
    private String logChatModeExited;
    @Getter
    private String logClearSelfLogged;
    @Getter
    private String logClearOtherLogged;
    @Getter
    private String logPlayerCommandAttempt;
    @Getter
    private String logAiRequestError;
    @Getter
    private String logPluginCommandAiError;

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * 测试专用构造器：跳过插件文件加载，直接使用给定配置。传入空 YamlConfiguration 时，
     * 各 getter 回退到 getString 的内置默认值（中文），便于单测构造文案实例而不依赖磁盘 yml。
     */
    public LanguageManager(FileConfiguration config) {
        this.plugin = null;
        this.config = config;
        loadLanguageConfig();
    }

    /**
     * 加载语言配置文件
     */
    public void loadConfig() {
        // 根据当前语言选择配置文件（zh=language.yml, en=language_en.yml）
        String lang = ((KilacraftAI) plugin).getI18nService().getLanguage();
        String fileName = "zh".equals(lang) ? "language.yml" : "language_" + lang + ".yml";

        // 复制默认配置
        ConfigResourceUtil.saveDefaultResource((KilacraftAI) plugin, fileName);

        // 加载对应语言配置文件到内存
        try {
            File languageFile = new File(plugin.getDataFolder(), fileName);
            this.config = YamlConfiguration.loadConfiguration(languageFile);
        } catch (Exception e) {
            PluginLoggerUtil.error("语言配置", I18nService.tr("加载 {} 失败", fileName), e);
        }

        // 加载语言配置
        loadLanguageConfig();
    }

    /**
     * 从配置文件中读取所有语言项
     */
    private void loadLanguageConfig() {
        // 帮助消息
        this.helpMessages = config.getStringList("help.messages");
        this.helpReload = config.getString("help.reload", " §b/kila reload §8- §f热重载全部配置");
        this.helpClearSelf = config.getString("help.clear-self", " §b/kila clear §8- §f清除自己的对话历史");
        this.helpClearOther = config.getString("help.clear-other", " §b/kila clear §7<玩家名> §8- §f清除指定玩家的历史");
        this.helpKnowledge = config.getString("help.knowledge", " §b/kila knowledge reload §8- §f重载知识库");
        this.helpPersonalities = config.getString("help.personalities", " §b/kila personalities reload §8- §f重载人格配置");
        this.helpSuggestion = config.getString("help.suggestion", " §b/kila suggestion §7<on|off|status> §8- §f管理对话推荐");
        this.helpProfile = config.getString("help.profile", " §b/kila profile start §7[秒数] §8- §f启动性能采样 (30~120s)");
        this.helpProfileSubcommands = config.getString("help.profile-subcommands", " §b/kila profile stop | status §8- §f中断采样 / 查看状态");
        this.helpNotify = config.getString("help.notify", " §b/kila notify test §8- §f测试外部通知渠道");
        this.helpUsage = config.getString("help.usage", " §b/kila usage §7[玩家|all] [时间] §8- §f查看 AI 用量");
        this.helpHistory = config.getString("help.history", " §b/kila history §7[玩家] [页码] [-f] §8- §f查看对话历史（-f 显示完整内容）");
        this.helpMemory = config.getString("help.memory", " §b/kila memory §7[玩家] §8- §f查看玩家画像");
        this.helpSkills = config.getString("help.skills", " §b/kila skills §7[页码] §8- §f列出可用技能");
        this.helpRun = config.getString("help.run", " §b/kila run §7<技能> <提示词> §8- §f强制指定技能执行");
        this.helpDoctor = config.getString("help.doctor", " §b/kila doctor §8- §f配置自检");
        this.helpAbout = config.getString("help.about", " §b/kila about §8- §f版本与更新检查");
        this.helpTasks = config.getString("help.tasks", " §b/kila tasks §8- §f查看定时任务状态");
        this.helpCache = config.getString("help.cache", " §b/kila cache §7[页码] §8- §f查看大模型缓存统计");
        this.helpCacheReset = config.getString("help.cache-reset", " §b/kila cache reset §8- §f重置缓存统计");

        // 权限相关消息
        this.permissionReload = config.getString("permissions.reload", "§c你没有权限重载配置！");
        this.permissionClearSelf = config.getString("permissions.clear-self", "§c你没有权限清除自己的对话历史记录！");
        this.permissionClearOther = config.getString("permissions.clear-other", "§c你没有权限清除其他玩家的历史记录！");
        this.permissionKnowledge = config.getString("permissions.knowledge", "§c你没有权限管理知识库！");
        this.permissionPersonalities = config.getString("permissions.personalities", "§c你没有权限管理人格配置！");

        // 功能状态消息
        this.featureChatModeDisabled = config.getString("features.chat-mode-disabled", "§c连续对话模式已被禁用！");
        this.featureChatModePlayerOnly = config.getString("features.chat-mode-player-only", "§c只有玩家才能使用连续对话模式！");
        this.featureChatModeEnter = config.getString("features.chat-mode-enter", "§a已进入连续对话模式！现在你说的每句话都会发送给 Kilacraft-AI。");
        this.featureChatModeEnterSubtitle = config.getString("features.chat-mode-enter-subtitle", "§7输入 §b/kila chat§7 退出连续对话模式");
        this.featureChatModeExit = config.getString("features.chat-mode-exit", "§7已退出连续对话模式");

        // 命令执行结果消息
        this.commandReloadSuccess = config.getString("commands.reload-success", "§a配置已重载！");
        this.commandReloadFailure = config.getString("commands.reload-failure", "§c配置重载失败：");
        this.commandClearSelfSuccess = config.getString("commands.clear-self-success", "§a已清除你的对话历史记录！");
        this.commandClearOtherSuccess = config.getString("commands.clear-other-success", "§a已清除玩家 {player} 的对话历史记录！");
        this.commandClearConsoleHint = config.getString("commands.clear-console-hint", "§c请在命令中添加玩家名称来清除其历史记录：/kila clear <玩家名称>");
        this.commandKnowledgeReloadSuccess = config.getString("commands.knowledge-reload-success", "§a知识库已重载！");
        this.commandKnowledgeReloadFailure = config.getString("commands.knowledge-reload-failure", "§c知识库重载失败：");
        this.commandPersonalitiesReloadSuccess = config.getString("commands.personalities-reload-success", "§a人格配置已重载！");
        this.commandPersonalitiesReloadFailure = config.getString("commands.personalities-reload-failure", "§c人格配置重载失败：");
        this.commandUnknownSubcommand = config.getString("commands.unknown-subcommand", "§c未知的子命令：");
        this.commandAvailableSubcommands = config.getString("commands.available-subcommands", "§b可用子命令：reload");
        this.cooldownWarning = config.getString("commands.cooldown-warning", "§c请等待 {seconds} 秒后再试！");
        this.pluginCommandCooldownWarning = config.getString("commands.plugin-command-cooldown-warning", "§c玩家 {player} 正在冷却中，请等待 {seconds} 秒后再试！");
        this.worldBannedHint = config.getString("commands.world-banned-hint", "§c当前世界禁止使用 {ai_name}！");

        // 插件命令专用消息
        this.commandTasksNoPermission = config.getString("commands.tasks-no-permission", "§c你没有权限查看定时任务状态。");
        this.commandTasksNotInit = config.getString("commands.tasks-not-init", "§cTaskScheduler 未初始化。");
        this.commandTasksTitle = config.getString("commands.tasks-title", "§6[Kilacraft-AI] §f定时任务状态 §7（共 §e{n} §7个）");
        this.commandTasksEmpty = config.getString("commands.tasks-empty", "§7暂无已注册的定时任务。");
        this.commandTaskHeader = config.getString("commands.task-header", "{mark} §f{name} §8· §7{interval}");
        this.commandTasksStatsLine = config.getString("commands.tasks-stats-line", "  §7统计 §f{stats}");
        this.commandTasksLastRunLine = config.getString("commands.tasks-last-run-line", "  §7上次 §f{time}");
        this.commandTasksErrorLine = config.getString("commands.tasks-error-line", "  §7异常 §c{error}");
        this.commandTasksStatsCount = config.getString("commands.tasks-stats-count", "累计 §e{n} §7条");
        this.commandTasksNoTrigger = config.getString("commands.tasks-no-trigger", "§7暂无触发");
        this.commandTasksNeverRun = config.getString("commands.tasks-never-run", "§8未执行");
        this.commandTasksTimeAgo = config.getString("commands.tasks-time-ago", "{n}前");

        this.commandSuggestionNotInit = config.getString("commands.suggestion-not-init", "§c对话推荐系统未初始化！");
        this.commandSuggestionAlreadyOn = config.getString("commands.suggestion-already-on", "§7对话推荐已经是开启状态。");
        this.commandSuggestionAlreadyOff = config.getString("commands.suggestion-already-off", "§7对话推荐已经是关闭状态。");
        this.commandSuggestionOn = config.getString("commands.suggestion-on", "§a对话推荐已开启。AI 回复后将推荐你可能想问的问题。");
        this.commandSuggestionOff = config.getString("commands.suggestion-off", "§a对话推荐已关闭。");
        this.commandSuggestionStatusOn = config.getString("commands.suggestion-status-on", "§a对话推荐：开启 §7（/kila suggestion off 关闭）");
        this.commandSuggestionStatusOff = config.getString("commands.suggestion-status-off", "§c对话推荐：关闭 §7（/kila suggestion on 开启）");
        this.commandSuggestionUsage = config.getString("commands.suggestion-usage", " §b/kila suggestion §7<on|off|status> §8- §f管理对话推荐");
        this.commandNotifyNoPermission = config.getString("commands.notify-no-permission", "§c你没有权限使用通知测试功能。");
        this.commandNotifyUsage = config.getString("commands.notify-usage", "§7用法：/kila notify test");
        this.commandNotifyNotReady = config.getString("commands.notify-not-ready", "§c通知服务未启用或未配置任何渠道。");
        this.commandNotifyTesting = config.getString("commands.notify-testing", "§7正在测试 {count} 个通知渠道...");
        this.commandNotifyResultTitle = config.getString("commands.notify-result-title", "§f通知渠道测试结果：");
        this.commandNotifySendSuccess = config.getString("commands.notify-send-success", "发送成功");
        this.commandNotifySendFailed = config.getString("commands.notify-send-failed", "§c发送失败: {msg}");
        this.commandProfileNoPermission = config.getString("commands.profile-no-permission", "§c你没有权限使用性能采样功能。");
        this.commandProfileNoModel = config.getString("commands.profile-no-model", "§c服务器健康监控不可用：admin.yml 和 llm.yml 均未配置可用模型。请至少在 llm.yml 中填写有效的 llm.api_key。");
        this.commandProfileGuardianDisabled = config.getString("commands.profile-guardian-disabled", "§c服务器健康监控不可用：守护线程已禁用（admin.yml 中 health_guardian.enabled 为 false）。");
        this.commandProfileNoSpark = config.getString("commands.profile-no-spark", "§c服务器健康监控不可用：Spark 插件未安装或未加载。");
        this.commandProfileInvalidDuration = config.getString("commands.profile-invalid-duration", "§c无效的采样时长，请输入 30~120 之间的数字。");
        this.commandProfileStarted = config.getString("commands.profile-started", "§a已启动性能采样，时长 {seconds} 秒。");
        this.commandProfileWillReport = config.getString("commands.profile-will-report", "§7采样完成后将自动生成诊断报告。");
        this.commandProfileAlreadyRunning = config.getString("commands.profile-already-running", "§c已有采样正在进行中，请等待完成或使用 /kila profile stop 中断。");
        this.commandProfileNoRunning = config.getString("commands.profile-no-running", "§7当前没有正在进行的采样。");
        this.commandProfileStopped = config.getString("commands.profile-stopped", "§a采样已中断，数据已丢弃。");
        this.commandProfileStatus = config.getString("commands.profile-status", "§f当前采样状态：§e{status}");
        this.commandProfileOperatorInfo = config.getString("commands.profile-operator-info", "§7操作者：{name}，采样时长：{seconds}秒");
        this.commandProfileAnalyzing = config.getString("commands.profile-analyzing", "§7正在执行深度分析...");
        this.commandProfileUnknownSub = config.getString("commands.profile-unknown-sub", "§c未知的 profile 子命令：{cmd}");
        this.commandProfileUsage = config.getString("commands.profile-usage", "§7用法：/kila profile <start|stop|status>");
        this.commandProfileUploadFailed = config.getString("commands.profile-upload-failed", "§c采样数据上传失败（Spark 服务器超时），无法生成诊断报告。");
        this.commandProfileUploadFailReason = config.getString("commands.profile-upload-fail-reason", "§7可能原因：服务器网络无法访问 Spark 数据服务器。请稍后重试。");
        this.commandDoctorNoPermission = config.getString("commands.doctor-no-permission", "§c你没有权限使用此命令。");
        this.commandDoctorRunning = config.getString("commands.doctor-running", "§7正在执行自检...");
        this.commandDoctorError = config.getString("commands.doctor-error", "§c自检执行失败，详情见控制台。");
        this.commandDoctorReportTitle = config.getString("commands.doctor-report-title", "§6[Kilacraft-AI] §f自检报告");
        this.commandDoctorGroupBase = config.getString("commands.doctor-group-base", "基础");
        this.commandDoctorGroupAi = config.getString("commands.doctor-group-ai", "AI 能力");
        this.commandDoctorGroupObs = config.getString("commands.doctor-group-obs", "可观测与集成");
        this.commandDoctorConsoleHint = config.getString("commands.doctor-console-hint", "§7完整诊断详情已输出到控制台。");
        this.commandDoctorGroupTitle = config.getString("commands.doctor-group-title", "§e▌{group}");
        this.commandDoctorCheckLine = config.getString("commands.doctor-check-line", "{icon} §f{name}§7：§f{detail}");
        this.commandDoctorGroupSummary = config.getString("commands.doctor-group-summary", "§e▌§f{group} §8· §a通过 {pass} §8/ §e提醒 {warn} §8/ §c失败 {fail}");
        this.commandDoctorGroupAllNormal = config.getString("commands.doctor-group-all-normal", "  §a✓ §7全部正常");
        this.commandDoctorIssueWarn = config.getString("commands.doctor-issue-warn", "  §e⚠ §f{name}§7：{detail}");
        this.commandDoctorIssueFail = config.getString("commands.doctor-issue-fail", "  §c✗ §f{name}§7：{detail}");
        this.commandDoctorCheckRuntimeEnv = config.getString("commands.doctor-check-runtime-env", "运行环境");
        this.commandDoctorCheckDatabase = config.getString("commands.doctor-check-database", "数据库");
        this.commandDoctorCheckLlm = config.getString("commands.doctor-check-llm", "LLM 连通");
        this.commandDoctorCheckSpark = config.getString("commands.doctor-check-spark", "Spark");
        this.commandDoctorCheckKnowledge = config.getString("commands.doctor-check-knowledge", "知识库");
        this.commandDoctorCheckEmbedding = config.getString("commands.doctor-check-embedding", "Embedding");
        this.commandDoctorCheckPersona = config.getString("commands.doctor-check-persona", "人格");
        this.commandDoctorCheckAgent = config.getString("commands.doctor-check-agent", "Agent 能力");
        this.commandDoctorCheckProfile = config.getString("commands.doctor-check-profile", "画像分析");
        this.commandDoctorCheckCommandSkill = config.getString("commands.doctor-check-command-skill", "命令技能");
        this.commandDoctorCheckPendingResume = config.getString("commands.doctor-check-pending-resume", "待确认续体");
        this.commandDoctorCheckIsolation = config.getString("commands.doctor-check-isolation", "玩家数据隔离");
        this.commandDoctorCheckHealthGuardian = config.getString("commands.doctor-check-health-guardian", "健康监控");
        this.commandDoctorCheckNotify = config.getString("commands.doctor-check-notify", "外部通知");
        this.commandDoctorCheckThinking = config.getString("commands.doctor-check-thinking", "推理模型");
        this.commandDoctorCheckGreeting = config.getString("commands.doctor-check-greeting", "登录问候");
        this.commandDoctorEnabled = config.getString("commands.doctor-enabled", "启用");
        this.commandDoctorDisabled = config.getString("commands.doctor-disabled", "禁用");
        this.commandDoctorPersonaLoaded = config.getString("commands.doctor-persona-loaded", "已加载 {count} 套");
        this.commandDoctorPersonaNone = config.getString("commands.doctor-persona-none", "未加载");
        this.commandDoctorNotifyOn = config.getString("commands.doctor-notify-on", "启用（{count} 个渠道）");
        this.commandDoctorThinkingOn = config.getString("commands.doctor-thinking-on", "已配置（{model}）");
        this.commandDoctorThinkingOff = config.getString("commands.doctor-thinking-off", "未配置");
        this.commandDoctorDbNotInit = config.getString("commands.doctor-db-not-init", "未初始化");
        this.commandDoctorDbOk = config.getString("commands.doctor-db-ok", "，连接正常");
        this.commandDoctorDbFail = config.getString("commands.doctor-db-fail", "，连接失败：{error}");
        this.commandDoctorSparkYes = config.getString("commands.doctor-spark-yes", "可用");
        this.commandDoctorSparkNo = config.getString("commands.doctor-spark-no", "不可用");
        this.commandDoctorAgentScopeBoth = config.getString("commands.doctor-agent-scope-both", "聊天/命令");
        this.commandDoctorAgentScopeChat = config.getString("commands.doctor-agent-scope-chat", "仅聊天");
        this.commandDoctorAgentScopeCmd = config.getString("commands.doctor-agent-scope-cmd", "仅命令");
        this.commandDoctorAgentScopeNone = config.getString("commands.doctor-agent-scope-none", "无入口");
        this.commandDoctorAgentOn = config.getString("commands.doctor-agent-on", "启用（{scope}）");
        this.commandDoctorLlmNoProvider = config.getString("commands.doctor-llm-no-provider", "未配置 LLM 提供商");
        this.commandDoctorLlmLatency = config.getString("commands.doctor-llm-latency", "{model}（{latency}ms）");
        this.commandDoctorLlmErrorLatency = config.getString("commands.doctor-llm-error-latency", "{error}（{latency}ms）");
        this.commandDoctorCheckSuggestion = config.getString("commands.doctor-check-suggestion", "对话推荐");
        this.commandDoctorCheckWatch = config.getString("commands.doctor-check-watch", "监听系统");
        this.commandDoctorCheckWebSearch = config.getString("commands.doctor-check-web-search", "Web 搜索");
        this.commandDoctorCheckWebFetch = config.getString("commands.doctor-check-web-fetch", "Web 抓取");
        this.commandDoctorSearchOn = config.getString("commands.doctor-search-on", "启用（{provider}）");
        this.commandDoctorSearchNoProvider = config.getString("commands.doctor-search-no-provider", "启用，但未配置任何搜索供应商");
        this.commandAboutNoPermission = config.getString("commands.about-no-permission", "§c你没有权限使用此命令。");
        this.commandAboutTitle = config.getString("commands.about-title", "§6[Kilacraft-AI] §f版本信息");
        this.commandAboutCurrent = config.getString("commands.about-current", "§7当前版本：§fv{ver}");
        this.commandAboutChecking = config.getString("commands.about-checking", "§7正在检查更新...");
        this.commandAboutCheckFailed = config.getString("commands.about-check-failed", "§7更新检查失败，无法连接到发布源。");
        this.commandAboutLatest = config.getString("commands.about-latest", "§7最新版本：§f{ver}");
        this.commandAboutReleaseNotes = config.getString("commands.about-release-notes", "§7版本说明：§f{notes}");
        this.commandAboutNewVersion = config.getString("commands.about-new-version", "§e发现新版本！");
        this.commandAboutUpToDate = config.getString("commands.about-up-to-date", "§a已是最新版本。");
        this.commandAboutPublished = config.getString("commands.about-published", "§7发布日期：§f{date}");
        this.commandAboutDownload = config.getString("commands.about-download", "§7下载地址：§b{url}");
        this.commandSkillsNotInit = config.getString("commands.skills-not-init", "§c技能系统未初始化。");
        this.commandSkillsEmpty = config.getString("commands.skills-empty", "§7当前没有你可用的技能。");
        this.commandSkillsTitle = config.getString("commands.skills-title", "§6[Kilacraft-AI] §f可用技能（第 {page}/{total} 页）");
        this.commandSkillsPagination = config.getString("commands.skills-pagination", "§7共 {total} 页，使用 /kila skills <页码> 翻页");
        this.commandRunPlayerOnly = config.getString("commands.run-player-only", "§c该命令仅限玩家使用。");
        this.commandRunUsage = config.getString("commands.run-usage", "§7用法：/kila run <技能名> <提示词>");
        this.commandRunNotInit = config.getString("commands.run-not-init", "§c技能系统未初始化。");
        this.commandRunSkillNotFound = config.getString("commands.run-skill-not-found", "§c技能不存在或无权使用：{skill}");
        this.commandRunExecuting = config.getString("commands.run-executing", "§7正在执行 {skill}...");
        this.commandRunError = config.getString("commands.run-error", "§c技能执行异常，请查看控制台日志。");
        this.commandRunTaskEmpty = config.getString("commands.run-task-empty", "§7任务执行完成，无输出。");
        this.commandUsageNotInit = config.getString("commands.usage-not-init", "§c数据库未初始化。");
        this.commandUsageQueryFailed = config.getString("commands.usage-query-failed", "§c查询用量统计失败。");
        this.commandUsageTitle = config.getString("commands.usage-title", "§6[Kilacraft-AI] §f{player} 的用量（近 {range}）");
        this.commandUsageGlobalTitle = config.getString("commands.usage-global-title", "§6[Kilacraft-AI] §f全服用量（近 {range}）");
        this.commandUsageTurns = config.getString("commands.usage-turns", "§7对话轮数：§f{value}");
        this.commandUsageSkills = config.getString("commands.usage-skills", "§7技能调用：§f{total}（§a成功 {ok} §c/ 失败 {fail}§7，成功率 {rate}%）");
        this.commandUsageTopSkills = config.getString("commands.usage-top-skills", "§bTop 技能：");
        this.commandUsageSkillLine = config.getString("commands.usage-skill-line", "§f  {skill}§7/{action} §a{count}");
        this.commandUsageScope = config.getString("commands.usage-scope", "§7口径：活跃度（对话轮数 + 技能调用数），非费用。");
        this.commandUsageActivePlayers = config.getString("commands.usage-active-players", "§b活跃玩家 Top：");
        this.commandUsagePlayerLine = config.getString("commands.usage-player-line", "§f  {name} §a{count} 次");
        this.commandUsagePlayerNotFound = config.getString("commands.usage-player-not-found", "§c未找到玩家：{player}");
        this.commandHistoryNotInit = config.getString("commands.history-not-init", "§c数据库未初始化。");
        this.commandHistoryPlayerNotFound = config.getString("commands.history-player-not-found", "§c未找到玩家：{player}");
        this.commandHistoryQueryFailed = config.getString("commands.history-query-failed", "§c查询对话历史失败。");
        this.commandHistoryEmpty = config.getString("commands.history-empty", "§7{player} 暂无对话历史。");
        this.commandHistoryTitle = config.getString("commands.history-title", "§6[Kilacraft-AI] §f{player} 的对话历史（第 {page}/{total} 页）");
        this.commandHistoryRoleUser = config.getString("commands.history-role-user", "§a你");
        this.commandHistoryEntryLine = config.getString("commands.history-entry-line", "§7[{time}] {role}§7：§f{content}");
        this.commandHistoryPagination = config.getString("commands.history-pagination", "§7共 {total} 页，使用 /kila history [玩家] <页码> 翻页");
        this.commandMemoryNotInit = config.getString("commands.memory-not-init", "§c数据库未初始化。");
        this.commandMemoryPlayerNotFound = config.getString("commands.memory-player-not-found", "§c未找到玩家：{player}");
        this.commandMemoryQueryFailed = config.getString("commands.memory-query-failed", "§c查询玩家画像失败。");
        this.commandMemoryTitle = config.getString("commands.memory-title", "§6[Kilacraft-AI] §f{player} 的画像");
        this.commandMemoryEmpty = config.getString("commands.memory-empty", "§7暂无画像数据。");
        this.commandMemoryFirstLogin = config.getString("commands.memory-first-login", "§7首次登录：§f{value}");
        this.commandMemoryLastLogin = config.getString("commands.memory-last-login", "§7最近登录：§f{value}");
        this.commandMemoryLoginCount = config.getString("commands.memory-login-count", "§7登录次数：§f{value}");
        this.commandMemoryPlaytime = config.getString("commands.memory-playtime", "§7累计在线：§f{value}");
        this.commandMemoryAnalyzed = config.getString("commands.memory-analyzed", "§7AI 画像：§a已分析（{value}）");
        this.commandMemoryNotAnalyzed = config.getString("commands.memory-not-analyzed", "§7AI 画像：§7暂未分析");
        this.commandMemoryProfileField = config.getString("commands.memory-profile-field", "§7{label}：§f{value}");
        this.commandMemoryProfileLabels = config.getStringList("commands.memory-profile-labels");
        this.commandMemoryDurationMinutes = config.getString("commands.memory-duration-minutes", "{n}分");
        this.commandMemoryDurationDays = config.getString("commands.memory-duration-days", "{n}天{m}小时");
        this.commandMemoryDurationHours = config.getString("commands.memory-duration-hours", "{n}小时{m}分");
        this.commandPersonalitiesLoaded = config.getString("commands.personalities-loaded", "§7当前共加载 {count} 个人格");
        this.pluginCommandPlayerBlocked = config.getString("plugins-command.player-blocked", "§c请使用 /kila <消息>");
        this.pluginCommandInsufficientArgs = config.getString("plugins-command.insufficient-args", "§c参数不足！使用方法：/kila plugins <人格> <内容> <玩家 UUID> [回调命令...]");
        this.pluginCommandUsageExample = config.getString("plugins-command.usage-example", "§b示例：/kila plugins 严厉教师 你好 069a79f4-44e9-4726-a5be-fca90e38aaf5");
        this.pluginCommandCallbackHint = config.getString("plugins-command.callback-hint", "§7可选：在末尾添加回调命令，AI 完成后自动执行");
        this.pluginCommandCallbackExample = config.getString("plugins-command.callback-example", "§b带回调：/kila plugins mm_ai 你好 UUID testai handleAI {response} mm_ai");
        this.pluginCommandCallbackPlaceholderHint = config.getString("plugins-command.callback-placeholder-hint", "§7{response} 会被替换为实际的 AI 回复内容");
        this.pluginCommandInvalidUuid = config.getString("plugins-command.invalid-uuid", "§c无效的玩家 UUID 格式：");
        this.pluginCommandUuidFormatHint = config.getString("plugins-command.uuid-format-hint", "§b请确保 UUID 格式正确，例如：069a79f4-44e9-4726-a5be-fca90e38aaf5");
        this.pluginCommandPersonalityNotFound = config.getString("plugins-command.personality-not-found", "§c未找到人格配置：");
        this.pluginCommandPersonalityListHint = config.getString("plugins-command.personality-list-hint", "§b可用的人格列表：");
        this.pluginCommandError = config.getString("plugins-command.error", "§c发生错误：");

        // 大模型缓存命中率统计
        this.commandCacheNoPermission = config.getString("commands.cache-no-permission", "§c你没有权限查看大模型缓存统计。");
        this.commandCacheResetSuccess = config.getString("commands.cache-reset-success", "§a大模型缓存统计已重置。");
        this.commandCacheHeader = config.getString("commands.cache-header", "§6▌大模型缓存命中率 §7({model})");
        this.commandCacheNoData = config.getString("commands.cache-no-data", "§7暂无数据。至少需要一次大模型调用才会产生统计。");
        this.commandCacheFooter = config.getString("commands.cache-footer", "重启清零 · 控制台查看详情");
        this.commandCacheAvgHit = config.getString("commands.cache-avg-hit", "平均命中率");
        this.commandCacheUnknownModel = config.getString("commands.cache-unknown-model", "未知模型");
        this.commandCacheTypeNames = config.getStringList("commands.cache-type-names");

        // 日志消息
        this.logConfigReloaded = config.getString("logs.config-reloaded", "配置已由 {sender} 重载");
        this.logKnowledgeReloaded = config.getString("logs.knowledge-reloaded", "知识库已由 {sender} 重载");
        this.logPersonalitiesReloaded = config.getString("logs.personalities-reloaded", "人格配置已由 {sender} 重载");
        this.logChatModeEntered = config.getString("logs.chat-mode-entered", "玩家 {player} 已进入连续对话模式");
        this.logChatModeExited = config.getString("logs.chat-mode-exited", "玩家 {player} 已退出连续对话模式");
        this.logClearSelfLogged = config.getString("logs.clear-self-logged", "玩家 {player} 已清除对话历史记录");
        this.logClearOtherLogged = config.getString("logs.clear-other-logged", "{sender} 已清除玩家 {player} 的对话历史记录");
        this.logPlayerCommandAttempt = config.getString("logs.player-command-attempt", "玩家 {player} 尝试执行控制台专用命令 /kila plugins");
        this.logAiRequestError = config.getString("logs.ai-request-error", "AI 请求发生错误：");
        this.logPluginCommandAiError = config.getString("logs.plugin-command-ai-error", "插件命令 AI 请求发生错误：");
    }

    public String getCommandCacheTypeName(CacheCallTypeEnum type) {
        int index = type.ordinal();
        if (commandCacheTypeNames != null && index < commandCacheTypeNames.size()) {
            return commandCacheTypeNames.get(index);
        }
        return type.getDisplayName();
    }

    /**
     * 替换消息中的占位符
     *
     * @param message     原始消息
     * @param placeholder 占位符名称（不含花括号）
     * @param value       替换值
     * @return 替换后的消息
     */
    public String replacePlaceholder(String message, String placeholder, String value) {
        if (message == null || value == null) {
            return message;
        }
        return message.replace("{" + placeholder + "}", value);
    }

    /**
     * 替换多个占位符
     *
     * @param message      原始消息
     * @param placeholders 占位符和值的数组，格式为：{placeholder1, value1, placeholder2, value2, ...}
     * @return 替换后的消息
     */
    public String replacePlaceholders(String message, String... placeholders) {
        if (message == null || placeholders == null || placeholders.length % 2 != 0) {
            return message;
        }

        String result = message;
        for (int i = 0; i < placeholders.length; i += 2) {
            result = replacePlaceholder(result, placeholders[i], placeholders[i + 1]);
        }
        return result;
    }
}
