package com.zm.kilacraftAI.config;

import com.zm.kilacraftAI.util.PluginLogger;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
    private boolean publicReply;                 // 公屏回复开关（关键词触发时AI回复是否对所有玩家可见）
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
    private double minRelevanceScore;            // 最低相关性得分阈值
    @Getter
    private int knowledgeMaxChunkSize;      // 每个片段最大字符数
    @Getter
    private int knowledgeMinChunkSize;      // 每个片段最小字符数
    @Getter
    private int knowledgeChunkOverlap;      // 片段重叠字符数

    // 关键词提取配置
    @Getter
    private int keywordTopK;                 // 每次提取的关键词数量

    // BM25 评分算法配置
    @Getter
    private double bm25K1;                   // k1 参数：控制词频饱和点
    @Getter
    private double bm25B;                    // b 参数：控制文档长度归一化

    // Embedding 语义检索配置
    @Getter
    private boolean embeddingEnabled;         // 是否启用 Embedding 语义检索
    @Getter
    private String embeddingModel;            // Embedding 模型名称
    @Getter
    private String embeddingApiUrl;            // Embedding API 完整 URL
    @Getter
    private String embeddingApiKey;            // Embedding API Key
    @Getter
    private int embeddingDimensions;          // 向量维度
    @Getter
    private double embeddingMinSimilarity;    // 最低相似度阈值（余弦相似度）
    @Getter
    private int embeddingTimeoutSeconds;      // API 调用超时（秒）
    @Getter
    private boolean embeddingCacheEnabled;    // 是否启用向量缓存持久化

    // 自定义词典配置
    @Getter
    private boolean customDictionaryEnabled; // 是否启用自定义词典
    @Getter
    private List<String> customDictionaryWords; // 自定义词汇列表

    @Getter
    private List<String> internalDictionaryWords; // 内置词汇表

    @Getter
    private List<String> allDictionaryWords; // 所有词汇（内置+自定义，去重后）

    // 内置词汇表加载状态：记录上次加载的语言，语言变化时重新加载
    private String vocabularyLoadedLanguage = null;

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
    private String agentAnalysisPromptSuffix; // LLM 分析提示词后缀（用于识别边界）

    // 命令执行技能配置
    @Getter
    private boolean commandSkillEnabled;              // 命令执行技能开关

    // 语言配置
    @Getter
    private String language;                        // 当前语言（zh、en 等，默认 zh）

    // 安全配置
    @Getter
    private boolean securityPlayerIsolationEnabled;   // 玩家数据隔离开关
    @Getter
    private List<String> securityAllowedActions;      // 允许操作其他玩家的白名单

    // 挂机任务配置
    @Getter
    private boolean afkTaskEnabled;            // 挂机任务总开关
    @Getter
    private int afkTaskMaxTasks;               // 最大并发任务数
    @Getter
    private int afkTaskCheckIntervalTicks;     // 定时轮询间隔（ticks）
    @Getter
    private int afkTaskMaxConsecutiveFailures; // 最大连续评估失败次数

    // AI 响应输出管线配置
    @Getter
    private final OutputConfigManager outputConfigManager = new OutputConfigManager();  // 输出载体配置

    // LLM 提供商配置（通用）
    @Getter
    private String llmApiKey;                  // LLM API 密钥

    /**
     * 检查 API Key 是否已配置（非默认占位符）
     *
     * @return true=已配置，false=未配置仍是默认值
     */
    public boolean isApiKeyConfigured() {
        return llmApiKey != null && !llmApiKey.isEmpty() && !"your-api-key".equals(llmApiKey);
    }

    /**
     * 检查当前语言是否为中文
     *
     * @return true=中文模式
     */
    public boolean isChinese() {
        return "zh".equals(language);
    }

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
        this.systemPrompt = getLocalizedString(config, "settings.system_prompt", """
                你是一个 Minecraft 游戏助手，正在和玩家 {player} 对话。请用友好、简洁的方式回答，输出不超过200个汉字。可以提到 Minecraft 游戏相关的内容。不要在回复中称呼玩家名字。
                【操作声明规范】你不得自行声称已执行任何游戏内操作，除非你收到了技能系统返回的明确成功信息。严禁在没有执行结果的情况下使用'我帮你'、'已经'、'成功'等暗示操作已完成的措辞。
                【技能系统回退】当用户消息中附带 [FAILURE]、[NEED_INFO] 等标记或失败/异常信息时，说明技能系统已尝试执行但需要处理，此时直接根据信息内容用自然语言向玩家解释或转述，不得提及'系统提示'或内部机制。
                【货币单位】本服经济系统的货币符号为 $（如 $100.00）。绝对不要使用'绿宝石'、'emerald'或其他 Minecraft 物品名称指代货币，所有金额都是 $ 货币单位。""");

        // 消息格式配置
        this.aiName = config.getString("messages.ai_name", "Kilacraft-AI");
        this.aiPrefix = config.getString("messages.ai_prefix", "§7[Kilacraft-AI] §f");
        this.thinkingMessage = getLocalizedString(config, "messages.thinking_message", "正在思考中...");

        // 知识库配置
        this.knowledgeEnabled = config.getBoolean("knowledge.enabled", true);
        this.maxRelevantChunks = config.getInt("knowledge.max_relevant_chunks", 3);
        this.minRelevanceScore = config.getDouble("knowledge.min_relevance_score", 30.0);

        // 知识库分段配置
        this.knowledgeMaxChunkSize = config.getInt("knowledge.segment.max_size", 500);
        this.knowledgeMinChunkSize = config.getInt("knowledge.segment.min_size", 20);
        this.knowledgeChunkOverlap = config.getInt("knowledge.segment.overlap", 30);

        // 关键词提取配置
        this.keywordTopK = config.getInt("knowledge.keywords.top_k", 10);

        // BM25 评分算法配置
        this.bm25K1 = config.getDouble("knowledge.bm25.k1", 1.5);
        this.bm25B = config.getDouble("knowledge.bm25.b", 0.75);

        // Embedding 语义检索配置
        this.embeddingEnabled = config.getBoolean("knowledge.embedding.enabled", true);
        this.embeddingModel = config.getString("knowledge.embedding.model", "");
        this.embeddingApiUrl = config.getString("knowledge.embedding.api_url", "");
        this.embeddingApiKey = config.getString("knowledge.embedding.api_key", "");
        this.embeddingDimensions = config.getInt("knowledge.embedding.dimensions", 1024);
        this.embeddingMinSimilarity = config.getDouble("knowledge.embedding.min_similarity", 0.5);
        this.embeddingTimeoutSeconds = config.getInt("knowledge.embedding.timeout_seconds", 10);
        this.embeddingCacheEnabled = config.getBoolean("knowledge.embedding.cache_enabled", true);

        // 自定义词典配置
        this.customDictionaryEnabled = config.getBoolean("knowledge.custom_dictionary.enabled", true);
        // 按语言选择自定义词汇列表：en 模式优先读取 words_en，为空则回退到 words
        if ("en".equals(this.language)) {
            List<String> enWords = config.getStringList("knowledge.custom_dictionary.words_en");
            this.customDictionaryWords = !enWords.isEmpty() ? enWords : config.getStringList("knowledge.custom_dictionary.words");
        } else {
            this.customDictionaryWords = config.getStringList("knowledge.custom_dictionary.words");
        }

        // 加载内置词汇表（语言变化时重新加载）
        if (vocabularyLoadedLanguage == null || !vocabularyLoadedLanguage.equals(this.language)) {
            this.internalDictionaryWords = loadInternalVocabulary();
            vocabularyLoadedLanguage = this.language;
        }

        if (this.internalDictionaryWords == null) {
            this.internalDictionaryWords = new ArrayList<>();
        }

        // 合并所有词汇（内置+自定义），自动去重
        Set<String> allWords = new LinkedHashSet<>(internalDictionaryWords);
        if (customDictionaryWords != null) {
            allWords.addAll(customDictionaryWords);
        }
        this.allDictionaryWords = new ArrayList<>(allWords);

        // Agent 能力配置
        this.agentEnabled = config.getBoolean("agent.enabled", true);
        this.agentEnableChatListener = config.getBoolean("agent.enable_chat_listener", true);
        this.agentEnableCommand = config.getBoolean("agent.enable_command", true);
        this.agentIntentHistoryCount = config.getInt("agent.intent_history_count", 5);
        this.agentAnalysisHistoryCount = config.getInt("agent.analysis_history_count", 2);
        this.agentSystemPrompt = getLocalizedString(config, "agent.prompts.system_prompt", "");

        // 分析提示词后缀（用于识别业务内容边界）
        this.agentAnalysisPromptSuffix = getLocalizedString(config, "agent.prompts.analysis_prompt_suffix", "");

        // LLM 提供商配置（通用）
        this.llmApiKey = config.getString("llm.api_key", "");
        this.llmApiUrl = config.getString("llm.api_url", "https://api.deepseek.com/v1/chat/completions");
        this.llmModel = config.getString("llm.model", "deepseek-chat");

        // 命令执行技能配置
        this.commandSkillEnabled = config.getBoolean("command_skill.enabled", false);

        // 安全配置
        this.securityPlayerIsolationEnabled = config.getBoolean("security.player_isolation.enabled", true);
        this.securityAllowedActions = config.getStringList("security.player_isolation.allowed_actions");

        // 挂机任务配置
        this.afkTaskEnabled = config.getBoolean("afk_task.enabled", true);
        this.afkTaskMaxTasks = config.getInt("afk_task.max_tasks", 10);
        this.afkTaskCheckIntervalTicks = config.getInt("afk_task.check_interval_ticks", 20);
        this.afkTaskMaxConsecutiveFailures = config.getInt("afk_task.max_consecutive_failures", 10);

        // AI 响应输出管线配置
        this.outputConfigManager.load(config);

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
            PluginLogger.warn("配置管理", I18nService.tr("刷新 LLM 配置缓存时发生异常: {}", e.getMessage()), e);
        }
    }

    /**
     * 将配置的轮数转换为实际的消息数量（1轮=2条消息）
     *
     * @param rounds 轮数
     * @return 实际消息数量
     */
    public int getHistoryMessageCount(int rounds) {
        return Math.max(0, rounds * 2);
    }

    /**
     * 加载内置词汇表
     * 从 internal/vocabulary/ 目录加载所有 .txt 文件
     *
     * @return 内置词汇列表
     */
    private List<String> loadInternalVocabulary() {
        List<String> words = new ArrayList<>();

        try {
            // 直接从 JAR 包中加载所有内置词汇文件
            loadVocabularyFromJar(words);

        } catch (Exception e) {
            PluginLogger.warn("配置管理", I18nService.tr("加载内置词汇表时发生异常: {}", e.getMessage()), e);
        }

        return words;
    }

    /**
     * 从 JAR 包中加载内置词汇表
     * 按语言子目录加载：中文加载 internal/vocabulary/ 下的 .txt，英文加载 internal/vocabulary/en/ 下的 .txt
     *
     * @param words 词汇列表（会被填充）
     */
    private void loadVocabularyFromJar(List<String> words) {
        try {
            // 获取插件的 JAR 文件路径
            java.io.File jarFile = new java.io.File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());

            if (!jarFile.exists()) {
                PluginLogger.warn("配置管理", "无法找到插件 JAR 文件，跳过加载词汇表");
                return;
            }

            // 按语言确定扫描前缀：中文默认目录，英文进 en/ 子目录
            String prefix = "en".equals(this.language) ? "internal/vocabulary/en/" : "internal/vocabulary/";

            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
                java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String name = entry.getName();

                    // 只处理对应语言目录下的 .txt 文件（排除子目录）
                    if (name.startsWith(prefix) && name.endsWith(".txt") && !entry.isDirectory()) {
                        // 确保文件在目标目录层级（排除更深层级的文件）
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
            PluginLogger.warn("配置管理", I18nService.tr("无法解析 JAR 文件路径: {}，跳过加载", e.getMessage()), e);
        } catch (Exception e) {
            PluginLogger.warn("配置管理", I18nService.tr("加载内置词汇表失败: {}", e.getMessage()), e);
        }
    }

    /**
     * 按语言读取配置中的本地化字符串
     * <p>当 language=en 时，优先读取 {key}_en，如果为空则回退到 {key}；
     * 当 language=zh 时，直接读取 {key}。</p>
     *
     * @param config          配置对象
     * @param key             配置键名（如 "settings.system_prompt"）
     * @param fallbackDefault 回退默认值（key 不存在时使用）
     * @return 对应语言的配置值
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