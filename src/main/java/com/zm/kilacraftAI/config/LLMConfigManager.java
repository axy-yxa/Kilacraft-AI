package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * LLM 与 Agent 配置管理器
 *
 * <p>管理独立的 llm.yml 配置文件，支持热重载。</p>
 *
 * @author Zm_Mmm
 */
public class LLMConfigManager {

    private static final String CONFIG_FILE = "llm.yml";

    private final KilacraftAI plugin;
    private File configFile;

    // LLM 基础配置
    @Getter
    private String apiUrl;
    @Getter
    private String apiKey;
    @Getter
    private String model;
    @Getter
    private double temperature;
    @Getter
    private int maxTokens;
    @Getter
    private String systemPrompt;
    @Getter
    private String systemPromptEn;

    // Agent 配置
    @Getter
    private boolean agentEnabled;
    @Getter
    private boolean agentEnableChatListener;
    @Getter
    private boolean agentEnableCommand;
    @Getter
    private int agentIntentHistoryCount;
    @Getter
    private int agentAnalysisHistoryCount;
    @Getter
    private String agentSystemPrompt;
    @Getter
    private String agentSystemPromptEn;
    @Getter
    private String agentAnalysisPromptSuffix;
    @Getter
    private String agentAnalysisPromptSuffixEn;

    public LLMConfigManager(KilacraftAI plugin) {
        this.plugin = plugin;
        ConfigResourceUtil.saveDefaultResource(plugin, CONFIG_FILE);
    }

    public void loadConfig() {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        // LLM 基础配置
        this.apiUrl = yaml.getString("llm.api_url", "https://api.deepseek.com/v1/chat/completions");
        this.apiKey = yaml.getString("llm.api_key", "");
        this.model = yaml.getString("llm.model", "deepseek-chat");
        this.temperature = yaml.getDouble("llm.temperature", 0.7);
        this.maxTokens = yaml.getInt("llm.max_tokens", 600);
        this.systemPrompt = yaml.getString("llm.system_prompt", "");
        this.systemPromptEn = yaml.getString("llm.system_prompt_en", "");

        // Agent 配置
        this.agentEnabled = yaml.getBoolean("agent.enabled", true);
        this.agentEnableChatListener = yaml.getBoolean("agent.enable_chat_listener", true);
        this.agentEnableCommand = yaml.getBoolean("agent.enable_command", true);
        this.agentIntentHistoryCount = yaml.getInt("agent.intent_history_count", 5);
        this.agentAnalysisHistoryCount = yaml.getInt("agent.analysis_history_count", 2);
        this.agentSystemPrompt = yaml.getString("agent.prompts.system_prompt", "");
        this.agentSystemPromptEn = yaml.getString("agent.prompts.system_prompt_en", "");
        this.agentAnalysisPromptSuffix = yaml.getString("agent.prompts.analysis_prompt_suffix", "");
        this.agentAnalysisPromptSuffixEn = yaml.getString("agent.prompts.analysis_prompt_suffix_en", "");
    }

    /**
     * 热重载配置
     */
    public void reload() {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        loadConfig();
    }

    /**
     * 按语言获取系统提示词
     *
     * @param isChinese       是否中文模式
     * @param fallbackDefault 回退默认值
     * @return 系统提示词
     */
    public String getSystemPromptByLanguage(boolean isChinese, String fallbackDefault) {
        if (!isChinese) {
            String enValue = systemPromptEn;
            if (enValue != null && !enValue.isEmpty()) {
                return enValue;
            }
        }
        String value = systemPrompt;
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return fallbackDefault;
    }

    /**
     * 按语言获取 Agent 系统提示词
     *
     * @param isChinese       是否中文模式
     * @param fallbackDefault 回退默认值
     * @return Agent 系统提示词
     */
    public String getAgentSystemPromptByLanguage(boolean isChinese, String fallbackDefault) {
        if (!isChinese) {
            String enValue = agentSystemPromptEn;
            if (enValue != null && !enValue.isEmpty()) {
                return enValue;
            }
        }
        String value = agentSystemPrompt;
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return fallbackDefault;
    }

    /**
     * 按语言获取 Agent 分析提示词后缀
     *
     * @param isChinese       是否中文模式
     * @param fallbackDefault 回退默认值
     * @return Agent 分析提示词后缀
     */
    public String getAgentAnalysisPromptSuffixByLanguage(boolean isChinese, String fallbackDefault) {
        if (!isChinese) {
            String enValue = agentAnalysisPromptSuffixEn;
            if (enValue != null && !enValue.isEmpty()) {
                return enValue;
            }
        }
        String value = agentAnalysisPromptSuffix;
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return fallbackDefault;
    }

    /**
     * 检查 API Key 是否已配置（非默认占位符）
     */
    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isEmpty() && !"your-api-key".equals(apiKey);
    }
}
