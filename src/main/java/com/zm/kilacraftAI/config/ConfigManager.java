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
    private int callbackTimeoutSeconds;  // 回调命令超时时间（秒）
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
    @Getter
    private int knowledgeMaxChunkSize;      // 每个片段最大字符数
    @Getter
    private int knowledgeMinChunkSize;      // 每个片段最小字符数
    @Getter
    private int knowledgeChunkOverlap;      // 片段重叠字符数
    
    // Agent 能力配置
    @Getter
    private boolean agentEnabled;           // Agent 总开关
    @Getter
    private boolean agentEnableChatListener;  // ChatListener 入口是否启用 Agent
    @Getter
    private boolean agentEnableCommand;       // KilacraftCommand 入口是否启用 Agent
    @Getter
    private int agentIntentHistoryCount;      // 意图识别时的历史对话数
    @Getter
    private int agentAnalysisHistoryCount;    // 二次分析时的历史对话数
    @Getter
    private String agentSystemPrompt;         // LLM 意图识别系统提示词
    @Getter
    private String agentAnalysisPrompt;       // LLM 分析执行结果提示词
    
    // LLM 提供商配置（通用）
    @Getter
    private String llmApiKey;                  // LLM API 密钥
    @Getter
    private String llmApiUrl;                  // LLM API 地址
    @Getter
    private String llmModel;                   // LLM 模型名称

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        // 加载配置文件，配置项不存在时使用默认配置
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
    
        // 通用配置
        this.temperature = config.getDouble("llm.temperature", 0.7);
        this.maxTokens = config.getInt("llm.max_tokens", 1000);
    
        // 插件设置
        this.debugMode = config.getBoolean("settings.debug_mode", false);
        this.enableChatCommand = config.getBoolean("settings.enable_chat_command", true);
        this.enableTrigger = config.getBoolean("settings.enable_trigger", true);
        String keywordsStr = config.getString("settings.trigger_keywords", "@kila,@ai,@zm");
        this.triggerKeywords = Arrays.asList(keywordsStr.split(","));
        this.enableStreamOutput = config.getBoolean("settings.enable_stream_output", false);
        this.cooldownSeconds = config.getInt("settings.cooldown_seconds", 5);
        this.pluginsCooldownSeconds = config.getInt("settings.plugins_cooldown_seconds", 5);
        this.callbackTimeoutSeconds = config.getInt("plugin_command.callback_timeout_seconds", 3);
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
        
        // 知识库分段配置
        this.knowledgeMaxChunkSize = config.getInt("knowledge.segment.max_size", 500);
        this.knowledgeMinChunkSize = config.getInt("knowledge.segment.min_size", 25);
        this.knowledgeChunkOverlap = config.getInt("knowledge.segment.overlap", 30);
            
        // Agent 能力配置
        this.agentEnabled = config.getBoolean("agent.enabled", true);
        this.agentEnableChatListener = config.getBoolean("agent.enable_chat_listener", true);
        this.agentEnableCommand = config.getBoolean("agent.enable_command", true);
        this.agentIntentHistoryCount = config.getInt("agent.intent_history_count", 5);
        this.agentAnalysisHistoryCount = config.getInt("agent.analysis_history_count", 2);
        this.agentSystemPrompt = config.getString("agent.prompts.system_prompt", "");
        this.agentAnalysisPrompt = config.getString("agent.prompts.analysis_prompt", "");
            
        // LLM 提供商配置（通用）
        this.llmApiKey = config.getString("llm.api_key", "");
        this.llmApiUrl = config.getString("llm.api_url", "https://api.deepseek.com/v1/chat/completions");
        this.llmModel = config.getString("llm.model", "deepseek-chat");
            
        // 通知 LLM 管理器刷新配置缓存
        refreshLLMConfigCache();
    }
        
    /**
     * 刷新 LLM 配置缓存
     */
    public void refreshLLMConfigCache() {
        try {
            com.zm.kilacraftAI.KilacraftAI plugin = com.zm.kilacraftAI.KilacraftAI.getInstance();
            if (plugin != null && plugin.getLlmManager() != null) {
                plugin.getLlmManager().refreshProviderConfig();
            }
        } catch (Exception e) {
            // 忽略异常，避免配置加载失败导致插件崩溃
            com.zm.kilacraftAI.KilacraftAI.getInstance().getLogger().warning("[ConfigManager] 刷新 LLM 配置缓存时发生异常: " + e.getMessage());
        }
    }

    /**
     * 将配置的轮数转换为实际的消息数量（1轮=2条消息）
     * @param rounds 轮数
     * @return 实际消息数量
     */
    public int getHistoryMessageCount(int rounds) {
        return Math.max(0, rounds * 2);
    }
}