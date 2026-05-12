package com.zm.kilacraftAI.profile;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.api.LLMProvider;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.ConversationDao;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.util.PluginLogger;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 玩家画像 LLM 分析服务
 *
 * <p>在玩家登录/退出时触发，异步读取上次分析以来的对话历史，调用 LLM 提取行为特征，
 * 结果写入 {@link ProfileManager#putExtendedData}。</p>
 *
 * <h3>触发条件（三重门控）</h3>
 * <ol>
 *   <li>时间间隔：距上次分析 &ge; {@code analysis_interval_days}</li>
 *   <li>消息数量：新消息 &ge; {@code min_messages_to_trigger}</li>
 *   <li>消息滑动窗口：仅读取 {@code profile_analyzed_at} 之后的消息，无数量上限</li>
 * </ol>
 *
 * <h3>调用链</h3>
 * {@code tryAnalyze()} → 异步查 DB → 静默 LLM 调用 → JSON 解析 → {@code putExtendedData()} 写回
 *
 * @author Zm_Mmm
 */
public class ProfileAnalysisService {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    /**
     * 参与画像分析的消息来源（排除 afk_callback、console、greeting、plugin）
     *
     * <p>greeting 是 AI 模板问候语，不反映玩家真实表达；
     * plugin 是 NPC 人格对话，场景天然隔离，不消费画像数据，也不应污染画像。</p>
     */
    private static final String SOURCE_FILTER = "'chat','command'";

    /**
     * LLM system prompt：要求输出固定 JSON 结构
     */
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个玩家行为分析助手。根据玩家的对话历史，分析该玩家的游戏风格、偏好和行为特征。
            
            请输出一个 JSON 对象，包含以下字段（如果没有足够信息则留空字符串）：
            {
              "playstyle": "玩家的游戏风格描述（如：探索型、建造型、战斗型、社交型等）",
              "personality": "从对话中观察到的性格特征（如：友好、幽默、直接、内向等）",
              "preferences": "玩家的偏好（如：喜欢的活动、感兴趣的话题等）",
              "communication_style": "沟通风格（如：简短直接、详细描述、使用表情符号等）",
              "notes": "其他值得注意的观察"
            }
            
            注意：
            1. 只分析对话中明确体现的信息，不要推测
            2. 每个字段尽量简洁，控制在50字以内
            3. 如果某个维度信息不足，对应字段填空字符串
            4. 只输出 JSON，不要包含其他内容
            """;

    private static final String DEFAULT_SYSTEM_PROMPT_EN = """
            You are a player behavior analysis assistant. Analyze the player's game style, preferences, and behavioral traits based on their conversation history.
            
            Output a JSON object with the following fields (use empty string if insufficient information):
            {
              "playstyle": "Player's game style description (e.g., explorer, builder, fighter, socializer)",
              "personality": "Personality traits observed from conversations (e.g., friendly, humorous, direct, introverted)",
              "preferences": "Player preferences (e.g., favorite activities, topics of interest)",
              "communication_style": "Communication style (e.g., brief and direct, detailed, uses emojis)",
              "notes": "Other notable observations"
            }
            
            Notes:
            1. Only analyze information explicitly shown in conversations, do not speculate
            2. Keep each field concise, within 50 characters
            3. If insufficient information for a dimension, use empty string
            4. Output only JSON, no other content
            """;

    /**
     * 增量分析 system prompt（无配置时使用）：注入旧画像，融合新对话
     */
    private static final String DEFAULT_INCREMENTAL_SYSTEM_PROMPT = """
            你是一个玩家行为分析助手。以下是玩家的历史画像数据（JSON）和新的对话记录。
            请对比历史画像和新对话，**保留仍然准确的内容，修正已经变化的内容**，融合输出更新后的完整画像 JSON。
            不要被最近几句话过度左右 —— 关注长期稳定的特征。

            【输出要求】
            1. 只输出 JSON，不要包含其他内容
            2. 字段与历史画像保持一致（playstyle、personality、preferences、communication_style、notes）
            3. 如果某个维度信息不足，对应字段填空字符串
            """;

    private static final String DEFAULT_INCREMENTAL_SYSTEM_PROMPT_EN = """
            You are a player behavior analysis assistant. Below is the player's existing profile data (JSON) and new conversation records.
            Compare the existing profile with new conversations, **retain what still holds true, revise what has changed**, and produce a complete updated profile JSON.
            Do not over-weight the most recent few messages — focus on consistent long-term traits.

            Requirements:
            1. Output only JSON, no other content
            2. Keep the same fields as the existing profile (playstyle, personality, preferences, communication_style, notes)
            3. If insufficient information for a dimension, use empty string
            """;

    private final KilacraftAI plugin;
    private final DatabaseManager databaseManager;
    private volatile ConversationDao conversationDao;
    private final ProfileManager profileManager;

    public ProfileAnalysisService(KilacraftAI plugin, DatabaseManager databaseManager, ProfileManager profileManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.conversationDao = new ConversationDao(databaseManager.getTablePrefix());
        this.profileManager = profileManager;
    }

    /**
     * 热重载配置（由 /kilacraft reload 触发）
     *
     * <p>重建 ConversationDao 以反映最新的表前缀。</p>
     * <p>其他 profile.* 配置（间隔天数、触发阈值、超时、提示词）均通过
     * {@code databaseManager.getConfig()} 动态读取，无需额外刷新。</p>
     */
    public void refreshConfig() {
        this.conversationDao = new ConversationDao(databaseManager.getTablePrefix());
        PluginLogger.info("数据库", "画像分析服务配置已刷新");
    }

    /**
     * 分析入口（供事件处理器调用）
     *
     * <p>所有异常兜底处理，不会向上抛出。</p>
     */
    public void tryAnalyze(UUID playerUuid) {
        tryAnalyzeAsync(playerUuid).exceptionally(ex -> {
            PluginLogger.warn("画像分析", I18nService.tr("玩家画像分析异常: {} - {}", playerUuid, ex.getMessage()));
            return null;
        });
    }

    /**
     * 核心异步流程：三重门控 → 查 DB → 调 LLM
     */
    private CompletableFuture<Void> tryAnalyzeAsync(UUID playerUuid) {
        PlayerProfile profile = profileManager.getCachedProfile(playerUuid);
        if (profile == null) {
            return CompletableFuture.completedFuture(null);
        }

        long now = System.currentTimeMillis();
        long lastAnalyzed = profile.getProfileAnalyzedAt();
        long intervalMs = (long) databaseManager.getConfig().getProfileAnalysisIntervalDays() * 24L * 60 * 60 * 1000;

        // 门控1：时间间隔未到
        if (intervalMs > 0 && (now - lastAnalyzed) < intervalMs) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            int minMessages = databaseManager.getConfig().getProfileMinMessagesToTrigger();

            try (Connection conn = databaseManager.getConnection()) {
                // 门控2：新消息数不足
                int newMessageCount = conversationDao.countMessagesSince(conn, playerUuid.toString(), SOURCE_FILTER, lastAnalyzed);

                if (newMessageCount < minMessages) {
                    PluginLogger.debug("画像分析", I18nService.tr("玩家 {} 新消息数 {} < 最低阈值 {}，跳过分析", playerUuid, newMessageCount, minMessages));
                    return null;
                }

                // 门控通过：加载滑动窗口内全部消息（无数量上限）
                List<ConversationManager.Message> messages = conversationDao.loadMessagesForAnalysis(conn, playerUuid.toString(), SOURCE_FILTER, lastAnalyzed);

                if (messages.isEmpty()) {
                    return null;
                }

                return callLLMForAnalysis(playerUuid, profile, messages, messages.size(), lastAnalyzed);
            } catch (SQLException e) {
                PluginLogger.warn("画像分析", I18nService.tr("加载对话历史失败: {} - {}", playerUuid, e.getMessage()));
                return null;
            }
        }, FoliaCompat.getIOPool()).thenCompose(result -> result != null ? result : CompletableFuture.completedFuture(null));
    }

    /**
     * 构造静默 AIResponseHandler 并调用 LLM，返回异步结果链
     */
    private CompletableFuture<Void> callLLMForAnalysis(UUID playerUuid, PlayerProfile profile,
                                                       List<ConversationManager.Message> messages,
                                                       int messageCount, long windowStart) {
        LLMProvider provider = plugin.getLlmManager().getCurrentProvider();
        if (provider == null) {
            PluginLogger.warn("画像分析", I18nService.tr("LLM Provider 未初始化，跳过分析"));
            return CompletableFuture.completedFuture(null);
        }

        String userMessage = buildAnalysisMessage(profile, messages);
        String playerName = profile.getName() != null ? profile.getName() : playerUuid.toString();

        CompletableFuture<String> responseFuture = new CompletableFuture<>();

        AIResponseHandler silentHandler = new AIResponseHandler() {
            @Override
            public UUID getPlayerId() {
                return playerUuid;
            }

            @Override
            public String getPlayerName() {
                return playerName;
            }

            @Override
            public void showResponse(String response) {
                responseFuture.complete(response);
            }

            @Override
            public void showStreamChunk(String chunk, String currentMessage) {
            }

            @Override
            public void handleError(String errorMessage) {
                responseFuture.completeExceptionally(new RuntimeException(errorMessage));
            }

            @Override
            public boolean isStreamOutputEnabled() {
                return false;
            }
        };

        int timeoutSeconds = databaseManager.getConfig().getProfileAnalysisTimeoutSeconds();

        provider.processRequestWithCustomSystemPrompt(userMessage, playerName, null, silentHandler, getAnalysisSystemPrompt(profile), false, false, true);

        long finalWindowStart = windowStart;
        return responseFuture.orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .thenAccept(response -> handleAnalysisResult(playerUuid, profile, response, messageCount, finalWindowStart))
                .exceptionally(ex -> {
                    PluginLogger.warn("画像分析", I18nService.tr("LLM 分析失败: {} - {}", playerUuid, ex.getMessage()));
                    return null;
                });
    }

    /**
     * 组装 LLM 用户侧消息（历史画像注入 + 对话历史拼接）
     *
     * <p>如果存在历史画像数据，在对话记录前注入旧画像 JSON，使 LLM 在增量模式下能对比融合。
     * 首次分析（无旧画像）时行为与改造前一致。</p>
     */
    private String buildAnalysisMessage(PlayerProfile profile, List<ConversationManager.Message> messages) {
        StringBuilder sb = new StringBuilder();

        Map<String, Object> existingData = profile.getExtendedData();
        if (existingData != null && !existingData.isEmpty()) {
            sb.append(I18nService.tr("以下是该玩家的历史画像数据（JSON）：\n\n"));
            sb.append(GSON.toJson(existingData)).append("\n\n");
            sb.append(I18nService.tr("以及自上次分析以来的新对话记录：\n\n"));
        } else {
            sb.append(I18nService.tr("以下是玩家的对话历史，请分析其行为特征：\n\n"));
        }

        for (ConversationManager.Message msg : messages) {
            String role = "user".equals(msg.getRole()) ? I18nService.tr("玩家") : "AI";
            sb.append(role).append(": ").append(msg.getContent()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 解析 LLM 响应 JSON 并写入画像
     */
    private void handleAnalysisResult(UUID playerUuid, PlayerProfile profile, String response,
                                      int messageCount, long windowStart) {
        if (response == null || response.isEmpty()) {
            PluginLogger.warn("画像分析", I18nService.tr("LLM 返回空响应，跳过更新"));
            return;
        }

        String jsonStr = extractJson(response);

        Map<String, Object> profileData;
        try {
            profileData = GSON.fromJson(jsonStr, MAP_TYPE);
        } catch (JsonSyntaxException e) {
            PluginLogger.warn("画像分析", I18nService.tr("解析 LLM 响应 JSON 失败: {}", e.getMessage()));
            return;
        }

        if (profileData == null || profileData.isEmpty()) {
            PluginLogger.warn("画像分析", I18nService.tr("解析结果为空，跳过更新"));
            return;
        }

        Map<String, Object> existingData = profile.getExtendedData();
        int oldVersion = 0;
        if (existingData != null && existingData.get("version") instanceof Number n) {
            oldVersion = n.intValue();
        }
        int newVersion = oldVersion + 1;

        long analyzedAt = System.currentTimeMillis();
        profileData.put("version", newVersion);
        profileData.put("analyzed_at", analyzedAt);

        long windowEnd = analyzedAt;
        profileManager.putExtendedData(playerUuid, profileData, analyzedAt, messageCount, windowStart, windowEnd, newVersion);

        PluginLogger.info("画像分析", I18nService.tr("玩家 {} 画像分析完成，版本: {}，字段: {}", playerUuid, newVersion, String.join(", ", profileData.keySet())));
    }

    /**
     * 获取画像分析系统提示词（按当前语言和是否有旧画像选择首次/增量提示词）
     *
     * <p>有旧画像时使用增量提示词（融合更新），无旧画像时使用首次提示词（纯分析）。
     * 用户可在 database.yml 中自定义提示词，留空则使用内置默认值。</p>
     */
    private String getAnalysisSystemPrompt(PlayerProfile profile) {
        boolean isChinese = plugin.getConfigManager().isChinese();
        boolean hasExistingProfile = profile.getExtendedData() != null && !profile.getExtendedData().isEmpty();

        if (hasExistingProfile) {
            if (isChinese) {
                String custom = databaseManager.getConfig().getProfileIncrementalSystemPrompt();
                return (custom != null && !custom.isEmpty()) ? custom : DEFAULT_INCREMENTAL_SYSTEM_PROMPT;
            } else {
                String customEn = databaseManager.getConfig().getProfileIncrementalSystemPromptEn();
                return (customEn != null && !customEn.isEmpty()) ? customEn : DEFAULT_INCREMENTAL_SYSTEM_PROMPT_EN;
            }
        } else {
            if (isChinese) {
                String custom = databaseManager.getConfig().getProfileAnalysisSystemPrompt();
                return (custom != null && !custom.isEmpty()) ? custom : DEFAULT_SYSTEM_PROMPT;
            } else {
                String customEn = databaseManager.getConfig().getProfileAnalysisSystemPromptEn();
                return (customEn != null && !customEn.isEmpty()) ? customEn : DEFAULT_SYSTEM_PROMPT_EN;
            }
        }
    }

    /**
     * 从 LLM 响应中提取 JSON（兼容 markdown 代码块包裹和裸 JSON）
     */
    private String extractJson(String response) {
        String trimmed = response.trim();

        // 去掉 ```json ... ``` 围栏
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }

        // 无围栏：取最外层 { ... }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }
}
