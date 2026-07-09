package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.i18n.I18nService;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 配置管理
 *
 * <p>主配置管理器，同时代理 LLM/Agent、输出管线、知识库、问候 4 个子 Manager 的 getter。</p>
 * <p>消费方通过 {@code plugin.getConfigManager().getXxx()} 调用，无需感知底层拆分。</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-24
 */
public class ConfigManager {

    private final JavaPlugin plugin;

    @Getter
    private final LLMConfigManager llmConfigManager;
    @Getter
    private final OutputConfigManager outputConfigManager;
    @Getter
    private final KnowledgeConfigManager knowledgeConfigManager;
    @Getter
    private final GreetingConfigManager greetingConfigManager;

    @Getter
    private boolean enableChatCommand;
    @Getter
    private int cooldownSeconds;
    @Getter
    private int pluginsCooldownSeconds;
    @Getter
    private int callbackTimeoutSeconds;
    @Getter
    private boolean debugMode;
    @Getter
    private int maxHistory;
    @Getter
    private boolean enableTrigger;
    @Getter
    private List<String> triggerKeywords;
    @Getter
    private boolean publicReply;
    @Getter
    private List<String> allowedWorlds;
    @Getter
    private List<String> bannedWorlds;
    @Getter
    private String aiPrefix;
    @Getter
    private String aiName;
    @Getter
    private String thinkingMessage;

    // 语言配置
    @Getter
    private String language;

    // 命令执行技能配置
    @Getter
    private boolean commandSkillEnabled;

    // 安全配置
    @Getter
    private boolean securityPlayerIsolationEnabled;
    @Getter
    private List<String> securityAllowedActions;
    @Getter
    private int securityOfflineCacheTtlMinutes;
    @Getter
    private int securityOfflineCacheMaxSize;
    @Getter
    private boolean securityOfflineCachePreload;
    @Getter
    private int securityOfflineCachePreloadDays;

    // 待确认续体配置（二次确认 / 补参：needInfo 时框架快照参数，下轮恢复，参数不经聊天往返）
    @Getter
    private boolean pendingResumeEnabled;
    @Getter
    private int pendingResumeTtlSeconds;
    @Getter
    private int pendingResumeMaxRounds;

    // 社交关系配置
    @Getter
    private List<String> socialSkillWhitelist;

    // 内置词汇表相关
    @Getter
    private List<String> internalDictionaryWords;
    @Getter
    private List<String> allDictionaryWords;
    private String vocabularyLoadedLanguage = null;

    public double getTemperature() {
        return llmConfigManager.getTemperature();
    }

    public int getMaxTokens() {
        return llmConfigManager.getMaxTokens();
    }

    /** 全局 LLM 预算/熔断阈值（D6/D13），转发 llm.yml。 */
    public int getLlmBudgetPerHour() {
        return llmConfigManager.getBudgetPerPlayerPerHour();
    }

    public String getSystemPrompt() {
        return llmConfigManager.getSystemPromptByLanguage(isChinese(), getDefaultSystemPrompt());
    }

    public String getLlmApiKey() {
        return llmConfigManager.getApiKey();
    }

    public String getLlmApiUrl() {
        return llmConfigManager.getApiUrl();
    }

    public String getLlmModel() {
        return llmConfigManager.getModel();
    }

    public boolean isApiKeyConfigured() {
        return llmConfigManager.isApiKeyConfigured();
    }

    // Agent 代理 getter
    public boolean isAgentEnabled() {
        return llmConfigManager.isAgentEnabled();
    }

    public boolean isAgentEnableChatListener() {
        return llmConfigManager.isAgentEnableChatListener();
    }

    public boolean isAgentEnableCommand() {
        return llmConfigManager.isAgentEnableCommand();
    }

    public int getAgentIntentHistoryCount() {
        return llmConfigManager.getAgentIntentHistoryCount();
    }

    public int getAgentAnalysisHistoryCount() {
        return llmConfigManager.getAgentAnalysisHistoryCount();
    }

    public String getAgentSystemPrompt() {
        return llmConfigManager.getAgentSystemPromptByLanguage(isChinese(), "");
    }

    public String getAgentAnalysisPromptSuffix() {
        return llmConfigManager.getAgentAnalysisPromptSuffixByLanguage(isChinese(), "");
    }

    public boolean isKnowledgeEnabled() {
        return knowledgeConfigManager.isEnabled();
    }

    public int getMaxRelevantChunks() {
        return knowledgeConfigManager.getMaxRelevantChunks();
    }

    public int getKnowledgeMaxChunkSize() {
        return knowledgeConfigManager.getMaxChunkSize();
    }

    public int getKnowledgeMinChunkSize() {
        return knowledgeConfigManager.getMinChunkSize();
    }

    public int getKnowledgeChunkOverlap() {
        return knowledgeConfigManager.getChunkOverlap();
    }

    public int getKeywordTopK() {
        return knowledgeConfigManager.getKeywordTopK();
    }

    public double getBm25K1() {
        return knowledgeConfigManager.getBm25K1();
    }

    public double getBm25B() {
        return knowledgeConfigManager.getBm25B();
    }

    public double getRetrievalNoiseFloor() {
        return knowledgeConfigManager.getRetrievalNoiseFloor();
    }

    public double getRetrievalRelativeThreshold() {
        return knowledgeConfigManager.getRetrievalRelativeThreshold();
    }

    public double getRetrievalRrfK() {
        return knowledgeConfigManager.getRetrievalRrfK();
    }

    public int getBm25AvgDocLength() {
        return knowledgeConfigManager.getBm25AvgDocLength();
    }

    public boolean isEmbeddingEnabled() {
        return knowledgeConfigManager.isEmbeddingEnabled();
    }

    public String getEmbeddingModel() {
        return knowledgeConfigManager.getEmbeddingModel();
    }

    public String getEmbeddingApiUrl() {
        return knowledgeConfigManager.getEmbeddingApiUrl();
    }

    public String getEmbeddingApiKey() {
        return knowledgeConfigManager.getEmbeddingApiKey();
    }

    public int getEmbeddingDimensions() {
        return knowledgeConfigManager.getEmbeddingDimensions();
    }

    public double getEmbeddingMinSimilarity() {
        return knowledgeConfigManager.getEmbeddingMinSimilarity();
    }

    public int getEmbeddingTimeoutSeconds() {
        return knowledgeConfigManager.getEmbeddingTimeoutSeconds();
    }

    public boolean isEmbeddingCacheEnabled() {
        return knowledgeConfigManager.isEmbeddingCacheEnabled();
    }

    public boolean isCustomDictionaryEnabled() {
        return knowledgeConfigManager.isCustomDictionaryEnabled();
    }

    public List<String> getCustomDictionaryWords() {
        return knowledgeConfigManager.getCustomDictionaryWords();
    }

    public boolean isGreetingEnabled() {
        return greetingConfigManager.isEnabled();
    }

    public int getGreetingDelayTicks() {
        return greetingConfigManager.getDelayTicks();
    }

    public String getGreetingFirstLoginPrompt() {
        return greetingConfigManager.getFirstLoginPrompt();
    }

    public String getGreetingReturningPrompt() {
        return greetingConfigManager.getReturningLoginPrompt();
    }

    public int getGreetingMaxOwnOfflineEvents() {
        return greetingConfigManager.getMaxOwnOfflineEvents();
    }

    public int getGreetingMaxFriendOfflineEvents() {
        return greetingConfigManager.getMaxFriendOfflineEvents();
    }

    public int getGreetingMaxSummaryEvents() {
        return greetingConfigManager.getMaxSummaryEvents();
    }

    public String getGreetingServerInfo() {
        return greetingConfigManager.getServerInfo();
    }

    public int getGreetingCooldownMinutes() {
        return greetingConfigManager.getGreetingCooldownMinutes();
    }

    public boolean isProfileInjectionEnabled() {
        return greetingConfigManager.isProfileInjectionEnabled();
    }

    public boolean isCommandSkillEnabled() {
        return commandSkillEnabled;
    }

    public boolean isPendingResumeEnabled() {
        return pendingResumeEnabled;
    }

    public boolean isSecurityPlayerIsolationEnabled() {
        return securityPlayerIsolationEnabled;
    }

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.llmConfigManager = new LLMConfigManager((KilacraftAI) plugin);
        this.knowledgeConfigManager = new KnowledgeConfigManager((KilacraftAI) plugin);
        this.greetingConfigManager = new GreetingConfigManager((KilacraftAI) plugin);
        this.outputConfigManager = new OutputConfigManager((KilacraftAI) plugin);
        loadConfig();
    }

    public void loadConfig() {
        // 加载主配置文件
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // 插件设置（主配置保留部分）
        this.debugMode = config.getBoolean("settings.debug_mode", false);
        this.language = config.getString("settings.language", "zh");
        this.enableChatCommand = config.getBoolean("settings.enable_chat_command", true);
        this.enableTrigger = config.getBoolean("settings.enable_trigger", true);
        String keywordsStr = config.getString("settings.trigger_keywords", "@kila,@ai,@zm");
        this.triggerKeywords = Arrays.asList(keywordsStr.split(","));
        this.publicReply = config.getBoolean("settings.public_reply", false);
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

        // 消息格式配置
        this.aiName = config.getString("messages.ai_name", "Kilacraft-AI");
        this.aiPrefix = config.getString("messages.ai_prefix", "§7[Kilacraft-AI] §f");
        this.thinkingMessage = getLocalizedString(config, "messages.thinking_message", "正在思考中...");

        // 命令执行技能配置
        this.commandSkillEnabled = config.getBoolean("command_skill.enabled", false);

        // 安全配置
        this.securityPlayerIsolationEnabled = config.getBoolean("security.player_isolation.enabled", true);
        this.securityAllowedActions = config.getStringList("security.player_isolation.allowed_actions");
        this.securityOfflineCacheTtlMinutes = config.getInt("security.player_isolation.offline_cache.ttl_minutes", 30);
        this.securityOfflineCacheMaxSize = config.getInt("security.player_isolation.offline_cache.max_size", 5000);
        this.securityOfflineCachePreload = config.getBoolean("security.player_isolation.offline_cache.preload_on_start", true);
        this.securityOfflineCachePreloadDays = config.getInt("security.player_isolation.offline_cache.preload_window_days", 7);

        // 待确认续体配置
        this.pendingResumeEnabled = config.getBoolean("pending_resume.enabled", true);
        this.pendingResumeTtlSeconds = config.getInt("pending_resume.ttl_seconds", 300);
        this.pendingResumeMaxRounds = config.getInt("pending_resume.max_rounds", 5);

        // 社交关系配置
        this.socialSkillWhitelist = config.getStringList("social.skill_whitelist");
        if (this.socialSkillWhitelist.isEmpty()) {
            this.socialSkillWhitelist = List.of("market_action", "cmi", "Guardian");
        }

        // 加载子 Manager 配置（语言已从主配置读取，传递给需要语言感知的子 Manager）
        llmConfigManager.loadConfig();
        knowledgeConfigManager.loadConfig(this.language);
        greetingConfigManager.loadConfig(this.language);
        outputConfigManager.loadConfig();

        // 加载内置词汇表（语言变化时重新加载）
        if (vocabularyLoadedLanguage == null || !vocabularyLoadedLanguage.equals(this.language)) {
            this.internalDictionaryWords = loadInternalVocabulary();
            vocabularyLoadedLanguage = this.language;
        }

        if (this.internalDictionaryWords == null) {
            this.internalDictionaryWords = new ArrayList<>();
        }

        // 合并所有词汇（内置+自定义），自动去重
        this.allDictionaryWords = knowledgeConfigManager.mergeDictionaryWords(internalDictionaryWords);

        // 通知 LLM 管理器刷新配置缓存
        refreshLLMConfigCache();
    }

    /**
     * 刷新 LLM 配置缓存
     */
    public void refreshLLMConfigCache() {
        try {
            KilacraftAI plugin = KilacraftAI.getInstance();
            if (plugin != null && plugin.getLlmManager() != null) {
                plugin.getLlmManager().refreshProviderConfig();
            }
        } catch (Exception e) {
            PluginLoggerUtil.warn("配置管理", I18nService.tr("刷新 LLM 配置缓存时发生异常: {}", e.getMessage()), e);
        }
    }

    /**
     * 将配置的轮数转换为实际的消息数量（1轮=2条消息）
     */
    public int getHistoryMessageCount(int rounds) {
        return Math.max(0, rounds * 2);
    }

    /**
     * 检查当前语言是否为中文
     */
    public boolean isChinese() {
        return "zh".equals(language);
    }

    /**
     * 获取 AI 输出语言约束指令
     *
     * <p>当非中文模式时，返回一条强制 AI 使用配置语言输出的指令，
     * 防止第三方 SPI Skill 的多语言数据干扰输出语言。
     * 中文模式时返回 null（中文是默认语言，无需额外约束）。</p>
     *
     * @return 语言约束指令，或 null（中文模式）
     */
    public String getLanguageDirective() {
        if ("zh".equals(language)) {
            return null;
        }
        // TODO 未来支持更多语言时，在此扩展
        // 目前仅支持 en
        return "[Language Requirement] You MUST respond in English. Regardless of the language used in the data, context, or skill results provided to you, your final output to the player must always be in English.";
    }

    /**
     * 获取默认系统提示词（当 llm.yml 未配置时的回退值）
     */
    private String getDefaultSystemPrompt() {
        if (!isChinese()) {
            return """
                    You are a Minecraft game assistant, currently talking to player {player}. Please respond in a concise and plain manner, no more than 150 words. You may mention Minecraft-related content. Do not address the player by name in your responses. Do not use exclamation marks or exaggerated tone.
                    [Operation Declaration Rules] You must NOT claim that you have executed any in-game operations unless you receive explicit success information from the skill system. Strictly avoid using phrases like "I'll help you", "already done", "success" or any other wording that implies an operation has been completed when there is no execution result.
                    [Skill System Fallback] The skill system uniformly tags results with [SUCCESS]/[FAILURE]/[NEED_INFO]/[SKIPPED]. When the user message contains these markers: [FAILURE]=failed, [NEED_INFO]=needs the player to supply info or confirm, [SKIPPED]=step skipped. Explain or relay the content to the player naturally (for [NEED_INFO], convey what needs confirming/supplementing). If the [Stats] line shows a "need-confirm" count, those steps are awaiting the player's input or confirmation (NOT failures) — convey what needs confirming rather than reporting an error. Never mention "system prompt" or internal mechanisms, and never expose these markers to the player.
                    [Currency Unit] The server's economy uses $ as the currency symbol (e.g., $100.00). Never use "emeralds", "emerald" or any other Minecraft item names to refer to currency. All amounts are in $ currency unit.""";
        }
        return """
                你是一个 Minecraft 游戏助手，正在和玩家 {player} 对话。请用简洁、平实的方式回答，输出不超过200个汉字。可以提到 Minecraft 游戏相关的内容。不要在回复中称呼玩家名字。不要使用感叹号、波浪号等夸张语气。
                【操作声明规范】你不得自行声称已执行任何游戏内操作，除非你收到了技能系统返回的明确成功信息。严禁在没有执行结果的情况下使用'我帮你'、'已经'、'成功'等暗示操作已完成的措辞。
                【技能系统回退】技能系统统一用 [SUCCESS]/[FAILURE]/[NEED_INFO]/[SKIPPED] 标记执行结果。当用户消息中附带这些标记时：[FAILURE] 表示失败、[NEED_INFO] 表示需要玩家补全信息或二次确认、[SKIPPED] 表示该步骤被跳过。直接根据标记与内容用自然语言向玩家解释或转述（如遇 [NEED_INFO] 则把待确认/待补充的信息转达给玩家）。[统计] 行若出现"需确认"项，表示对应步骤正在等待玩家补充或确认（并非失败），应转达待确认内容而非向玩家报错。不得提及'系统提示'或内部机制，也不得向玩家暴露这些标记本身。
                【货币单位】本服经济系统的货币符号为 $（如 $100.00）。绝对不要使用'绿宝石'、'emerald'或其他 Minecraft 物品名称指代货币，所有金额都是 $ 货币单位.""";
    }

    /**
     * 加载内置词汇表
     */
    private List<String> loadInternalVocabulary() {
        List<String> words = new ArrayList<>();
        try {
            loadVocabularyFromJar(words);
        } catch (Exception e) {
            PluginLoggerUtil.warn("配置管理", I18nService.tr("加载内置词汇表时发生异常: {}", e.getMessage()), e);
        }
        return words;
    }

    /**
     * 从 JAR 包中加载内置词汇表
     */
    private void loadVocabularyFromJar(List<String> words) {
        try {
            File jarFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());

            if (!jarFile.exists()) {
                PluginLoggerUtil.warn("配置管理", "无法找到插件 JAR 文件，跳过加载词汇表");
                return;
            }

            String prefix = "en".equals(this.language) ? "internal/vocabulary/en/" : "internal/vocabulary/";

            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (name.startsWith(prefix) && name.endsWith(".txt") && !entry.isDirectory()) {
                        String relativePath = name.substring(prefix.length());
                        if (relativePath.contains("/")) continue;

                        try (InputStream is = plugin.getResource(name); BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                String trimmedLine = line.trim();
                                if (!trimmedLine.isEmpty()) {
                                    words.add(trimmedLine);
                                }
                            }
                        }
                    }
                }
            }
        } catch (java.net.URISyntaxException e) {
            PluginLoggerUtil.warn("配置管理", I18nService.tr("无法解析 JAR 文件路径: {}，跳过加载", e.getMessage()), e);
        } catch (Exception e) {
            PluginLoggerUtil.warn("配置管理", I18nService.tr("加载内置词汇表失败: {}", e.getMessage()), e);
        }
    }

    /**
     * 按语言读取配置中的本地化字符串
     * <p>用于主配置文件中仍需本地化的字段（如 messages.thinking_message）。</p>
     */
    private String getLocalizedString(FileConfiguration config, String key, String fallbackDefault) {
        String lang = this.language;
        if ("en".equals(lang)) {
            String enKey = key + "_en";
            String enValue = config.getString(enKey, "");
            if (!enValue.isEmpty()) {
                return enValue;
            }
        }
        return config.getString(key, fallbackDefault);
    }
}
