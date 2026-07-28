package com.zm.kilacraftAI.skills.utility;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.common.enums.OutputScenarioEnum;
import com.zm.kilacraftAI.common.enums.PluginPermissionEnum;
import com.zm.kilacraftAI.common.util.PluginLoggerUtil;
import com.zm.kilacraftAI.config.SkillConfigManager;
import com.zm.kilacraftAI.handler.AIResponseHandler;
import com.zm.kilacraftAI.i18n.I18nService;
import com.zm.kilacraftAI.llm.LLMProvider;
import com.zm.kilacraftAI.service.conversation.ConversationManager;
import com.zm.kilacraftAI.service.output.AIResponsePipeline;
import com.zm.kilacraftAI.skills.framework.Skill;
import com.zm.kilacraftAI.skills.framework.SkillConfig;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillResult;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 通用工具技能
 *
 * <p>提供延迟等待、主动通知和全服广播三个基础能力，可在多步骤任务（TaskPlan）中灵活编排。</p>
 *
 * <h3>动作列表：</h3>
 * <ul>
 *   <li>{@code delay_wait}：延迟等待 N 秒（1-60秒），用于步骤间的时序间隔</li>
 *   <li>{@code notify_player}：将阶段性结果通过 AI 总结后主动通知玩家</li>
 *   <li>{@code broadcast_message}：通过 AI 美化消息后全服广播（仅 OP 管理员可用）</li>
 * </ul>
 *
 * @author Zm_Mmm
 * @since 2026-04-29
 */
public class UtilitySkill implements Skill {

    private static final int MIN_DELAY_SECONDS = 1;
    private static final int MAX_DELAY_SECONDS = 60;
    private static final long LLM_TIMEOUT_SECONDS = 120;

    private static final String DEFAULT_NOTIFY_SYSTEM_PROMPT = """
            你是 Minecraft 游戏助手，正在多步骤任务执行过程中向玩家发送阶段性通知。
            请用简洁自然的语气概括下方数据的关键信息，不超过2句话、80个汉字。
            不要使用任何标题或模板，直接说出关键结果。
            不要提及'系统提示'、'任务步骤'、'执行结果'等内部机制。
            将英文物品名转换为中文（如 DIAMOND→钻石，STICK→木棍）。
            直接输出通知内容，不要有任何前缀。
            """;

    private static final String DEFAULT_NOTIFY_USER_PROMPT = "请概括以下数据的关键信息：\n{0}";

    private static final String DEFAULT_BROADCAST_SYSTEM_PROMPT = """
            你是 Minecraft 服务器的全服广播文案撰写助手。
            管理员提供了一段原始信息，请你将其美化为适合全服广播的文案。
            要求：语气热情友好，排版清晰（可使用列表、分段），不超过300个汉字。
            将英文物品名转换为中文（如 DIAMOND→钻石，STICK→木棍）。
            去掉所有 Minecraft 颜色代码和格式代码（如§f、§l、§x等）。
            不要提及'管理员'、'系统'、'广播'等内部机制。
            直接输出广播文案内容，不要有任何前缀或解释。
            """;

    private static final String DEFAULT_BROADCAST_USER_PROMPT = "请将以下信息美化为全服广播文案：\n{0}";

    /**
     * 延迟调度器：专用于 delay_wait 的非阻塞延迟
     * <p>单线程调度器，线程安全，delay_wait 不会并发执行</p>
     */
    private static final ScheduledExecutorService DELAY_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "UtilitySkill-Delay");
        t.setDaemon(true);
        return t;
    });

    private final SkillConfigManager configManager;

    public UtilitySkill() {
        this.configManager = SkillConfigManager.getInstance();

        // 如果配置不存在，保存默认配置并动态加载
        if (configManager != null && configManager.getSkillConfig("utility", "UtilitySkill") == null) {
            configManager.saveDefaultSkillConfig("utility", "UtilitySkill");
            configManager.loadSingleSkillConfig("utility", "UtilitySkill");
        }
    }

    @Override
    public String getName() {
        return "utility";
    }

    @Override
    public String getDescription() {
        SkillConfig config = getConfig();
        return (config != null && !config.getDescription().isEmpty()) ? config.getDescription() : null;
    }

    @Override
    public Map<String, String> getActions() {
        SkillConfig config = getConfig();
        return (config != null && config.getActionDescriptions() != null) ? new LinkedHashMap<>(config.getActionDescriptions()) : Collections.emptyMap();
    }

    @Override
    public List<String> getHints() {
        SkillConfig config = getConfig();
        return (config != null && config.getHints() != null && !config.getHints().isEmpty()) ? new ArrayList<>(config.getHints()) : new ArrayList<>();
    }

    @Override
    public String getRequiredPermission() {
        return PluginPermissionEnum.UTILITY.getNode();
    }

    @Override
    public boolean isAvailable(SkillContext context) {
        return context.getPlayer() != null;
    }

    @Override
    public CompletableFuture<SkillResult> execute(SkillContext context) {
        String action = context.getAction();
        if (action == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("未指定工具动作"));
        }

        Player player = context.getPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("工具技能仅支持在线玩家"));
        }

        return switch (action) {
            case "delay_wait" -> handleDelayWait(context);
            case "notify_player" -> handleNotifyPlayer(context);
            case "broadcast_message" -> handleBroadcastMessage(context);
            default ->
                    CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("未知工具动作: {}", action)));
        };
    }

    private SkillConfig getConfig() {
        return (configManager != null) ? configManager.getSkillConfig("utility", "UtilitySkill") : null;
    }

    /**
     * 从 SkillConfig 缓存读取自定义字段（零磁盘 IO，支持热重载）
     */
    private String getConfigField(String key, String defaultValue) {
        SkillConfig config = getConfig();
        return (config != null) ? config.getCustomField(key, defaultValue) : defaultValue;
    }


    /**
     * delay_wait
     */
    private CompletableFuture<SkillResult> handleDelayWait(SkillContext context) {
        Player player = context.getPlayer();

        if (!PluginPermissionEnum.UTILITY_DELAY_WAIT.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.UTILITY_DELAY_WAIT.getNode())));
        }

        String secondsStr = context.getEntity("seconds");
        if (secondsStr == null || secondsStr.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure("缺少 seconds 参数"));
        }

        int seconds;
        try {
            seconds = Integer.parseInt(secondsStr);
        } catch (NumberFormatException e) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("无效的秒数: {}", secondsStr)));
        }

        // 校验范围
        seconds = Math.max(MIN_DELAY_SECONDS, Math.min(seconds, MAX_DELAY_SECONDS));

        final int delaySeconds = seconds;
        PluginLoggerUtil.debug("工具技能", "延迟等待: {}秒", delaySeconds);

        CompletableFuture<SkillResult> future = new CompletableFuture<>();
        DELAY_SCHEDULER.schedule(() -> {
            PluginLoggerUtil.debug("工具技能", "延迟完成: {}秒", delaySeconds);
            future.complete(SkillResult.success(I18nService.tr("已等待{}秒", delaySeconds)));
        }, delaySeconds, TimeUnit.SECONDS);
        return future;
    }

    /**
     * notify_player
     */
    private CompletableFuture<SkillResult> handleNotifyPlayer(SkillContext context) {
        Player player = context.getPlayer();

        if (!PluginPermissionEnum.UTILITY_NOTIFY_PLAYER.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用此功能: {}", PluginPermissionEnum.UTILITY_NOTIFY_PLAYER.getNode())));
        }

        String message = context.getEntity("message");
        if (message == null || message.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure("缺少 message 参数"));
        }

        final String playerName = player.getName();
        PluginLoggerUtil.debug("工具技能", "主动通知玩家: {}", playerName);

        KilacraftAI plugin = KilacraftAI.getInstance();
        LLMProvider llmProvider = requireLLMProvider();
        if (llmProvider == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("LLM Provider 未初始化"));
        }

        // 从配置缓存读取提示词
        String systemPrompt = getConfigField("notify_system_prompt", DEFAULT_NOTIFY_SYSTEM_PROMPT);

        // 画像注入
        var profileManager = plugin.getProfileManager();
        if (profileManager != null) {
            systemPrompt = profileManager.injectProfileSummary(systemPrompt, context.getPlayer().getUniqueId());
        }
        String userPromptTemplate = getConfigField("notify_user_prompt", DEFAULT_NOTIFY_USER_PROMPT);
        String userPrompt = userPromptTemplate.replace("{0}", message);

        PluginLoggerUtil.debug("工具技能", I18nService.tr("阶段性通知摘要 - 玩家: {}, systemPrompt: [{}]", playerName, systemPrompt));
        PluginLoggerUtil.debug("工具技能", I18nService.tr("阶段性通知摘要 - 玩家: {}, userPrompt: [{}]", playerName, userPrompt));

        // 动态流式输出配置
        AIResponsePipeline pipeline = plugin.getResponsePipeline();
        boolean streamEnabled = plugin.getConfigManager().getOutputConfigManager().isStreamEnabled();
        AIResponseHandler handler = createPlayerOutputHandler(player, pipeline, OutputScenarioEnum.TASK_RESULT, streamEnabled);

        Deque<ConversationManager.Message> emptyHistory = new ArrayDeque<>();

        CompletableFuture<String> llmFuture = llmProvider.processRequestWithCustomSystemPrompt(userPrompt, playerName, emptyHistory, handler, systemPrompt, false, false, false);

        return llmFuture.orTimeout(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS).handle((response, ex) -> {
            if (ex != null) {
                PluginLoggerUtil.warn("工具技能", "通知玩家 {} 失败: {}", playerName, ex.getMessage());
                return SkillResult.failure(I18nService.tr("通知玩家失败: {}", ex.getMessage()));
            }
            PluginLoggerUtil.debug("工具技能", "通知完成: {}", playerName);
            return SkillResult.success("已通知玩家");
        });
    }

    /**
     * broadcast_message
     */
    private CompletableFuture<SkillResult> handleBroadcastMessage(SkillContext context) {
        Player player = context.getPlayer();

        if (!PluginPermissionEnum.UTILITY_BROADCAST.hasPermission(player)) {
            return CompletableFuture.completedFuture(SkillResult.failure(I18nService.tr("你没有权限使用全服广播功能: {}", PluginPermissionEnum.UTILITY_BROADCAST.getNode())));
        }

        String message = context.getEntity("message");
        if (message == null || message.isEmpty()) {
            return CompletableFuture.completedFuture(SkillResult.failure("缺少 message 参数"));
        }

        final String playerName = player.getName();
        PluginLoggerUtil.debug("工具技能", "全服广播请求，发起者: {}, 消息长度: {}", playerName, message.length());

        KilacraftAI plugin = KilacraftAI.getInstance();
        LLMProvider llmProvider = requireLLMProvider();
        if (llmProvider == null) {
            return CompletableFuture.completedFuture(SkillResult.failure("LLM Provider 未初始化"));
        }

        // 从配置缓存读取提示词
        String systemPrompt = getConfigField("broadcast_system_prompt", DEFAULT_BROADCAST_SYSTEM_PROMPT);

        // 画像注入
        var profileManager = plugin.getProfileManager();
        if (profileManager != null) {
            systemPrompt = profileManager.injectProfileSummary(systemPrompt, player.getUniqueId());
        }

        String userPromptTemplate = getConfigField("broadcast_user_prompt", DEFAULT_BROADCAST_USER_PROMPT);
        String userPrompt = userPromptTemplate.replace("{0}", message);

        PluginLoggerUtil.debug("工具技能", I18nService.tr("全服广播摘要 - 发起者: {}, systemPrompt: [{}]", playerName, systemPrompt));
        PluginLoggerUtil.debug("工具技能", I18nService.tr("全服广播摘要 - 发起者: {}, userPrompt: [{}]", playerName, userPrompt));

        // 强制 CHAT 载体，不开启流式，仅收集 LLM 美化后的文本
        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        AIResponseHandler silentHandler = new AIResponseHandler() {
            @Override
            public UUID getPlayerId() {
                return player.getUniqueId();
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
            public void showStreamChunk(String chunk, String currentMessage) { /* 静默 */ }

            @Override
            public void handleError(String errorMessage) {
                // 后台不重复 §c 玩家串（provider WARN 已含详情），用纯文本标记完成异常
                responseFuture.completeExceptionally(new RuntimeException(I18nService.tr("全服广播 LLM 分析失败（详见控制台 WARN）")));
            }

            @Override
            public boolean isStreamOutputEnabled() {
                return false;
            }
        };

        Deque<ConversationManager.Message> emptyHistory = new ArrayDeque<>();

        llmProvider.processRequestWithCustomSystemPrompt(userPrompt, playerName, emptyHistory, silentHandler, systemPrompt, false, false, false);

        return responseFuture.orTimeout(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS).handle((response, ex) -> {
            if (ex != null) {
                PluginLoggerUtil.warn("工具技能", "全服广播 LLM 分析失败，发起者: {}, 错误: {}", playerName, ex.getMessage());
                return SkillResult.failure(I18nService.tr("全服广播失败: {}", ex.getMessage()));
            }

            // 通过 pipeline.broadcast 全服广播（强制 CHAT 载体）
            plugin.getResponsePipeline().broadcast(response, null);
            PluginLoggerUtil.debug("工具技能", "全服广播完成，发起者: {}", playerName);
            return SkillResult.success(I18nService.tr("已全服广播消息"));
        });
    }

    /**
     * 获取当前 LLM Provider（线程安全：仅从已初始化的管理器读取）
     */
    private LLMProvider requireLLMProvider() {
        KilacraftAI plugin = KilacraftAI.getInstance();
        return (plugin != null) ? plugin.getLlmManager().getCurrentProvider() : null;
    }

    /**
     * 创建通过 AIResponsePipeline 输出给玩家的 Handler
     *
     * @param streamEnabled 是否启用流式输出（由服主配置决定）
     */
    private AIResponseHandler createPlayerOutputHandler(Player player, AIResponsePipeline pipeline, OutputScenarioEnum scenario, boolean streamEnabled) {
        return new AIResponseHandler() {
            @Override
            public UUID getPlayerId() {
                return player.getUniqueId();
            }

            @Override
            public String getPlayerName() {
                return player.getName();
            }

            @Override
            public void showResponse(String response) {
                if (streamEnabled) {
                    pipeline.completeStream(player, response, scenario);
                } else {
                    pipeline.send(player, response, scenario);
                }
            }

            @Override
            public void showStreamChunk(String chunk, String currentMessage) {
                if (streamEnabled) {
                    pipeline.updateStream(player, chunk, currentMessage, pipeline.getChannelForScenario(scenario));
                }
            }

            @Override
            public void handleError(String errorMessage) {
                if (streamEnabled) {
                    pipeline.cancelStream(player);
                }
                pipeline.sendError(player, errorMessage);
            }

            @Override
            public boolean isStreamOutputEnabled() {
                return streamEnabled;
            }
        };
    }
}
