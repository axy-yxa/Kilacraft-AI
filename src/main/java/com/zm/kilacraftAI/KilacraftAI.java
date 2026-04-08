package com.zm.kilacraftAI;

import com.zm.kilacraftAI.core.KilacraftCommand;
import com.zm.kilacraftAI.core.TabCompleter;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.IntentPromptConfigManager;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.config.PersonalitiesConfigManager;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.listener.ChatListener;
import com.zm.kilacraftAI.knowledge.KnowledgeBaseManager;
import com.zm.kilacraftAI.knowledge.KnowledgeRetriever;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.manager.LLMManager;

import com.zm.kilacraftAI.skills.bukkit.GenericBukkitAPISkill;
import com.zm.kilacraftAI.skills.framework.SkillManager;
import com.zm.kilacraftAI.skills.framework.SkillIntentRecognizer;
import com.zm.kilacraftAI.skills.framework.spi.SkillRegistry;
import com.zm.kilacraftAI.skills.globalmarketplus.MarketQuerySkill;
import com.zm.kilacraftAI.translate.ItemTranslator;
import com.zm.kilacraftAI.util.ChineseTextUtil;
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

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        initializeManagers();
        initializeKnowledgeSystem();
        initializeChatAndCommands();
        registerMythicMobsPlaceholders();
        initializeSkillsSystem();
        printStartupBanner();
    }

    /**
     * 初始化基础管理器
     */
    private void initializeManagers() {
        configManager = new ConfigManager(this);
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
        knowledgeRetriever = new KnowledgeRetriever(
                knowledgeBase,
                configManager.getMaxRelevantChunks(),
                configManager.getKnowledgeMaxChunkSize(),
                configManager.getKnowledgeMinChunkSize(),
                configManager.getKnowledgeChunkOverlap(),
                configManager.getKeywordTopK(),
                configManager.getBm25K1(),
                configManager.getBm25B());

        // 自定义词典（依赖 configManager）
        if (configManager.isCustomDictionaryEnabled()) {
            ChineseTextUtil.initCustomDictionary(configManager.getAllDictionaryWords());
            getLogger().info("已加载 " + configManager.getInternalDictionaryWords().size() + " 个内置词汇");
            List<String> customWords = configManager.getCustomDictionaryWords();
            getLogger().info("已加载 " + (customWords != null ? customWords.size() : 0) + " 个自定义词汇");
        }

        // 物品翻译器（无外部依赖）
        itemTranslator = new ItemTranslator();
        itemTranslator.loadTranslationTable();
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
            getLogger().severe("无法注册命令：kilacraft，请检查 plugin.yml 配置");
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
                getLogger().info("MythicMobs 占位符注册成功");
            } else {
                getLogger().warning("当前 JDK 版本为 " + javaVersion + "，MythicMobs 需要 Java 21+，跳过占位符注册");
            }
        } catch (ClassNotFoundException e) {
            getLogger().severe("MythicMobs 兼容模块缺失，请检查 JAR 包完整性");
        } catch (Exception e) {
            getLogger().severe("MythicMobs 占位符注册失败：" + e.getMessage());
            if (configManager != null && configManager.isDebugMode()) {
                e.printStackTrace();
            }
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

        // 意图识别器（依赖 configManager + intentPromptConfigManager + skillManager）
        intentRecognizer = new SkillIntentRecognizer(configManager, intentPromptConfigManager, skillManager);

        // 延迟发现并注册第三方 SkillProvider 提供的 Skill
        getServer().getScheduler().runTaskLater(this, () -> new SkillRegistry(this, skillManager).discoverAndRegister(), 20L);
    }

    /**
     * 打印启动标志
     */
    private void printStartupBanner() {
        getLogger().info("╻┏ ╻╻  ┏━┓┏━╸┏━┓┏━┓┏━╸╺┳╸   ┏━┓╻");
        getLogger().info("┣┻┓┃┃  ┣━┫┃  ┣┳┛┣━┫┣╸  ┃ ╺━╸┣━┫┃");
        getLogger().info("╹ ╹╹┗━╸╹ ╹┗━╸╹┗╸╹ ╹╹   ╹    ╹ ╹╹");
        getLogger().info("版本：v" + getDescription().getVersion());
        getLogger().info("作者：Zm_Mmm");
    }
    
    /**
     * 注册默认技能
     */
    private void registerDefaultSkills() {
        // 注册市场查询技能
        skillManager.registerSkill(new MarketQuerySkill());

        // 注册通用 Bukkit API 执行器（数据驱动的原版功能调用)
        skillManager.registerSkill(new GenericBukkitAPISkill());
    }

    @Override
    public void onDisable() {
        // 关闭 LLM 管理器（包含所有提供商的连接池）
        if (llmManager != null) {
            llmManager.shutdownAll();
        }
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        getLogger().info("  Kilacraft-AI 已停止运行");
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
