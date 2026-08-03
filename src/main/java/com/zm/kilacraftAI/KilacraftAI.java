package com.zm.kilacraftAI;

import com.zm.kilacraftAI.command.KilacraftCommand;
import com.zm.kilacraftAI.command.TabCompleter;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.*;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.model.DatabaseConfig;
import com.zm.kilacraftAI.db.service.ConversationPersistenceService;
import com.zm.kilacraftAI.db.service.DataCleanupService;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.i18n.TextProcessorFactory;
import com.zm.kilacraftAI.listener.AdminListener;
import com.zm.kilacraftAI.listener.ChatListener;
import com.zm.kilacraftAI.listener.PrivateChatListener;
import com.zm.kilacraftAI.listener.TpaListener;
import com.zm.kilacraftAI.llm.LLMManager;
import com.zm.kilacraftAI.metrics.MetricsBootstrap;
import com.zm.kilacraftAI.metrics.MetricsCollector;
import com.zm.kilacraftAI.model.profile.SocialGraph;
import com.zm.kilacraftAI.scheduler.ManagedTask;
import com.zm.kilacraftAI.scheduler.TaskScheduler;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.service.event.EventCollector;
import com.zm.kilacraftAI.service.event.MarketEventCollector;
import com.zm.kilacraftAI.service.event.OfflineEventAggregator;
import com.zm.kilacraftAI.service.greeting.LoginGreetingHandler;
import com.zm.kilacraftAI.service.health.ServerHealthGuardian;
import com.zm.kilacraftAI.service.health.SparkDataCollector;
import com.zm.kilacraftAI.service.knowledge.EmbeddingService;
import com.zm.kilacraftAI.service.knowledge.InternalEnumRegistry;
import com.zm.kilacraftAI.service.knowledge.KnowledgeBaseManager;
import com.zm.kilacraftAI.service.knowledge.KnowledgeRetriever;
import com.zm.kilacraftAI.service.notification.NotificationService;
import com.zm.kilacraftAI.service.output.AIResponsePipeline;
import com.zm.kilacraftAI.service.output.SoundEffectManager;
import com.zm.kilacraftAI.service.output.StreamOutputManager;
import com.zm.kilacraftAI.service.playerwatch.PlayerWatchService;
import com.zm.kilacraftAI.service.profile.ProfileAnalysisService;
import com.zm.kilacraftAI.service.profile.ProfileEventCollector;
import com.zm.kilacraftAI.service.profile.ProfileManager;
import com.zm.kilacraftAI.service.profile.SocialRelationExtractor;
import com.zm.kilacraftAI.service.suggestion.SuggestionManager;
import com.zm.kilacraftAI.service.suggestion.SuggestionService;
import com.zm.kilacraftAI.service.translate.ItemTranslator;
import com.zm.kilacraftAI.service.update.UpdateChecker;
import com.zm.kilacraftAI.service.watch.WatchService;
import com.zm.kilacraftAI.skills.admin.AuditLogSkill;
import com.zm.kilacraftAI.skills.admin.PlayerAnalysisSkill;
import com.zm.kilacraftAI.skills.admin.ServerHealthSkill;
import com.zm.kilacraftAI.skills.admin.VersionInfoSkill;
import com.zm.kilacraftAI.skills.bukkit.BukkitFXSkill;
import com.zm.kilacraftAI.skills.bukkit.BukkitPlayerInfoSkill;
import com.zm.kilacraftAI.skills.bukkit.BukkitPlayerInventorySkill;
import com.zm.kilacraftAI.skills.bukkit.BukkitPlayerStatusSkill;
import com.zm.kilacraftAI.skills.bukkit.BukkitServerSkill;
import com.zm.kilacraftAI.skills.bukkit.BukkitStatsSkill;
import com.zm.kilacraftAI.skills.bukkit.BukkitWorldSkill;
import com.zm.kilacraftAI.skills.cmi.CMISkill;
import com.zm.kilacraftAI.skills.command.CommandSkill;
import com.zm.kilacraftAI.skills.framework.SkillIntentRecognizer;
import com.zm.kilacraftAI.skills.framework.SkillManager;
import com.zm.kilacraftAI.skills.framework.SkillRegistry;
import com.zm.kilacraftAI.skills.framework.SkillSecurityFilter;
import com.zm.kilacraftAI.skills.framework.resume.PendingResumeManager;
import com.zm.kilacraftAI.skills.framework.task.LLMOutputCoordinator;
import com.zm.kilacraftAI.skills.globalmarketplus.MarketActionSkill;
import com.zm.kilacraftAI.skills.globalmarketplus.MarketQuerySkill;
import com.zm.kilacraftAI.skills.playerwatch.PlayerWatchSkill;
import com.zm.kilacraftAI.skills.utility.UtilitySkill;
import com.zm.kilacraftAI.skills.watch.WatchSkill;
import com.zm.kilacraftAI.skills.webfetch.WebFetchSkill;
import com.zm.kilacraftAI.skills.websearch.WebSearchSkill;
import lombok.Getter;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Logger;

/**
 * 插件入口
 *
 * @author Zm_Mmm
 * @since 2026-03-24
 */
@Getter
public final class KilacraftAI extends JavaPlugin {

    @Getter
    private static KilacraftAI instance;
    private ConfigManager configManager;
    @Getter
    private LanguageManager languageManager;
    @Getter
    private PersonalitiesConfigManager personalitiesConfigManager;
    @Getter
    private SkillConfigManager skillConfigManager;
    @Getter
    private IntentPromptConfigManager intentPromptConfigManager;
    @Getter
    private DatabaseConfigManager databaseConfigManager;
    @Getter
    private DatabaseManager databaseManager;

    /**
     * 玩家画像管理器
     */
    @Getter
    private ProfileManager profileManager;

    /**
     * 服务器事件采集器
     */
    @Getter
    private EventCollector eventCollector;

    /**
     * 社交关系管理器
     */
    @Getter
    private SocialGraph socialGraph;

    /**
     * 社交关系智能提取器
     */
    @Getter
    private SocialRelationExtractor socialRelationExtractor;

    /**
     * 市场事件采集器
     */
    @Getter
    private MarketEventCollector marketEventCollector;

    /**
     * 对话持久化服务
     */
    @Getter
    private ConversationPersistenceService persistenceService;

    /**
     * 事件数据清理服务
     */
    @Getter
    private DataCleanupService dataCleanupService;

    /**
     * 离线事件聚合器
     */
    @Getter
    private OfflineEventAggregator offlineEventAggregator;

    /**
     * 版本更新检测器（单例，供问候系统与管理员命令复用）
     */
    @Getter
    private UpdateChecker updateChecker;

    /**
     * 画像分析服务
     */
    @Getter
    private ProfileAnalysisService profileAnalysisService;

    private ChatListener chatListener;
    private ConversationManager conversationManager;
    @Getter
    private KnowledgeBaseManager knowledgeBase;
    @Getter
    private KnowledgeRetriever knowledgeRetriever;
    @Getter
    private EmbeddingService embeddingService;

    private SkillManager skillManager;
    private SkillIntentRecognizer intentRecognizer;

    @Getter
    private LLMManager llmManager;
    private ItemTranslator itemTranslator;

    /**
     * 国际化服务
     */
    @Getter
    private I18nService i18nService;

    /**
     * AI 响应输出管线（统一管理所有 AI 回复的输出载体）
     */
    @Getter
    private AIResponsePipeline responsePipeline;

    /**
     * 流式输出管理器（管理流式状态和占位符窗口期）
     */
    @Getter
    private StreamOutputManager streamOutputManager;

    /**
     * AI 回复音效管理器
     */
    @Getter
    private SoundEffectManager soundEffectManager;

    /**
     * LLM 输出协调器（统一调度 LLM 二次分析的输出）
     */
    @Getter
    private LLMOutputCoordinator llmOutputCoordinator;

    /**
     * 跨玩家上下线订阅服务（PlayerWatch）
     */
    @Getter
    private PlayerWatchService playerWatchService;

    /**
     * 玩家自定义监听服务（WatchSkill）
     */
    @Getter
    private WatchService watchService;
    @Getter
    private WatchConfigManager watchConfigManager;

    /**
     * 对话推荐系统配置管理器（behavior.yml suggestion 段）
     */
    @Getter
    private SuggestionConfigManager suggestionConfigManager;
    /**
     * 对话推荐系统玩家级开关管理器（opt-out 内存态）
     */
    @Getter
    private SuggestionManager suggestionManager;
    /**
     * 对话推荐编排服务
     */
    @Getter
    private SuggestionService suggestionService;

    /**
     * 工具通知提示词配置管理器（behavior.yml utility.prompts）
     */
    @Getter
    private UtilityConfigManager utilityConfigManager;

    /**
     * 统一定时任务调度器
     */
    @Getter
    private TaskScheduler taskScheduler;

    /**
     * Admin 配置管理器（admin.yml）
     */
    @Getter
    private AdminConfigManager adminConfigManager;

    @Getter
    private WebConfigManager webConfigManager;

    /**
     * 服务器健康守护线程（非 static 单例，由本类持有）
     */
    @Getter
    private ServerHealthGuardian serverHealthGuardian;

    /**
     * 外部通知服务（Discord/钉钉 webhook）
     */
    @Getter
    private NotificationService notificationService;

    /**
     * 管理员功能事件监听器
     */
    private AdminListener adminListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        initializeManagers();
        initializeKnowledgeSystem();
        initializeStreamOutput();      // 初始化流式输出管理器
        initializeSoundEffect();       // 初始化音效管理器
        initializeResponsePipeline();  // 初始化响应输出管线
        initializeLLMOutputCoordinator();  // 初始化 LLM 输出协调器
        initializeChatAndCommands();
        registerMythicMobsPlaceholders();
        initializeSkillsSystem();
        initializeAdminSystem();   // 初始化服主管理功能
        initializePlayerWatchSystem(); // 初始化跨玩家上下线订阅（依赖 skillManager 就绪）
        initializeWatchSystem();      // 初始化玩家自定义监听（依赖 skillManager 就绪）
        initializeSuggestionSystem(); // 初始化对话推荐（依赖 skillManager + llmManager 就绪）

        // 设置 MetricsCollector 的 SkillManager 引用（用于动态获取 Skill 列表）
        MetricsCollector.getInstance().setSkillManager(skillManager);

        MetricsBootstrap.bootstrap(this);
        printStartupBanner();
    }

    /**
     * 初始化基础管理器
     */
    private void initializeManagers() {
        configManager = new ConfigManager(this);
        // WebConfigManager 由 ConfigManager 构造时初始化并 loadConfig，此处取引用供后续技能注册使用
        webConfigManager = configManager.getWebConfigManager();
        i18nService = new I18nService(this);
        i18nService.load();
        languageManager = new LanguageManager(this);
        personalitiesConfigManager = new PersonalitiesConfigManager(this);
        conversationManager = new ConversationManager();

        // 初始化数据库（在所有管理器之后、知识库之前）
        initializeDatabase();
    }

    /**
     * 初始化数据库系统
     *
     * <p>数据库初始化失败不会阻止插件启动。MySQL 连接失败时会自动回退到 H2。</p>
     */
    private void initializeDatabase() {
        try {
            databaseConfigManager = new DatabaseConfigManager(this);
            databaseManager = new DatabaseManager();
            databaseManager.initialize(databaseConfigManager);

            String serverId = databaseManager.getConfig().getServerId();

            // 初始化画像/事件/社交系统
            // 创建顺序：EventCollector 先于 ProfileManager，因为 ProfileManager 需要注入 EventCollector
            eventCollector = new EventCollector(this, databaseManager, serverId);
            profileManager = new ProfileManager(this, databaseManager);
            profileManager.setEventCollector(eventCollector);
            socialGraph = new SocialGraph(this, databaseManager);

            // 注册事件监听器
            getServer().getPluginManager().registerEvents(eventCollector, this);
            getServer().getPluginManager().registerEvents(new ProfileEventCollector(this, profileManager, eventCollector), this);

            // 注册私聊监听器（最小依赖：仅注入 SocialGraph 和 DatabaseManager）
            getServer().getPluginManager().registerEvents(new PrivateChatListener(socialGraph, databaseManager), this);

            // 注册 TPA 传送监听器（与私聊监听器同模式，覆盖直接使用 /tpa、/tpahere 的场景）
            getServer().getPluginManager().registerEvents(new TpaListener(socialGraph, databaseManager), this);

            // 对话持久化服务初始化（定时任务由 TaskScheduler 统一调度）
            initializePersistenceService();

            // 初始化画像分析服务
            profileAnalysisService = new ProfileAnalysisService(this, databaseManager, profileManager);

            PluginLoggerUtil.info("数据库", "画像与事件采集系统已初始化");
        } catch (Exception e) {
            PluginLoggerUtil.error("数据库", "数据库初始化失败（含 H2 回退）: {}", e.getMessage());
            PluginLoggerUtil.warn("数据库", "插件将继续运行，但持久化功能不可用");
            databaseManager = null;
        }

        // 初始化离线事件聚合器（数据库不可用时也能创建）
        if (databaseManager != null) {
            offlineEventAggregator = new OfflineEventAggregator(this, databaseManager);

            // 条件注册 GlobalMarketPlus 事件采集器
            if (getServer().getPluginManager().getPlugin("GlobalMarketPlus") != null) {
                try {
                    marketEventCollector = new MarketEventCollector(databaseManager, databaseManager.getConfig().getServerId());
                    getServer().getPluginManager().registerEvents(marketEventCollector, this);
                    PluginLoggerUtil.info("数据库", "GlobalMarketPlus 事件采集器已注册");
                } catch (Exception e) {
                    PluginLoggerUtil.warn("市场事件", "GMP 事件监听注册失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 初始化知识库系统
     */
    private void initializeKnowledgeSystem() {
        // 知识库管理器（依赖 configManager）
        knowledgeBase = new KnowledgeBaseManager(this, getDataFolder().getAbsolutePath());
        knowledgeBase.loadAllKnowledge();

        // LLM 管理器（优先级高，其他组件运行时依赖它）
        llmManager = new LLMManager();

        // 知识检索器（依赖 configManager + knowledgeBase）
        knowledgeRetriever = new KnowledgeRetriever(knowledgeBase, configManager.getMaxRelevantChunks(), configManager.getKnowledgeMaxChunkSize(), configManager.getKnowledgeMinChunkSize(), configManager.getKnowledgeChunkOverlap(), configManager.getKeywordTopK(), configManager.getBm25K1(), configManager.getBm25B());

        // 先预构建分段缓存（使 avgDocLength 可统计，同时预热检索）；
        // 再注入软阈值 + BM25 长度归一化参数（setRetrievalConfig 内部已触发 computeAvgDocLength）
        knowledgeRetriever.buildChunkCache();
        knowledgeRetriever.setRetrievalConfig(configManager.getRetrievalNoiseFloor(), configManager.getRetrievalRelativeThreshold(), configManager.getRetrievalRrfK(), configManager.getBm25AvgDocLength());

        // Embedding 语义检索服务
        if (configManager.isEmbeddingEnabled()) {
            embeddingService = new EmbeddingService(configManager, knowledgeBase.getKnowledgeDir());

            // 仅在 Embedding 可用时（配置完整）执行预计算和启用
            if (embeddingService.isAvailable()) {
                knowledgeRetriever.setEmbeddingService(embeddingService, true, configManager.getEmbeddingMinSimilarity());

                // 预计算向量（分段缓存已在上方对所有用户构建）
                try {
                    embeddingService.precomputeAllChunks(knowledgeBase.getAllChunkCache());
                } catch (Exception e) {
                    PluginLoggerUtil.warn("知识库", "Embedding 预计算异常: {}", e.getMessage());
                }

                PluginLoggerUtil.info("知识库", "Embedding 语义检索已启用");
            }
        }

        // 自定义词典（依赖 configManager，通过工厂统一初始化）
        if (configManager.isCustomDictionaryEnabled()) {
            TextProcessorFactory.initialize(knowledgeBase.buildDictionaryWordsWithCorpus(configManager.getAllDictionaryWords()));
            PluginLoggerUtil.info("词典系统", "已加载 {} 个内置词汇", configManager.getInternalDictionaryWords().size());
            List<String> customWords = configManager.getCustomDictionaryWords();
            PluginLoggerUtil.info("词典系统", "已加载 {} 个自定义词汇", customWords != null ? customWords.size() : 0);
        }

        // 物品翻译器（无外部依赖）
        itemTranslator = new ItemTranslator();
        itemTranslator.loadTranslationTable();

        // 内置枚举注册表（音效/粒子/统计）
        InternalEnumRegistry enumRegistry = new InternalEnumRegistry();
        enumRegistry.loadAll();
    }

    /**
     * 初始化 AI 响应输出管线
     */
    private void initializeResponsePipeline() {
        responsePipeline = new AIResponsePipeline(this);
    }

    /**
     * 初始化流式输出管理器
     */
    private void initializeStreamOutput() {
        streamOutputManager = new StreamOutputManager(this);
    }

    /**
     * 初始化 AI 回复音效管理器
     */
    private void initializeSoundEffect() {
        soundEffectManager = new SoundEffectManager(this);
    }

    /**
     * 初始化 LLM 输出协调器
     */
    private void initializeLLMOutputCoordinator() {
        llmOutputCoordinator = new LLMOutputCoordinator(this);
    }

    /**
     * 初始化对话持久化服务
     */
    private void initializePersistenceService() {
        DatabaseConfig dbConfig = databaseManager.getConfig();
        int maxHistory = configManager.getMaxHistory();
        int retentionDays = dbConfig.getConversationRetentionDays();
        boolean loadHistoryEnabled = dbConfig.isLoadHistoryOnLogin();
        String serverId = dbConfig.getServerId();

        persistenceService = new ConversationPersistenceService(this, databaseManager, conversationManager, maxHistory, retentionDays, loadHistoryEnabled, serverId);

        // 注入到 ConversationManager（onPlayerQuit 时 flush）
        conversationManager.setPersistenceService(persistenceService);

        PluginLoggerUtil.info("数据库", "对话持久化服务已初始化（历史加载: {}, 保留天数: {}）", loadHistoryEnabled, retentionDays > 0 ? retentionDays : "永久");

        // 初始化事件数据清理服务（定时任务由 TaskScheduler 统一调度）
        int eventRetentionDays = dbConfig.getEventRetentionDays();
        dataCleanupService = new DataCleanupService(databaseManager, eventRetentionDays, dbConfig.getSkillLogRetentionDays());

        // 统一注册所有定时任务到 TaskScheduler
        initializeScheduledTasks();
    }

    /**
     * 统一注册所有定时任务到 TaskScheduler
     *
     * <p>所有周期性任务通过 TaskScheduler 集中管理，统一获得 CAS 互斥保护、
     * 结构化日志和生命周期管理。</p>
     */
    private void initializeScheduledTasks() {
        taskScheduler = new TaskScheduler(this);

        // 1. 对话刷盘（每 30 秒）
        taskScheduler.register(new ManagedTask() {
            @Override
            public String name() {
                return I18nService.tr("对话刷盘");
            }

            @Override
            public String description() {
                return I18nService.tr("定时批量写入对话记录");
            }

            @Override
            public long delayTicks() {
                return 600L;
            }

            @Override
            public long intervalTicks() {
                return 600L;
            }

            @Override
            public int execute() {
                return persistenceService.scheduledFlush();
            }
        });

        // 2. 对话清理（每 6 小时，条件注册）
        taskScheduler.register(new ManagedTask() {
            @Override
            public String name() {
                return I18nService.tr("对话清理");
            }

            @Override
            public String description() {
                return I18nService.tr("清理过期对话记录");
            }

            @Override
            public long delayTicks() {
                return 1200L;
            }

            @Override
            public long intervalTicks() {
                return 432000L;
            }

            @Override
            public boolean enabled() {
                return persistenceService.getRetentionDays() > 0;
            }

            @Override
            public int execute() {
                return persistenceService.scheduledCleanup();
            }
        });

        // 3. 事件清理（每 6 小时，条件注册）
        taskScheduler.register(new ManagedTask() {
            @Override
            public String name() {
                return I18nService.tr("事件清理");
            }

            @Override
            public String description() {
                return I18nService.tr("清理过期事件和审计日志");
            }

            @Override
            public long delayTicks() {
                return 2400L;
            }

            @Override
            public long intervalTicks() {
                return 432000L;
            }

            @Override
            public boolean enabled() {
                return dataCleanupService.needsCleanup();
            }

            @Override
            public int execute() {
                return dataCleanupService.scheduledCleanup();
            }
        });

        // 4. 社交关系每日衰减（每 24 小时）
        taskScheduler.register(new ManagedTask() {
            @Override
            public String name() {
                return I18nService.tr("社交衰减");
            }

            @Override
            public String description() {
                return I18nService.tr("每日衰减社交关系强度");
            }

            @Override
            public long delayTicks() {
                return 6000L;
            }

            @Override
            public long intervalTicks() {
                return 1728000L;
            }

            @Override
            public int execute() {
                return socialGraph.performDailyDecay();
            }
        });

        // 5. 社交关系智能提取（每 30 分钟）
        socialRelationExtractor = new SocialRelationExtractor(databaseManager, socialGraph, databaseManager.getConfig().getServerId());
        taskScheduler.register(new ManagedTask() {
            @Override
            public String name() {
                return I18nService.tr("社交提取");
            }

            @Override
            public String description() {
                return I18nService.tr("从Skill日志提取社交关系");
            }

            @Override
            public long delayTicks() {
                return 3600L;
            }

            @Override
            public long intervalTicks() {
                return 36000L;
            }

            @Override
            public int execute() {
                return socialRelationExtractor.extractNewRelations();
            }
        });

        // 6. 安全过滤器近期活跃玩家缓存清理（每 5 分钟）
        taskScheduler.register(new ManagedTask() {
            @Override
            public String name() {
                return I18nService.tr("安全缓存清理");
            }

            @Override
            public String description() {
                return I18nService.tr("清理过期的近期活跃玩家缓存");
            }

            @Override
            public long delayTicks() {
                return 6000L;
            }

            @Override
            public long intervalTicks() {
                return 6000L; // 5 分钟
            }

            @Override
            public boolean enabled() {
                return configManager != null && configManager.isSecurityPlayerIsolationEnabled();
            }

            @Override
            public int execute() {
                return SkillSecurityFilter.cleanupExpired();
            }
        });

        // 7. 待确认续体过期清理（每 5 分钟）
        taskScheduler.register(new ManagedTask() {
            @Override
            public String name() {
                return I18nService.tr("续体过期清理");
            }

            @Override
            public String description() {
                return I18nService.tr("清理过期/超轮的待确认续体");
            }

            @Override
            public long delayTicks() {
                return 6000L;
            }

            @Override
            public long intervalTicks() {
                return 6000L; // 5 分钟
            }

            @Override
            public boolean enabled() {
                return configManager != null && configManager.isPendingResumeEnabled();
            }

            @Override
            public int execute() {
                return PendingResumeManager.getInstance().cleanupExpired();
            }
        });
    }

    /**
     * 初始化聊天监听器和命令注册
     */
    private void initializeChatAndCommands() {
        chatListener = new ChatListener(this);

        // 注册命令
        var command = getCommand("kilacraft");
        if (command != null) {
            command.setExecutor(new KilacraftCommand(this));
            command.setTabCompleter(new TabCompleter());
        } else {
            PluginLoggerUtil.error("命令注册", "无法注册命令：kilacraft，请检查 plugin.yml 配置");
        }

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(chatListener, this);

        // 注册 AI 登录问候监听器（始终注册，运行时由 LoginGreetingHandler 检查条件）
        if (offlineEventAggregator != null) {
            getServer().getPluginManager().registerEvents(new LoginGreetingHandler(this, offlineEventAggregator), this);
            if (configManager.isGreetingEnabled() && configManager.isApiKeyConfigured()) {
                PluginLoggerUtil.info("问候系统", "AI 登录问候系统已启用");
            }
        }
    }

    /**
     * 注册 MythicMobs 占位符（反射调用，运行时检测 JDK 版本）
     * <p>使用反射避免编译时对 Java 21 的硬依赖</p>
     */
    private void registerMythicMobsPlaceholders() {
        try {
            String javaVersion = System.getProperty("java.version");
            int majorVersion = Integer.parseInt(javaVersion.split("\\.")[0]);

            if (majorVersion >= 21) {
                Class<?> managerClass = Class.forName("com.zm.kilacraftAI.compat.mythicmobs.MythicMobsPlaceholderManager");
                var constructor = managerClass.getConstructor(KilacraftAI.class);
                var managerInstance = constructor.newInstance(this);
                var method = managerClass.getMethod("registerPlaceholders");
                method.invoke(managerInstance);
            } else {
                PluginLoggerUtil.warn("MythicMobs", "当前 JDK 版本为 {}，MythicMobs 需要 Java 21+，跳过占位符注册", javaVersion);
            }
        } catch (ClassNotFoundException e) {
            PluginLoggerUtil.error("MythicMobs", "MythicMobs 兼容模块缺失，请检查 JAR 包完整性");
        } catch (Exception e) {
            PluginLoggerUtil.error("MythicMobs", I18nService.tr("MythicMobs 占位符注册失败：{}", e.getMessage()), e);
        }
    }

    /**
     * 初始化 Skills 系统
     */
    private void initializeSkillsSystem() {
        // 技能配置管理器（依赖 plugin）
        skillConfigManager = new SkillConfigManager(this);
        skillConfigManager.loadAllSkillConfigs();

        // 意图识别提示词配置管理器（依赖 plugin）
        intentPromptConfigManager = new IntentPromptConfigManager(this);

        // 工具通知提示词配置（UtilitySkill 构造时读取，须先于 registerDefaultSkills）
        utilityConfigManager = new UtilityConfigManager(this);
        utilityConfigManager.loadConfig();

        // Skills 系统（依赖 skillConfigManager）
        skillManager = new SkillManager();
        registerDefaultSkills();

        // 安全过滤器：注册事件监听器（在线玩家名缓存）
        SkillSecurityFilter securityFilter = SkillSecurityFilter.createAndInit();
        getServer().getPluginManager().registerEvents(securityFilter, this);

        // 待确认续体管理器：注册事件监听器（玩家下线清理 per-player 槽位）
        getServer().getPluginManager().registerEvents(PendingResumeManager.getInstance(), this);

        // 意图识别器（依赖 configManager + intentPromptConfigManager + skillManager）
        intentRecognizer = new SkillIntentRecognizer(configManager, intentPromptConfigManager, skillManager);

        // 延迟发现并注册第三方 SkillProvider 提供的 Skill
        FoliaCompat.runTaskLater(this, () -> new SkillRegistry(this, skillManager).discoverAndRegister(), 20L);
    }

    /**
     * 打印启动标志，并异步检查版本更新
     */
    private void printStartupBanner() {
        Logger logger = getLogger();
        logger.info("╻┏ ╻╻  ┏━┓┏━╸┏━┓┏━┓┏━╸╺┳╸   ┏━┓╻");
        logger.info("┣┻┓┃┃  ┣━┫┃  ┣┳┛┣━┫┣╸  ┃ ╺━╸┣━┫┃");
        logger.info("╹ ╹╹┗━╸╹ ╹┗━╸╹┗╸╹ ╹╹   ╹    ╹ ╹╹");
        logger.info((I18nService.isZh() ? "版本：v" : "Version: v") + getDescription().getVersion());
        logger.info(I18nService.isZh() ? "作者：Zm_Mmm" : "Author: Zm_Mmm");

        // 初始化版本检测单例并异步检查（有新版本时控制台输出彩色提示，失败或已是最新则静默）
        updateChecker = new UpdateChecker(this);
        updateChecker.checkAsync();
    }

    /**
     * 注册默认技能
     */
    private void registerDefaultSkills() {
        // 注册 Bukkit API 查询技能（按语义域拆分为 5 个标准 skill）
        skillManager.registerSkill(new BukkitPlayerInventorySkill());
        skillManager.registerSkill(new BukkitPlayerStatusSkill());
        skillManager.registerSkill(new BukkitPlayerInfoSkill());
        skillManager.registerSkill(new BukkitWorldSkill());
        skillManager.registerSkill(new BukkitServerSkill());

        // 注册音效与粒子效果技能
        skillManager.registerSkill(new BukkitFXSkill());

        // 注册原版统计数据查询技能
        skillManager.registerSkill(new BukkitStatsSkill());

        // 注册通用工具技能（始终可用）
        skillManager.registerSkill(new UtilitySkill());

        // 注册命令执行技能（条件注册：需 config.yml 中 command_skill.enabled=true）
        if (configManager.isCommandSkillEnabled()) {
            skillManager.registerSkill(new CommandSkill());
        }

        // 注册全球市场技能（条件注册：仅当 GlobalMarketPlus 插件存在时）
        if (getServer().getPluginManager().getPlugin("GlobalMarketPlus") != null) {
            skillManager.registerSkill(new MarketQuerySkill());
            skillManager.registerSkill(new MarketActionSkill());
        }

        // 注册 CMI 技能（条件注册：仅当 CMI 插件存在时）
        if (getServer().getPluginManager().getPlugin("CMI") != null) {
            skillManager.registerSkill(new CMISkill());
        }

        // 管理员技能（无外部依赖，数据源为项目数据库表或 Release API）
        skillManager.registerSkill(new ServerHealthSkill());
        skillManager.registerSkill(new PlayerAnalysisSkill());
        skillManager.registerSkill(new AuditLogSkill());
        skillManager.registerSkill(new VersionInfoSkill());

        // Web 搜索技能（条件注册：需 web.yml search.enabled=true）
        if (webConfigManager != null && webConfigManager.isSearchEnabled()) {
            skillManager.registerSkill(new WebSearchSkill());
        }
        if (webConfigManager != null && webConfigManager.isFetchEnabled()) {
            skillManager.registerSkill(new WebFetchSkill());
        }
    }

    /**
     * 同步条件技能的注册状态（支持热重载）
     *
     * <p>根据当前配置动态注册或注销条件技能，使 reload 命令能够即时生效。</p>
     */
    public void syncConditionalSkills() {
        // 同步命令执行技能（条件：config.yml command_skill.enabled）
        syncSkill("command", configManager.isCommandSkillEnabled(), () -> skillManager.registerSkill(new CommandSkill()));

        // 注意：第三方插件技能（CMI、GlobalMarketPlus）无需热重载同步
        // 插件在运行时不会被动态装卸，注册时已经通过 isAvailable() 检查

        // 同步管理员技能（无外部依赖，始终注册）
        syncSkill("server_health", true, () -> skillManager.registerSkill(new ServerHealthSkill()));
        syncSkill("player_analysis", true, () -> skillManager.registerSkill(new PlayerAnalysisSkill()));
        syncSkill("audit_log", true, () -> skillManager.registerSkill(new AuditLogSkill()));

        syncSkill("web_search", webConfigManager != null && webConfigManager.isSearchEnabled(), () -> skillManager.registerSkill(new WebSearchSkill()));
        syncSkill("web_fetch", webConfigManager != null && webConfigManager.isFetchEnabled(), () -> skillManager.registerSkill(new WebFetchSkill()));
    }

    /**
     * 同步单个技能的注册状态
     *
     * @param skillName          技能名称
     * @param shouldBeRegistered 当前是否应该注册
     * @param registerAction     注册动作（仅在需要注册时执行）
     */
    private void syncSkill(String skillName, boolean shouldBeRegistered, Runnable registerAction) {
        boolean isRegistered = skillManager.getSkill(skillName) != null;

        if (shouldBeRegistered && !isRegistered) {
            registerAction.run();
            PluginLoggerUtil.info("热重载", "已注册技能 {}", skillName);
        } else if (!shouldBeRegistered && isRegistered) {
            skillManager.unregisterSkill(skillName);
            PluginLoggerUtil.info("热重载", "已注销技能 {}", skillName);
        }
    }

    /**
     * 初始化服主管理功能系统
     *
     * <p>包含 AdminConfigManager、ServerHealthGuardian、AdminListener 的初始化。</p>
     * <p>守护线程依赖 Spark + 诊断模型可用（admin.yml 显式 或 回退 llm.yml 基础模型），任一不满足则不创建（AdminListener 和 AdminConfigManager 始终初始化）。</p>
     */
    private void initializeAdminSystem() {
        adminConfigManager = new AdminConfigManager(this);
        adminConfigManager.loadConfig();

        // 初始化外部通知服务
        notificationService = new NotificationService();
        notificationService.reload(adminConfigManager.isNotificationEnabled(), adminConfigManager.getNotificationChannels());

        // 管理员事件监听器（掉线中断采样）始终注册
        adminListener = new AdminListener(this);
        getServer().getPluginManager().registerEvents(adminListener, this);

        // 守护线程依赖：databaseManager + guardian.enabled + 诊断模型可用（admin 显式 或 llm 回退）
        if (databaseManager == null || !adminConfigManager.isGuardianEnabled() || !adminConfigManager.isThinkingModelConfigured()) {
            return;
        }

        // 检测 Spark 可用性（第三个依赖）
        SparkDataCollector sparkCollector = new SparkDataCollector();
        if (sparkCollector.isSparkAvailable()) {
            serverHealthGuardian = new ServerHealthGuardian(this, adminConfigManager);
            if (taskScheduler != null) {
                taskScheduler.register(serverHealthGuardian);
                PluginLoggerUtil.info("服主管理", "健康守护线程已启动（间隔: {}s）", adminConfigManager.getGuardianInterval());
            }
        } else {
            // Spark 暂未检测到（Leaf 等服务端延迟注册），2 分钟后重试
            scheduleSparkRetry();
        }
    }

    /**
     * 初始化跨玩家上下线订阅系统（PlayerWatch）。
     *
     * <p>PlayerWatch 是玩家订阅他人上下线状态。
     * 内存订阅（不持久化），玩家下线自动清空。失败降级跳过。</p>
     */
    private void initializePlayerWatchSystem() {
        try {
            this.playerWatchService = new PlayerWatchService(this);
            getServer().getPluginManager().registerEvents(playerWatchService, this);
            if (skillManager != null) {
                skillManager.registerSkill(new PlayerWatchSkill());
            }
            PluginLoggerUtil.info("跨玩家监控", I18nService.tr("PlayerWatch 系统已初始化"));
        } catch (Exception e) {
            PluginLoggerUtil.error("跨玩家监控", I18nService.tr("PlayerWatch 初始化失败，降级跳过: {}", e.getMessage()), e);
        }
    }

    /**
     * 初始化玩家自定义监听系统（依赖 skillManager 就绪）。
     */
    private void initializeWatchSystem() {
        try {
            this.watchConfigManager = new WatchConfigManager(this);
            this.watchConfigManager.loadConfig();
            if (!watchConfigManager.isEnabled()) {
                return;
            }
            this.watchService = new WatchService(this, watchConfigManager);
            this.watchService.initGlobalListener();
            getServer().getPluginManager().registerEvents(watchService, this);
            if (skillManager != null) {
                skillManager.registerSkill(new WatchSkill());
            }
            PluginLoggerUtil.info("自定义监听", I18nService.tr("WatchSkill 系统已初始化"));
        } catch (Exception e) {
            PluginLoggerUtil.error("自定义监听", I18nService.tr("WatchSkill 初始化失败，降级跳过: {}", e.getMessage()), e);
        }
    }

    /**
     * 初始化对话推荐系统（behavior.yml suggestion 段 + 玩家级开关 + 编排服务）。
     * 依赖 skillManager（技能摘要）与 llmManager（LLM 调用）已就绪，须在两者之后调用。
     */
    private void initializeSuggestionSystem() {
        try {
            this.suggestionConfigManager = new SuggestionConfigManager(this);
            this.suggestionConfigManager.loadConfig();
            if (!suggestionConfigManager.isEnabled()) {
                return;
            }
            this.suggestionManager = new SuggestionManager();
            this.suggestionService = new SuggestionService(this);
            PluginLoggerUtil.info("对话推荐", I18nService.tr("对话推荐系统已初始化"));
        } catch (Exception e) {
            PluginLoggerUtil.error("对话推荐", I18nService.tr("对话推荐初始化失败，降级跳过: {}", e.getMessage()), e);
        }
    }

    /**
     * 安排 Spark 延迟检测（服务器启动完毕后 2 分钟）
     *
     * <p>Leaf 等服务端配置 {@code enable-immediately: false} 时，
     * Spark API 单例在插件 onEnable 时尚未注册。
     * 延迟 2 分钟等待 Spark 平台完成初始化后再检测一次。</p>
     *
     * <p>如果 2 分钟后仍检测不到 Spark，视为不启用任何 Spark 相关功能。</p>
     */
    private void scheduleSparkRetry() {
        PluginLoggerUtil.info("服主管理", "Spark 暂未检测到，2 分钟后重试");
        FoliaCompat.runTaskLater(this, this::retrySparkDetection, 2400L);
    }

    /**
     * Spark 延迟检测逻辑（仅执行一次）
     *
     * <p>服务器启动 2 分钟后执行。成功则注册 Spark 相关功能，失败则提醒服主。</p>
     */
    private void retrySparkDetection() {
        SparkDataCollector sparkCollector = new SparkDataCollector();
        if (sparkCollector.isSparkAvailable()) {
            syncConditionalSkills();
            syncGuardianState();
            if (serverHealthGuardian != null) {
                PluginLoggerUtil.info("服主管理", "Spark 延迟检测成功，健康监控已启用");
            }
        } else {
            PluginLoggerUtil.info("服主管理", "Spark 不可用，健康监控已禁用");
        }
    }

    /**
     * 同步守护线程状态（支持热重载）
     *
     * <p>根据当前配置（诊断模型可用性、Spark 可用性、guardian enabled、interval）动态创建、销毁或重启守护线程。</p>
     * <p>由 {@code /kila reload} 命令调用，使 admin.yml 配置变更即时生效。</p>
     */
    public void syncGuardianState() {
        if (adminConfigManager == null || databaseManager == null) {
            return;
        }

        // 检查 Spark 是否可用（优先尝试 Spark API，兼容 Leaf 等内置 Spark 的服务端）
        SparkDataCollector sparkCollector = new SparkDataCollector();
        boolean sparkAvailable = sparkCollector.isSparkAvailable();

        boolean shouldBeActive = sparkAvailable && adminConfigManager.isGuardianEnabled() && adminConfigManager.isThinkingModelConfigured();
        boolean isActive = serverHealthGuardian != null;

        if (shouldBeActive && !isActive) {
            // 创建并注册守护线程
            serverHealthGuardian = new ServerHealthGuardian(this, adminConfigManager);
            if (taskScheduler != null) {
                taskScheduler.register(serverHealthGuardian);
                PluginLoggerUtil.info("热重载", "健康守护线程已启动（间隔: {}s）", adminConfigManager.getGuardianInterval());
            }
        } else if (!shouldBeActive && isActive) {
            // 关闭并注销守护线程
            serverHealthGuardian.shutdown();
            if (taskScheduler != null) {
                taskScheduler.unregister(serverHealthGuardian);
            }
            serverHealthGuardian = null;
            PluginLoggerUtil.info("热重载", "健康守护线程已关闭");
        } else if (shouldBeActive) {
            // 间隔变化时重启守护线程（interval_seconds 需要重建任务才能生效）
            int newInterval = adminConfigManager.getGuardianInterval();
            int oldInterval = serverHealthGuardian.getIntervalSeconds();
            if (newInterval != oldInterval) {
                serverHealthGuardian.shutdown();
                if (taskScheduler != null) {
                    taskScheduler.unregister(serverHealthGuardian);
                }
                serverHealthGuardian = new ServerHealthGuardian(this, adminConfigManager);
                if (taskScheduler != null) {
                    taskScheduler.register(serverHealthGuardian);
                }
                PluginLoggerUtil.info("热重载", "健康守护线程已重启（间隔: {}s → {}s）", oldInterval, newInterval);
            }
            // 其他配置值（阈值、超时等）会在下次轮询时自动读取
        }

        // 同步通知服务配置
        if (notificationService != null) {
            notificationService.reload(adminConfigManager.isNotificationEnabled(), adminConfigManager.getNotificationChannels());
        }
    }

    @Override
    public void onDisable() {
        // 清理 AI 响应输出管线（释放 BossBar 等资源）
        if (responsePipeline != null) {
            responsePipeline.cleanup();
        }

        // 清理流式输出管理器（释放状态映射）
        if (streamOutputManager != null) {
            streamOutputManager.cleanup();
        }

        // 关闭 Embedding 服务
        if (embeddingService != null) {
            embeddingService.shutdown();
        }

        // 关闭守护线程（必须在 taskScheduler.shutdownAll() 之前，设置 shutdown 标志）
        if (serverHealthGuardian != null) {
            serverHealthGuardian.shutdown();
        }

        // 关闭跨玩家订阅（注销 Listener + 清空订阅，避免延迟任务回调进入已关闭的 LLM 链）
        if (playerWatchService != null) {
            HandlerList.unregisterAll(playerWatchService);
            playerWatchService.shutdown();
            playerWatchService = null;
        }

        // 关闭玩家自定义监听（注销 Listener + 取消定时器 + 清空监听）
        if (watchService != null) {
            HandlerList.unregisterAll(watchService);
            watchService.shutdown();
            watchService = null;
        }

        // 关闭外部通知服务（释放自建 OkHttpClient 线程池）
        if (notificationService != null) {
            notificationService.shutdown();
        }

        // 统一取消所有定时任务（必须在 flushAll 之前）
        if (taskScheduler != null) {
            taskScheduler.shutdownAll();
        }

        // 刷盘对话持久化服务剩余消息（定时器已由 TaskScheduler 取消）
        if (persistenceService != null) {
            persistenceService.shutdown();
        }

        // 同步刷盘所有在线玩家画像
        if (profileManager != null) {
            profileManager.flushAllProfiles();
        }

        // 等待 IO Pool 完成所有已提交的异步写入任务（flushPlayer 异步提交的 writeBatch 等）
        // 必须在 databaseManager.shutdown() 之前，否则异步写入因连接池已关闭而失败导致消息丢失
        FoliaCompat.shutdownIOPool();

        // 确认异步写入全部完成后，再关闭数据库连接池
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        // 关闭 LLM 管理器（包含所有提供商的连接池）
        if (llmManager != null) {
            llmManager.shutdownAll();
        }

        // 清除全部待确认续体（纯内存态，不落盘；高风险待确认操作绝不跨重启复活）
        PendingResumeManager.getInstance().clearAll();
        PluginLoggerUtil.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        PluginLoggerUtil.info("  Kilacraft-AI 已停止运行");
        PluginLoggerUtil.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
