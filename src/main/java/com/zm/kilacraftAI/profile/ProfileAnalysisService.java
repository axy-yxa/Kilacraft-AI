package com.zm.kilacraftAI.profile;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.api.LLMProvider;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.db.DatabaseConfig;
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
    private static final String SYSTEM_PROMPT = """
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

    private final KilacraftAI plugin;
    private final DatabaseManager databaseManager;
    private final ConversationDao conversationDao;
    private final ProfileManager profileManager;
    private final DatabaseConfig dbConfig;

    public ProfileAnalysisService(KilacraftAI plugin, DatabaseManager databaseManager, ProfileManager profileManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.conversationDao = new ConversationDao(databaseManager.getTablePrefix());
        this.profileManager = profileManager;
        this.dbConfig = databaseManager.getConfig();
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
        long intervalMs = (long) dbConfig.getProfileAnalysisIntervalDays() * 24L * 60 * 60 * 1000;

        // 门控1：时间间隔未到
        if (intervalMs > 0 && (now - lastAnalyzed) < intervalMs) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            int minMessages = dbConfig.getProfileMinMessagesToTrigger();

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

                return callLLMForAnalysis(playerUuid, profile, messages);
            } catch (SQLException e) {
                PluginLogger.warn("画像分析", I18nService.tr("加载对话历史失败: {} - {}", playerUuid, e.getMessage()));
                return null;
            }
        }, FoliaCompat.getIOPool()).thenCompose(result -> result != null ? result : CompletableFuture.completedFuture(null));
    }

    /**
     * 构造静默 AIResponseHandler 并调用 LLM，返回异步结果链
     */
    private CompletableFuture<Void> callLLMForAnalysis(UUID playerUuid, PlayerProfile profile, List<ConversationManager.Message> messages) {
        LLMProvider provider = plugin.getLlmManager().getCurrentProvider();
        if (provider == null) {
            PluginLogger.warn("画像分析", I18nService.tr("LLM Provider 未初始化，跳过分析"));
            return CompletableFuture.completedFuture(null);
        }

        String userMessage = buildAnalysisMessage(messages);
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

        int timeoutSeconds = dbConfig.getProfileAnalysisTimeoutSeconds();

        provider.processRequestWithCustomSystemPrompt(userMessage, playerName, null, silentHandler, SYSTEM_PROMPT, false, false, true);

        return responseFuture.orTimeout(timeoutSeconds, TimeUnit.SECONDS).thenAccept(response -> handleAnalysisResult(playerUuid, response)).exceptionally(ex -> {
            PluginLogger.warn("画像分析", I18nService.tr("LLM 分析失败: {} - {}", playerUuid, ex.getMessage()));
            return null;
        });
    }

    /**
     * 组装 LLM 用户侧消息（对话历史拼接）
     */
    private String buildAnalysisMessage(List<ConversationManager.Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append(I18nService.tr("以下是玩家的对话历史，请分析其行为特征：\n\n"));

        for (ConversationManager.Message msg : messages) {
            String role = "user".equals(msg.getRole()) ? I18nService.tr("玩家") : "AI";
            sb.append(role).append(": ").append(msg.getContent()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 解析 LLM 响应 JSON 并写入画像
     */
    private void handleAnalysisResult(UUID playerUuid, String response) {
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

        profileData.put("version", 1);
        profileData.put("analyzed_at", System.currentTimeMillis());

        long analyzedAt = System.currentTimeMillis();
        profileManager.putExtendedData(playerUuid, profileData, analyzedAt);

        PluginLogger.info("画像分析", I18nService.tr("玩家 {} 画像分析完成，字段: {}", playerUuid, String.join(", ", profileData.keySet())));
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
