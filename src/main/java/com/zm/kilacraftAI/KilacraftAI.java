package com.zm.kilacraftAI;

import com.zm.kilacraftAI.core.KilacraftCommand;
import com.zm.kilacraftAI.core.TabCompleter;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.config.PersonalitiesConfigManager;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.listener.ChatListener;
import com.zm.kilacraftAI.knowledge.KnowledgeBaseManager;
import com.zm.kilacraftAI.knowledge.KnowledgeRetriever;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.manager.LLMManager;
import com.zm.kilacraftAI.compat.mythicmobs.MythicMobsPlaceholderManager;
import com.zm.kilacraftAI.skills.bukkit.GenericBukkitAPISkill;
import com.zm.kilacraftAI.skills.framework.SkillManager;
import com.zm.kilacraftAI.skills.framework.SkillIntentRecognizer;
import com.zm.kilacraftAI.skills.framework.spi.SkillRegistry;
import com.zm.kilacraftAI.skills.globalmarketplus.MarketQuerySkill;
import com.zm.kilacraftAI.translate.ItemTranslator;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

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
    private ChatListener chatListener;
    private ConversationManager conversationManager;
    private KnowledgeBaseManager knowledgeBase;
    private KnowledgeRetriever knowledgeRetriever;
    private MythicMobsPlaceholderManager placeholderManager;
    private SkillManager skillManager;
    private SkillIntentRecognizer intentRecognizer;
    @Getter
    private LLMManager llmManager;
    private ItemTranslator itemTranslator;

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置
        saveDefaultConfig();

        // 初始化管理器
        configManager = new ConfigManager(this);
        languageManager = new LanguageManager(this);
        personalitiesConfigManager = new PersonalitiesConfigManager(this);
        conversationManager = new ConversationManager();

        // 初始化知识库管理器
        knowledgeBase = new KnowledgeBaseManager(this, getDataFolder().getAbsolutePath());
        knowledgeBase.loadAllKnowledge();
        
        // 初始化 LLM 管理器（优先级高，其他组件依赖它）
        llmManager = new LLMManager();

        // 初始化知识检索器（从配置读取参数）
        int maxChunks = configManager.getMaxRelevantChunks();
        int maxChunkSize = configManager.getKnowledgeMaxChunkSize();
        int minChunkSize = configManager.getKnowledgeMinChunkSize();
        int chunkOverlap = configManager.getKnowledgeChunkOverlap();
        knowledgeRetriever = new KnowledgeRetriever(knowledgeBase, maxChunks, maxChunkSize, minChunkSize, chunkOverlap);

        // 初始化聊天监听器（需要在 LLM 管理器之后）
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

        // 注册 Plugin Message 通道（用于第三方插件解耦通信）
        getServer().getMessenger().registerOutgoingPluginChannel(this, "kilacraft:ai_response");

        // 注册 MythicMobs 占位符
        placeholderManager = new MythicMobsPlaceholderManager(this);
        placeholderManager.registerPlaceholders();

        // 初始化物品翻译器
        itemTranslator = new ItemTranslator();
        itemTranslator.loadTranslationTable();
        
        // 初始化技能配置管理器
        skillConfigManager = new SkillConfigManager(this);
        
        // 加载所有技能配置
        skillConfigManager.loadAllSkillConfigs();
        
        // 初始化 Skills 系统
        skillManager = new SkillManager();
        registerDefaultSkills();
        
        // 初始化意图识别器
        intentRecognizer = new SkillIntentRecognizer(llmManager.getCurrentProvider(), configManager, skillManager);

        // 延迟发现并注册第三方 SkillProvider 提供的 Skill
        getServer().getScheduler().runTaskLater(this, () -> new SkillRegistry(this, skillManager).discoverAndRegister(), 20L);

        // ASCII Art 启动标志
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
