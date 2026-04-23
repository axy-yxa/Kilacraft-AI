package com.zm.kilacraftAI;

import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.*;
import com.zm.kilacraftAI.core.KilacraftCommand;
import com.zm.kilacraftAI.core.TabCompleter;
import com.zm.kilacraftAI.knowledge.KnowledgeBaseManager;
import com.zm.kilacraftAI.knowledge.KnowledgeRetriever;
import com.zm.kilacraftAI.listener.ChatListener;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.manager.LLMManager;
import com.zm.kilacraftAI.manager.SoundEffectManager;
import com.zm.kilacraftAI.manager.StreamOutputManager;
import com.zm.kilacraftAI.metrics.MetricsBootstrap;
import com.zm.kilacraftAI.metrics.MetricsCollector;
import com.zm.kilacraftAI.output.AIResponsePipeline;
import com.zm.kilacraftAI.skills.afktask.AFKTaskListener;
import com.zm.kilacraftAI.skills.afktask.AFKTaskManager;
import com.zm.kilacraftAI.skills.afktask.AFKTaskSkill;
import com.zm.kilacraftAI.skills.bukkit.BukkitFXSkill;
import com.zm.kilacraftAI.skills.bukkit.BukkitStatsSkill;
import com.zm.kilacraftAI.skills.bukkit.GenericBukkitAPISkill;
import com.zm.kilacraftAI.skills.cmi.CMISkill;
import com.zm.kilacraftAI.skills.command.CommandSkill;
import com.zm.kilacraftAI.skills.framework.SkillIntentRecognizer;
import com.zm.kilacraftAI.skills.framework.SkillManager;
import com.zm.kilacraftAI.skills.framework.SkillSecurityFilter;
import com.zm.kilacraftAI.skills.framework.spi.SkillRegistry;
import com.zm.kilacraftAI.skills.framework.task.LLMOutputCoordinator;
import com.zm.kilacraftAI.skills.globalmarketplus.MarketQuerySkill;
import com.zm.kilacraftAI.translate.ItemTranslator;
import com.zm.kilacraftAI.util.PluginLogger;
import com.zm.kilacraftAI.util.TextProcessorFactory;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 插件入口
 *
 * @author Zm_Mmm
 * @since 2026-03-24 17:21:29
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
    private ChatListener chatListener;
    private ConversationManager conversationManager;
    private KnowledgeBaseManager knowledgeBase;
    private KnowledgeRetriever knowledgeRetriever;
    private SkillManager skillManager;
    private SkillIntentRecognizer intentRecognizer;

    @Getter
    private LLMManager llmManager;
    private ItemTranslator itemTranslator;
    @Getter
    private AFKTaskManager afkTaskManager;
    private AFKTaskListener afkTaskListener;

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
        initializeAFKTaskSystem();
        
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
        i18nService = new I18nService(this);
        i18nService.load();
        languageManager = new LanguageManager(this);
        personalitiesConfigManager = new PersonalitiesConfigManager(this);
        conversationManager = new ConversationManager();
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
        knowledgeRetriever = new KnowledgeRetriever(knowledgeBase, configManager.getMaxRelevantChunks(), configManager.getMinRelevanceScore(), configManager.getKnowledgeMaxChunkSize(), configManager.getKnowledgeMinChunkSize(), configManager.getKnowledgeChunkOverlap(), configManager.getKeywordTopK(), configManager.getBm25K1(), configManager.getBm25B());

        // 自定义词典（依赖 configManager，通过工厂统一初始化）
        if (configManager.isCustomDictionaryEnabled()) {
            TextProcessorFactory.initialize(configManager.getAllDictionaryWords());
            PluginLogger.info("词典系统", "已加载 {} 个内置词汇", configManager.getInternalDictionaryWords().size());
            List<String> customWords = configManager.getCustomDictionaryWords();
            PluginLogger.info("词典系统", "已加载 {} 个自定义词汇", customWords != null ? customWords.size() : 0);
        }

        // 物品翻译器（无外部依赖）
        itemTranslator = new ItemTranslator();
        itemTranslator.loadTranslationTable();
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
            PluginLogger.error("命令注册", "无法注册命令：kilacraft，请检查 plugin.yml 配置");
        }

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(chatListener, this);
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
                PluginLogger.warn("MythicMobs", "当前 JDK 版本为 {}，MythicMobs 需要 Java 21+，跳过占位符注册", javaVersion);
            }
        } catch (ClassNotFoundException e) {
            PluginLogger.error("MythicMobs", "MythicMobs 兼容模块缺失，请检查 JAR 包完整性");
        } catch (Exception e) {
            PluginLogger.error("MythicMobs", I18nService.tr("MythicMobs 占位符注册失败：{}", e.getMessage()), e);
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

        // Skills 系统（依赖 skillConfigManager）
        skillManager = new SkillManager();
        registerDefaultSkills();

        // 安全过滤器：注册事件监听器（在线玩家名缓存）
        SkillSecurityFilter securityFilter = SkillSecurityFilter.createAndInit();
        getServer().getPluginManager().registerEvents(securityFilter, this);

        // 意图识别器（依赖 configManager + intentPromptConfigManager + skillManager）
        intentRecognizer = new SkillIntentRecognizer(configManager, intentPromptConfigManager, skillManager);

        // 延迟发现并注册第三方 SkillProvider 提供的 Skill
        FoliaCompat.runTaskLater(this, () -> new SkillRegistry(this, skillManager).discoverAndRegister(), 20L);
    }

    /**
     * 打印启动标志
     */
    private void printStartupBanner() {
        java.util.logging.Logger logger = getLogger();
        logger.info("╻┏ ╻╻  ┏━┓┏━╸┏━┓┏━┓┏━╸╺┳╸   ┏━┓╻");
        logger.info("┣┻┓┃┃  ┣━┫┃  ┣┳┛┣━┫┣╸  ┃ ╺━╸┣━┫┃");
        logger.info("╹ ╹╹┗━╸╹ ╹┗━╸╹┗╸╹ ╹╹   ╹    ╹ ╹╹");
        logger.info((I18nService.isZh() ? "版本：v" : "Version: v") + getDescription().getVersion());
        logger.info(I18nService.isZh() ? "作者：Zm_Mmm" : "Author: Zm_Mmm");
    }

    /**
     * 注册默认技能
     */
    private void registerDefaultSkills() {
        // 注册通用 Bukkit API 执行器（数据驱动的原版功能调用)
        skillManager.registerSkill(new GenericBukkitAPISkill());

        // 注册音效与粒子效果技能
        skillManager.registerSkill(new BukkitFXSkill());

        // 注册原版统计数据查询技能
        skillManager.registerSkill(new BukkitStatsSkill());

        // 注册挂机任务技能
        if (configManager.isAfkTaskEnabled()) {
            skillManager.registerSkill(new AFKTaskSkill());
        }

        // 注册命令执行技能（条件注册：需 config.yml 中 command_skill.enabled=true）
        if (configManager.isCommandSkillEnabled()) {
            skillManager.registerSkill(new CommandSkill());
        }

        // 注册市场查询技能（条件注册：仅当 GlobalMarketPlus 插件存在时）
        if (getServer().getPluginManager().getPlugin("GlobalMarketPlus") != null) {
            skillManager.registerSkill(new MarketQuerySkill());
        }

        // 注册 CMI 技能（条件注册：仅当 CMI 插件存在时）
        if (getServer().getPluginManager().getPlugin("CMI") != null) {
            skillManager.registerSkill(new CMISkill());
        }
    }

    /**
     * 初始化挂机任务系统
     */
    private void initializeAFKTaskSystem() {
        if (!configManager.isAfkTaskEnabled()) {
            PluginLogger.info("挂机任务", "挂机任务功能已禁用");
            return;
        }

        // 创建任务管理器
        afkTaskManager = new AFKTaskManager(this);

        // 注册事件监听器（玩家下线自动清理）
        afkTaskListener = new AFKTaskListener(this);
        getServer().getPluginManager().registerEvents(afkTaskListener, this);

        PluginLogger.info("挂机任务", "初始化挂机任务系统（最大并发任务数：{}）", configManager.getAfkTaskMaxTasks());
    }

    /**
     * 同步条件技能的注册状态（支持热重载）
     *
     * <p>根据当前配置动态注册或注销条件技能，使 reload 命令能够即时生效。</p>
     */
    public void syncConditionalSkills() {
        // 同步命令执行技能（条件：config.yml command_skill.enabled）
        syncSkill("command", configManager.isCommandSkillEnabled(), () -> skillManager.registerSkill(new CommandSkill()));

        // 同步挂机任务技能（条件：config.yml afk_task.enabled）
        syncSkill("AFKTask", configManager.isAfkTaskEnabled(), () -> skillManager.registerSkill(new AFKTaskSkill()));

        // 注意：第三方插件技能（CMI、GlobalMarketPlus）无需热重载同步
        // 插件在运行时不会被动态装卸，注册时已经通过 isAvailable() 检查

        // 同步挂机任务系统（管理器和监听器）
        syncAFKTaskSystem();
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
            PluginLogger.info("热重载", "已注册技能 {}", skillName);
        } else if (!shouldBeRegistered && isRegistered) {
            skillManager.unregisterSkill(skillName);
            PluginLogger.info("热重载", "已注销技能 {}", skillName);
        }
    }

    /**
     * 同步挂机任务系统状态
     */
    private void syncAFKTaskSystem() {
        boolean shouldBeEnabled = configManager.isAfkTaskEnabled();

        if (shouldBeEnabled && afkTaskManager == null) {
            afkTaskManager = new AFKTaskManager(this);
            // 仅在 listener 未注册时注册，避免热重载时重复注册
            if (afkTaskListener == null) {
                afkTaskListener = new AFKTaskListener(this);
                getServer().getPluginManager().registerEvents(afkTaskListener, this);
            }
            PluginLogger.info("热重载", "挂机任务系统已初始化");
        } else if (!shouldBeEnabled && afkTaskManager != null) {
            afkTaskManager.shutdown();
            // 注销监听器，防止内存泄漏
            if (afkTaskListener != null) {
                org.bukkit.event.HandlerList.unregisterAll(afkTaskListener);
                afkTaskListener = null;
            }
            afkTaskManager = null;
            PluginLogger.info("热重载", "挂机任务系统已关闭");
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

        // 关闭挂机任务系统
        if (afkTaskManager != null) {
            afkTaskManager.shutdown();
        }

        // 关闭 LLM 管理器（包含所有提供商的连接池）
        if (llmManager != null) {
            llmManager.shutdownAll();
        }
        PluginLogger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        PluginLogger.info("  Kilacraft-AI 已停止运行");
        PluginLogger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
