package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.ConfigResourceUtil;
import com.zm.kilacraftAI.llm.LLMCompatibilityResolver;
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
 * @since 2026-05-07
 */
public class LLMConfigManager {

    private static final String CONFIG_FILE = "llm.yml";

    private final KilacraftAI plugin;
    private File configFile;

    // LLM 基础配置（volatile：主线程 reload 写入 → IO 线程通过 GenericLLMProvider 缓存间接读取，
    // 虽当前无一 IO 线程直读此字段，但加 volatile 消除"配置变更后 IO 线程读到 CPU 缓存过期值"的理论风险）
    @Getter
    private volatile String apiUrl;
    @Getter
    private volatile String apiKey;
    @Getter
    private volatile String model;
    @Getter
    private volatile double temperature;
    @Getter
    private volatile int maxTokens;
    @Getter
    private volatile String systemPrompt;
    @Getter
    private volatile String systemPromptEn;

    // Agent 配置
    @Getter
    private volatile boolean agentEnabled;
    @Getter
    private volatile boolean agentEnableChatListener;
    @Getter
    private volatile boolean agentEnableCommand;
    @Getter
    private volatile int agentIntentHistoryCount;
    @Getter
    private volatile int agentAnalysisHistoryCount;
    @Getter
    private volatile String agentSystemPrompt;
    @Getter
    private volatile String agentSystemPromptEn;
    @Getter
    private volatile String agentAnalysisPromptSuffix;
    @Getter
    private volatile String agentAnalysisPromptSuffixEn;

    // 全局预算/熔断（D6/D13）：正常使用绝对达不到，仅防 runaway。≤0 禁用治理。
    @Getter
    private volatile int budgetPerPlayerPerHour;

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
        this.apiUrl = LLMCompatibilityResolver.resolveApiUrl(yaml.getString("llm.api_url", "https://api.deepseek.com/v1/chat/completions"));
        this.apiKey = yaml.getString("llm.api_key", "");
        this.model = LLMCompatibilityResolver.resolveModel(yaml.getString("llm.model", "deepseek-v4-flash"));
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

        // 全局预算/熔断（D6/D13）。默认 200：正常使用绝对达不到，仅防 runaway。
        this.budgetPerPlayerPerHour = yaml.getInt("llm.budget_per_player_per_hour", 200);
    }

    /**
     * 热重载配置
     */
    public void reload() {
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        loadConfig();
    }

    /**
     * 通用语言回退逻辑：优先英文（非中文模式）、其次中文、最后回退默认值
     */
    private String getByLanguage(boolean isChinese, String zhValue, String enValue, String fallbackDefault) {
        if (!isChinese && enValue != null && !enValue.isEmpty()) {
            return enValue;
        }
        if (zhValue != null && !zhValue.isEmpty()) {
            return zhValue;
        }
        return fallbackDefault;
    }

    /**
     * 按语言获取系统提示词
     */
    public String getSystemPromptByLanguage(boolean isChinese, String fallbackDefault) {
        return getByLanguage(isChinese, systemPrompt, systemPromptEn, fallbackDefault);
    }

    /**
     * 按语言获取 Agent 系统提示词
     */
    public String getAgentSystemPromptByLanguage(boolean isChinese, String fallbackDefault) {
        return getByLanguage(isChinese, agentSystemPrompt, agentSystemPromptEn, fallbackDefault);
    }

    /**
     * 按语言获取 Agent 分析提示词后缀
     */
    public String getAgentAnalysisPromptSuffixByLanguage(boolean isChinese, String fallbackDefault) {
        return getByLanguage(isChinese, agentAnalysisPromptSuffix, agentAnalysisPromptSuffixEn, fallbackDefault);
    }

    /**
     * 检查 API Key 是否已配置（非默认占位符）
     */
    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isEmpty() && !"your-api-key".equals(apiKey);
    }
}
