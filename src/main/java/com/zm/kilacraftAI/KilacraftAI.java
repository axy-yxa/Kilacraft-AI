package com.zm.kilacraftAI;

import com.zm.kilacraftAI.api.DeepSeekAPI;
import com.zm.kilacraftAI.core.KilacraftCommand;
import com.zm.kilacraftAI.core.TabCompleter;
import com.zm.kilacraftAI.config.ConfigManager;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.config.PersonalitiesConfigManager;
import com.zm.kilacraftAI.listener.ChatListener;
import com.zm.kilacraftAI.knowledge.KnowledgeBaseManager;
import com.zm.kilacraftAI.knowledge.KnowledgeRetriever;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.compat.mythicmobs.MythicMobsPlaceholderManager;
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
    private DeepSeekAPI deepSeekAPI;
    private ChatListener chatListener;
    private ConversationManager conversationManager;
    private KnowledgeBaseManager knowledgeBase;
    private KnowledgeRetriever knowledgeRetriever;
    private MythicMobsPlaceholderManager placeholderManager;

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
        deepSeekAPI = new DeepSeekAPI(configManager);
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

        // ASCII Art 启动标志
        getLogger().info("╻┏ ╻╻  ┏━┓┏━╸┏━┓┏━┓┏━╸╺┳╸   ┏━┓╻");
        getLogger().info("┣┻┓┃┃  ┣━┫┃  ┣┳┛┣━┫┣╸  ┃ ╺━╸┣━┫┃");
        getLogger().info("╹ ╹╹┗━╸╹ ╹┗━╸╹┗╸╹ ╹╹   ╹    ╹ ╹╹");
        getLogger().info("版本：v" + getDescription().getVersion());
        getLogger().info("作者：Zm_Mmm");
    }

    @Override
    public void onDisable() {
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        getLogger().info("  Kilacraft-AI 已停止运行");
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
