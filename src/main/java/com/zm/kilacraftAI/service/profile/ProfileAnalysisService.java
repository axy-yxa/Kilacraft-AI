package com.zm.kilacraftAI.service.profile;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.util.JsonSafeGetUtil;
import com.zm.kilacraftAI.common.util.LLMResponseUtil;
import com.zm.kilacraftAI.common.util.LogSnippetUtil;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import com.zm.kilacraftAI.db.DatabaseManager;
import com.zm.kilacraftAI.db.dao.ConversationDao;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.LLMProvider;
import com.zm.kilacraftAI.model.profile.PlayerProfile;
import com.zm.kilacraftAI.service.conversation.ConversationManager;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
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
 * @since 2026-05-07
 */
public class ProfileAnalysisService {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    /**
     * 触发分析的消息来源（计数门控用）：仅玩家主动发起的对话。
     *
     * <p>玩家对守护的回复经 ChatListener 记成 source=chat，已计入这里；
     * 守护 AI 主动消息（source=guardian）是 AI 发起、非玩家主动，不计入触发阈值，
     * 否则会因守护频繁主动而虚抬画像分析频率。</p>
     */
    private static final String TRIGGER_SOURCE_FILTER = "'chat','command'";

    /**
     * 加载给 LLM 分析的消息来源。
     *
     * <p>守护是交互式主动层：AI 提问、玩家回应。加载时须把 guardian 的 AI 消息一并纳入，
     * 否则只看到玩家单方面的回应而看不到 AI 提了什么，上下文断裂反而是噪音。
     * 仍排除 greeting（单向模板广播，无玩家交互信号）与 plugin（人格隔离场景）。</p>
     */
    private static final String ANALYSIS_SOURCE_FILTER = "'chat','command','guardian'";

    /**
     * LLM system prompt：要求输出固定 JSON 结构（8 维度扁平结构）
     */
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个玩家行为分析助手。根据玩家的对话历史，分析该玩家的游戏风格、偏好和行为特征。
            
            【画像用途】画像会作为长期记忆注入未来的对话，但刷新间隔较长（按天计）。一旦写入易过期的内容，会长期误导后续回复。务必只提炼长期稳定、不会随单次操作而改变的特征。
            
            请输出一个 JSON 对象，包含以下字段（信息不足则留空字符串）：
            {
              "playstyle": "长期游戏倾向（如：偏探索、偏建造、偏战斗、偏社交等）",
              "personality": "观察到的性格特征（如：友好、幽默、直接、内敛等）",
              "interests": "长期稳定的兴趣领域（如：建筑、经济、自动化、探险等）",
              "boundaries": "交互禁忌或反感（如：不愿被催促、介意特定称呼等，无则留空）",
              "communication": "期望的 AI 回复方式（如：简短直接、不用表情、语气克制等）",
              "spatial": "长期归属地的定性描述（如：在某区域有长期基地），严禁写坐标和数量",
              "facts": "玩家声明的稳定关系或身份（如：某玩家是好友或对手、自称某身份），严禁写数值",
              "notes": "其他值得长期注意的观察"
            }
            
            【数据性质红线 - 最重要】
            1. 严禁记录任何会随时间或他人行为变化的具体数值快照：余额/金币、库存数量与物品清单、当前坐标、血量/饱食、在线状态、在售商品与价格、附近实体数量等。这类数据随时变化，固化进画像只会留下过期错误答案——即使玩家本人在对话里明确说出具体数字，也不要记录。
            2. 只记"定性关系与稳定倾向"，不记"瞬时数值"。判断标准：某信息若是具体数字、或会随单次操作而改变 → 不记；若是关系、身份、偏好、风格等长期属性 → 记。
            3. 只分析对话中明确体现的信息，不要推测。
            
            【输出规范】
            1. 每个字段用简短的短语或句子，整个 JSON 总字符数控制在 500 以内。
            2. 如果某个维度信息不足，对应字段填空字符串。
            3. 只输出 JSON，不要包含其他内容。
            """;

    private static final String DEFAULT_SYSTEM_PROMPT_EN = """
            You are a player behavior analysis assistant. Analyze the player's game style, preferences, and behavioral traits based on their conversation history.
            
            [Profile purpose] The profile is injected into future conversations as long-term memory, but refreshes infrequently (on the order of days). Anything volatile that gets written in will mislead subsequent replies for a long time. Extract only traits that are long-term stable and do not change with a single action.
            
            Output a JSON object with the following fields (use empty string if insufficient information):
            {
              "playstyle": "Long-term game tendency (e.g., explorer-leaning, builder-leaning, combat-leaning, socializer-leaning)",
              "personality": "Observed personality traits (e.g., friendly, humorous, direct, reserved)",
              "interests": "Long-term stable interests (e.g., building, economy, automation, exploration)",
              "boundaries": "Interaction taboos or dislikes (e.g., dislikes being rushed, sensitive to certain forms of address; leave empty if none)",
              "communication": "Preferred AI response style (e.g., brief and direct, no emojis, restrained tone)",
              "spatial": "Qualitative description of long-term turf (e.g., has a long-term base in some region); NEVER write coordinates or counts",
              "facts": "Stated stable relations or identity (e.g., a player is a friend or rival, self-claimed role); NEVER write numeric values",
              "notes": "Other observations worth remembering long-term"
            }
            
            [Data-nature red lines - most important]
            1. NEVER record numeric snapshots of anything that changes over time or with others' actions: balance/coins, inventory counts and item lists, current coordinates, health/hunger, online status, shop listings and prices, nearby entity counts, etc. Such data is volatile at all times; freezing it into the profile only leaves stale wrong answers — even if the player explicitly states a specific number in the conversation, do not record it.
            2. Record only "qualitative relations and stable tendencies", not "instantaneous values". Test: if a piece of information is a specific number or changes with a single action → do not record; if it is a relation, identity, preference, or style (a long-term attribute) → record it.
            3. Analyze only information explicitly shown in conversations; do not speculate.
            
            [Output rules]
            1. Keep each field as a brief phrase or sentence; keep total JSON within 500 characters.
            2. If a dimension has insufficient information, use empty string.
            3. Output only JSON, no other content.
            """;

    /**
     * 增量分析 system prompt（无配置时使用）：注入旧画像，融合新对话
     */
    private static final String DEFAULT_INCREMENTAL_SYSTEM_PROMPT = """
            你是一个玩家行为分析助手。以下是玩家的历史画像数据（JSON）和新的对话记录。
            请对比历史画像和新对话，**保留仍然准确的内容，修正已经变化的内容**，融合输出更新后的完整画像 JSON。
            不要被最近几句话过度左右 —— 关注长期稳定的特征。
            
            【数据性质红线 - 必须执行】
            1. 融合时一律移除旧画像和新对话中的任何具体数值快照（余额/金币、库存数量与物品清单、当前坐标、血量/饱食、在线状态、在售商品与价格、附近实体数量等）。这类数据随时变化、默认过期——即使旧画像里已存在，或新对话里玩家再次陈述，也不要写入新画像。
            2. 只保留"定性关系与稳定倾向"，丢弃"瞬时数值"。
            
            【输出要求】
            1. 只输出 JSON，不要包含其他内容
            2. 使用以下固定字段输出（旧画像中的 preferences/communication_style 等旧字段请迁移到新结构）：
               playstyle、personality、interests、boundaries、communication、spatial、facts、notes
            3. 整个JSON总字符数控制在500以内
            4. 如果某个维度信息不足，对应字段填空字符串
            5. 当字符数受限需要取舍时，优先保留新对话中印证或提及的信息，精简或移除长期未被提及的低优先级信息
            """;

    private static final String DEFAULT_INCREMENTAL_SYSTEM_PROMPT_EN = """
            You are a player behavior analysis assistant. Below is the player's existing profile data (JSON) and new conversation records.
            Compare the existing profile with new conversations, **retain what still holds true, revise what has changed**, and produce a complete updated profile JSON.
            Do not over-weight the most recent few messages — focus on consistent long-term traits.
            
            [Data-nature red lines - must execute]
            1. On merge, ALWAYS remove any numeric snapshots from both the old profile and new conversations (balance/coins, inventory counts and item lists, current coordinates, health/hunger, online status, shop listings and prices, nearby entity counts, etc.). Such data is volatile and stale by default — even if it already exists in the old profile, or the player restates it in new conversations, do not write it into the new profile.
            2. Keep only "qualitative relations and stable tendencies"; drop "instantaneous values".
            
            [Output requirements]
            1. Output only JSON, no other content
            2. Use the following fixed fields (migrate old fields like preferences/communication_style to the new structure):
               playstyle, personality, interests, boundaries, communication, spatial, facts, notes
            3. Keep total JSON within 500 characters
            4. If insufficient information for a dimension, use empty string
            5. When character limit requires trade-offs, prioritize information corroborated or mentioned in new conversations, and trim or remove long-unmentioned low-priority information
            """;

    /**
     * 注入旧画像时需要过滤掉的元数据字段（非画像内容，由 handleAnalysisResult 自动管理）
     */
    private static final Set<String> METADATA_KEYS = Set.of("version", "analyzed_at");

    /**
     * 画像有效字段白名单（与 system prompt 约定的 8 维度结构一致）。非本次输出的旧字段（preferences/communication_style 等）或未知键一律过滤，防止脏写。
     */
    private static final Set<String> PROFILE_FIELDS = Set.of("playstyle", "personality", "interests", "boundaries", "communication", "spatial", "facts", "notes");

    /**
     * 单字段最大字符数。提示词约定整个 JSON ≤500 字符 / 8 字段（均值 ~62），上限取 ~3 倍容下较长定性描述，同时截断 LLM 失控输出。
     */
    private static final int MAX_PROFILE_FIELD_LENGTH = 200;

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
     * 热重载配置（由 /kila reload 触发）
     *
     * <p>重建 ConversationDao 以反映最新的表前缀。</p>
     * <p>其他 profile.* 配置（间隔天数、触发阈值、超时、提示词）均通过
     * {@code databaseManager.getConfig()} 动态读取，无需额外刷新。</p>
     */
    public void refreshConfig() {
        this.conversationDao = new ConversationDao(databaseManager.getTablePrefix());
        PluginLoggerUtil.info("数据库", "画像分析服务配置已刷新");
    }

    /**
     * 分析入口（供事件处理器调用）
     *
     * <p>所有异常兜底处理，不会向上抛出。</p>
     */
    public void tryAnalyze(UUID playerUuid) {
        tryAnalyzeAsync(playerUuid).exceptionally(ex -> {
            PluginLoggerUtil.warn("画像分析", I18nService.tr("玩家画像分析异常: {} - {}", playerUuid, ex.getMessage()));
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
                // 门控2：新消息数不足（只数玩家主动发起的 chat/command，守护 AI 消息不虚抬阈值）
                int newMessageCount = conversationDao.countMessagesSince(conn, playerUuid.toString(), TRIGGER_SOURCE_FILTER, lastAnalyzed);

                if (newMessageCount < minMessages) {
                    PluginLoggerUtil.debug("画像分析", I18nService.tr("玩家 {} 新消息数 {} < 最低阈值 {}，跳过分析", playerUuid, newMessageCount, minMessages));
                    return null;
                }

                // 门控通过：加载滑动窗口内全部消息（无数量上限）
                List<ConversationManager.Message> messages = conversationDao.loadMessagesForAnalysis(conn, playerUuid.toString(), ANALYSIS_SOURCE_FILTER, lastAnalyzed);

                if (messages.isEmpty()) {
                    return null;
                }

                return callLLMForAnalysis(playerUuid, profile, messages, messages.size(), lastAnalyzed);
            } catch (SQLException e) {
                PluginLoggerUtil.warn("画像分析", I18nService.tr("加载对话历史失败: {} - {}", playerUuid, e.getMessage()));
                return null;
            }
        }, FoliaCompat.getIOPool()).thenCompose(result -> result != null ? result : CompletableFuture.completedFuture(null));
    }

    /**
     * 构造静默 AIResponseHandler 并调用 LLM，返回异步结果链
     */
    private CompletableFuture<Void> callLLMForAnalysis(UUID playerUuid, PlayerProfile profile, List<ConversationManager.Message> messages, int messageCount, long windowStart) {
        LLMProvider provider = plugin.getLlmManager().getCurrentProvider();
        if (provider == null) {
            PluginLoggerUtil.warn("画像分析", I18nService.tr("LLM Provider 未初始化，跳过分析"));
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
                // errorMessage 是面向玩家的 §c 串；后台日志不重复详情（provider WARN 已含分类+原始错误），用纯文本标记完成异常
                responseFuture.completeExceptionally(new RuntimeException(I18nService.tr("LLM 画像分析请求失败（详见控制台 WARN）")));
            }

            @Override
            public boolean isStreamOutputEnabled() {
                return false;
            }
        };

        int timeoutSeconds = databaseManager.getConfig().getProfileAnalysisTimeoutSeconds();

        provider.processRequestWithCustomSystemPrompt(userMessage, playerName, null, silentHandler, getAnalysisSystemPrompt(profile), false, false, true);

        return responseFuture.orTimeout(timeoutSeconds, TimeUnit.SECONDS).thenAccept(response -> handleAnalysisResult(playerUuid, profile, response, messageCount, windowStart)).exceptionally(ex -> {
            PluginLoggerUtil.warn("画像分析", I18nService.tr("LLM 分析失败: {} - {}", playerUuid, ex.getMessage()));
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
            // 过滤元数据字段，仅注入画像内容（version/analyzed_at 由 handleAnalysisResult 管理）
            Map<String, Object> cleanData = new LinkedHashMap<>(existingData);
            METADATA_KEYS.forEach(cleanData::remove);
            sb.append(I18nService.tr("以下是该玩家的历史画像数据（JSON）：\n\n"));
            sb.append(GSON.toJson(cleanData)).append("\n\n");
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
    private void handleAnalysisResult(UUID playerUuid, PlayerProfile profile, String response, int messageCount, long windowStart) {
        if (response == null || response.isEmpty()) {
            PluginLoggerUtil.warn("画像分析", I18nService.tr("LLM 返回空响应，跳过更新"));
            return;
        }
        // 防御性兜底：正常错误流走 handleError → 异常完成，不会到这里；若 future 异常地以 §c 串正常完成，也跳过不写画像
        if (LLMResponseUtil.isErrorResponse(response)) {
            PluginLoggerUtil.warn("画像分析", I18nService.tr("LLM 返回错误响应，跳过画像更新: {}", LogSnippetUtil.truncateForLog(response, 200)));
            return;
        }

        String jsonStr = extractJson(response);

        Map<String, Object> profileData;
        try {
            profileData = GSON.fromJson(jsonStr, MAP_TYPE);
        } catch (JsonSyntaxException e) {
            // 尝试自动修复不完整的 JSON
            String repaired = JsonSafeGetUtil.repairJsonBraces(jsonStr);
            if (!repaired.equals(jsonStr)) {
                try {
                    profileData = GSON.fromJson(repaired, MAP_TYPE);
                    PluginLoggerUtil.debug("画像分析", I18nService.tr("JSON 自动修复成功"));
                } catch (Exception ignored) {
                    // 修复后仍然失败
                    PluginLoggerUtil.warn("画像分析", I18nService.tr("解析 LLM 响应 JSON 失败: {}。原始响应: {}", e.getMessage(), LogSnippetUtil.truncateForLog(response, 300)));
                    return;
                }
            } else {
                PluginLoggerUtil.warn("画像分析", I18nService.tr("解析 LLM 响应 JSON 失败: {}。原始响应: {}", e.getMessage(), LogSnippetUtil.truncateForLog(response, 300)));
                return;
            }
        }

        if (profileData == null || profileData.isEmpty()) {
            PluginLoggerUtil.warn("画像分析", I18nService.tr("解析结果为空，跳过更新"));
            return;
        }

        // 白名单过滤：只留 8 个目标字段，旧字段/未知键/嵌套结构丢弃，标量化 + 长度截断防脏写
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String key : PROFILE_FIELDS) {
            Object v = profileData.get(key);
            if (v == null) continue;
            String sv;
            if (v instanceof String s) {
                sv = s;
            } else if (v instanceof Number || v instanceof Boolean) {
                // 标量强制转字符串（画像字段语义为短语）
                sv = String.valueOf(v);
            } else {
                // 嵌套对象/数组不符合画像字段语义，跳过
                continue;
            }
            if (sv.length() > MAX_PROFILE_FIELD_LENGTH) {
                sv = sv.substring(0, MAX_PROFILE_FIELD_LENGTH);
            }
            if (!sv.isEmpty()) {
                filtered.put(key, sv);
            }
        }
        if (filtered.isEmpty()) {
            PluginLoggerUtil.warn("画像分析", I18nService.tr("LLM 未输出任何有效画像字段（已过滤未知/旧结构字段），跳过更新: {}", LogSnippetUtil.truncateForLog(response, 200)));
            return;
        }
        profileData = filtered;

        // 防御性长度检查：画像 JSON 应控制在 500 字符内，超过 2000 字符输出警告
        if (jsonStr.length() > 2000) {
            PluginLoggerUtil.warn("画像分析", I18nService.tr("画像JSON长度异常: {} 字符（建议≤500）", jsonStr.length()));
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

        profileManager.putExtendedData(playerUuid, profileData, analyzedAt, messageCount, windowStart, analyzedAt, newVersion);

        PluginLoggerUtil.info("画像分析", I18nService.tr("玩家 {} 画像分析完成，版本: {}，字段: {}", playerUuid, newVersion, String.join(", ", profileData.keySet())));
    }

    /**
     * 获取画像分析系统提示词（按当前语言和是否有旧画像选择首次/增量提示词）
     *
     * <p>有旧画像时使用增量提示词（融合更新），无旧画像时使用首次提示词（纯分析）。
     * 用户可在 database.yml 中自定义提示词，留空则使用内置默认值。</p>
     */
    private String getAnalysisSystemPrompt(PlayerProfile profile) {
        boolean isChinese = I18nService.isZh();
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
