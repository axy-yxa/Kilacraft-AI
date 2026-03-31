package com.zm.kilacraftAI;

import com.zm.kilacraftAI.api.DeepSeekAPINew;
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
import com.zm.kilacraftAI.compat.mythicmobs.MythicMobsPlaceholderManager;
import com.zm.kilacraftAI.skills.framework.SkillManager;
import com.zm.kilacraftAI.skills.framework.SkillIntentRecognizer;
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
    @Getter
    private DeepSeekAPINew deepSeekAPI;
    private ChatListener chatListener;
    private ConversationManager conversationManager;
    private KnowledgeBaseManager knowledgeBase;
    private KnowledgeRetriever knowledgeRetriever;
    private MythicMobsPlaceholderManager placeholderManager;
    private SkillManager skillManager;
    private SkillIntentRecognizer intentRecognizer;
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
        deepSeekAPI = new DeepSeekAPINew(configManager);
        chatListener = new ChatListener(this);

        // 初始化知识库管理器
        knowledgeBase = new KnowledgeBaseManager(this, getDataFolder().getAbsolutePath());
        knowledgeBase.loadAllKnowledge();

        // 初始化知识检索器（从配置读取最大返回数量）
        int maxChunks = configManager.getMaxRelevantChunks();
        knowledgeRetriever = new KnowledgeRetriever(knowledgeBase, maxChunks);

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

        // 输出知识库统计信息
        if (knowledgeBase != null) {
            getLogger().info(knowledgeBase.getStatistics());
        }

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
        
        // 初始化意图识别器（传入 skillManager）
        intentRecognizer = new SkillIntentRecognizer(deepSeekAPI, configManager, skillManager);
        getLogger().info("已初始化 LLM 意图识别器（动态技能描述）");

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
        getLogger().info("已注册 " + skillManager.getAllSkills().size() + " 个技能");
    }

    @Override
    public void onDisable() {
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        getLogger().info("  Kilacraft-AI 已停止运行");
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // 关闭 HTTP 连接池
        if (deepSeekAPI != null) {
            deepSeekAPI.shutdown();
        }
    }
}
