package com.zm.kilacraftAI.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 配置管理
 *
 * @author Zm_Mmm
 * @since 2026-03-24 17:22:28
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    @Getter
    private String apiKey;
    @Getter
    private String apiUrl;
    @Getter
    private String model;
    @Getter
    private double temperature;
    @Getter
    private int maxTokens;
    @Getter
    private boolean enableChatCommand;
    @Getter
    private int cooldownSeconds;
    @Getter
    private int pluginsCooldownSeconds;
    @Getter
    private boolean debugMode;
    @Getter
    private int maxHistory;
    @Getter
    private boolean enableTrigger;
    @Getter
    private List<String> triggerKeywords;
    @Getter
    private boolean enableStreamOutput;
    @Getter
    private List<String> allowedWorlds;
    @Getter
    private List<String> bannedWorlds;
    @Getter
    private String systemPrompt;
    @Getter
    private String aiPrefix;
    @Getter
    private String aiName;
    @Getter
    private String thinkingMessage;
    @Getter
    private boolean knowledgeEnabled;
    @Getter
    private int maxRelevantChunks;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        // 加载配置文件，配置项不存在时使用默认配置
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
    
        // API 配置
        this.apiKey = config.getString("api.key", "sk-afbe212f24ca4014bcb8f6a152904677");
        this.apiUrl = config.getString("api.url", "https://api.deepseek.com/v1/chat/completions");
        this.model = config.getString("api.model", "deepseek-chat");
        this.temperature = config.getDouble("api.temperature", 0.7);
        this.maxTokens = config.getInt("api.max_tokens", 1000);
    
        // 插件设置
        this.debugMode = config.getBoolean("settings.debug_mode", false);
        this.enableChatCommand = config.getBoolean("settings.enable_chat_command", true);
        this.enableTrigger = config.getBoolean("settings.enable_trigger", true);
        String keywordsStr = config.getString("settings.trigger_keywords", "@kila,@ai,@zm");
        this.triggerKeywords = Arrays.asList(keywordsStr.split(","));
        this.enableStreamOutput = config.getBoolean("settings.enable_stream_output", false);
        this.cooldownSeconds = config.getInt("settings.cooldown_seconds", 5);
        this.pluginsCooldownSeconds = config.getInt("settings.plugins_cooldown_seconds", 5);
        this.maxHistory = config.getInt("settings.max_history", 10);
        this.allowedWorlds = config.getStringList("settings.allowed_worlds");
        if (this.allowedWorlds.isEmpty()) {
            this.allowedWorlds = new ArrayList<>();
        }
        this.bannedWorlds = config.getStringList("settings.banned_worlds");
        if (this.bannedWorlds.isEmpty()) {
            this.bannedWorlds = new ArrayList<>();
        }
        this.systemPrompt = config.getString("settings.system_prompt", "你是一个 Minecraft 游戏助手，正在和玩家 {player} 对话。请用友好、有趣的方式回答，可以提到 Minecraft 游戏相关的内容。");
            
        // 消息格式配置
        this.aiName = config.getString("messages.ai_name", "Kilacraft-AI");
        this.aiPrefix = config.getString("messages.ai_prefix", "§7[Kilacraft-AI] §f");
        this.thinkingMessage = config.getString("messages.thinking_message", "正在思考中...");
            
        // 知识库配置
        this.knowledgeEnabled = config.getBoolean("knowledge.enabled", true);
        this.maxRelevantChunks = config.getInt("knowledge.max_relevant_chunks", 3);
            
        // 通知 DeepSeekAPI 刷新配置缓存
        refreshAPICache();
    }
        
    /**
     * 刷新 API 配置缓存（由 DeepSeekAPI 使用）
     */
    private void refreshAPICache() {
        try {
            com.zm.kilacraftAI.KilacraftAI plugin = com.zm.kilacraftAI.KilacraftAI.getInstance();
            if (plugin != null && plugin.getDeepSeekAPI() != null) {
                plugin.getDeepSeekAPI().refreshConfigCache();
            }
        } catch (Exception e) {
            // 忽略异常，避免配置加载失败
        }
    }
}