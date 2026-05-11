package com.zm.kilacraftAI.handler;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.config.I18nService;
import com.zm.kilacraftAI.config.LanguageManager;
import com.zm.kilacraftAI.db.ConversationSource;
import com.zm.kilacraftAI.enums.OutputScenario;
import com.zm.kilacraftAI.handler.impl.ConsoleResponseHandler;
import com.zm.kilacraftAI.handler.impl.PlayerResponseHandler;
import com.zm.kilacraftAI.manager.ConversationManager;
import com.zm.kilacraftAI.metrics.MetricsCollector;
import com.zm.kilacraftAI.output.AIResponsePipeline;
import com.zm.kilacraftAI.skills.framework.SkillContext;
import com.zm.kilacraftAI.skills.framework.SkillIntent;
import com.zm.kilacraftAI.skills.framework.task.AnalysisSummary;
import com.zm.kilacraftAI.skills.framework.task.TaskExecutor;
import com.zm.kilacraftAI.skills.framework.task.TaskPlan;
import com.zm.kilacraftAI.util.AIRequestValidator;
import com.zm.kilacraftAI.util.MessageUtil;
import com.zm.kilacraftAI.util.PluginLogger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI 请求统一处理器
 *
 * <p>封装 LLM 意图识别和技能执行的通用逻辑，供 ChatListener 和 KilacraftCommand 复用</p>
 *
 * @author Zm_Mmm
 * @since 2026-03-31
 */
public class AIRequestHandler {

    private final KilacraftAI plugin;
    private final AIRequestValidator validator;
    private final LanguageManager languageManager;

    public AIRequestHandler(KilacraftAI plugin) {
        this.plugin = plugin;
        this.validator = new AIRequestValidator(plugin);
        this.languageManager = plugin.getLanguageManager();
    }

    /**
     * 处理玩家 AI 请求（默认私信回复）
     */
    public void handleAIRequest(Player player, String message, Deque<ConversationManager.Message> playerHistory, boolean enableAgent) {
        handleAIRequest(player, message, playerHistory, enableAgent, false, ConversationSource.CHAT);
    }

    /**
     * 处理玩家 AI 请求
     *
     * @param publicReply 是否将AI回复广播给所有在线玩家（公屏回复）
     */
    public void handleAIRequest(Player player, String message, Deque<ConversationManager.Message> playerHistory, boolean enableAgent, boolean publicReply) {
        handleAIRequest(player, message, playerHistory, enableAgent, publicReply, ConversationSource.CHAT);
    }

    /**
     * 处理玩家 AI 请求（含 source 参数）
     *
     * @param publicReply 是否将AI回复广播给所有在线玩家（公屏回复）
     * @param source      来源标识
     */
    public void handleAIRequest(Player player, String message, Deque<ConversationManager.Message> playerHistory, boolean enableAgent, boolean publicReply, ConversationSource source) {
        // 使用统一的响应管线
        AIResponsePipeline pipeline = plugin.getResponsePipeline();

        java.util.function.Consumer<String> sendResponse;
        java.util.function.Consumer<String> sendError;

        if (publicReply) {
            // 公屏模式：先发送给触发者（使用场景配置），再广播给所有人
            sendResponse = response -> {
                // 1. 发送给触发者（使用 NORMAL_CHAT 场景配置，如 ACTION_BAR/SIDEBAR/CHAT）
                pipeline.send(player, response, OutputScenario.NORMAL_CHAT);
                // 2. 公屏广播（强制 CHAT）
                // 如果NORMAL_CHAT的载体是CHAT，需要排除触发者避免重复；否则传null让所有人都收到
                boolean isChatChannel = (pipeline.getChannelForScenario(OutputScenario.NORMAL_CHAT) == com.zm.kilacraftAI.enums.OutputChannel.CHAT);
                pipeline.broadcast(response, isChatChannel ? player : null);
            };
        } else {
            // 私信模式：只发给触发者
            sendResponse = response -> pipeline.send(player, response, OutputScenario.NORMAL_CHAT);
        }
        sendError = error -> pipeline.sendError(player, languageManager.getPluginCommandError() + error);

        RequestContext ctx = new RequestContext(player.getName(), player, playerHistory, sendResponse, sendError, OutputScenario.NORMAL_CHAT, publicReply, source);
        handleAIRequestInternal(message, ctx, enableAgent);
    }

    /**
     * 处理控制台 AI 请求
     */
    public void handleAIRequestForConsole(CommandSender sender, String message, boolean enableAgent) {
        UUID consoleUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
        Deque<ConversationManager.Message> consoleHistory = getOrCreateHistory(consoleUUID);

        RequestContext ctx = new RequestContext("Console", null, consoleHistory, response -> sender.sendMessage(MessageUtil.getAIPrefix() + MessageUtil.convertMarkdownToMinecraft(response)), error -> sender.sendMessage(languageManager.getPluginCommandError() + error), OutputScenario.NORMAL_CHAT, false, ConversationSource.CONSOLE);
        handleAIRequestInternal(message, ctx, enableAgent);
    }

    /**
     * 处理 AI 请求（内部统一逻辑）
     */
    private void handleAIRequestInternal(String message, RequestContext ctx, boolean enableAgent) {
        // 校验 API Key 是否已配置
        if (!plugin.getConfigManager().isApiKeyConfigured()) {
            String hint = I18nService.tr("§c[AI请求] API Key 未配置！请编辑 plugins/Kilacraft-AI/llm.yml 中的 llm.api_key 后重启服务器或执行 /kilacraft reload");
            ctx.sendError.accept(hint);
            PluginLogger.warn("AI请求", "拒绝请求：API Key 未配置");
            return;
        }

        if (!enableAgent) {
            PluginLogger.debug("AI请求", "Agent 能力已禁用，进入普通 AI 处理");
            MetricsCollector.getInstance().recordRequestType("normal_chat");
            handleNormalAIRequest(message, ctx);
            return;
        }

        PluginLogger.debug("AI请求", "开始 LLM 意图识别，用户：{}, 消息：{}", ctx.name(), message);

        var intentRecognizer = plugin.getIntentRecognizer();
        if (intentRecognizer == null) {
            MetricsCollector.getInstance().recordRequestType("normal_chat");
            handleNormalAIRequest(message, ctx);
            return;
        }

        intentRecognizer.recognizeIntent(message, ctx.history(), ctx.name(), ctx.player()).thenAccept(result -> {
            if (result instanceof TaskPlan taskPlan && taskPlan.isMultiStep()) {
                MetricsCollector.getInstance().recordRequestType("skill_execution");
                handleTaskPlan(taskPlan, message, ctx);
            } else if (result instanceof SkillIntent intent && intent.isValid()) {
                MetricsCollector.getInstance().recordRequestType("skill_execution");
                handleSkillIntent(intent, message, ctx);
            } else {
                PluginLogger.debug("AI请求", "意图识别结束，回退到普通 AI 处理");
                MetricsCollector.getInstance().recordRequestType("normal_chat");
                handleNormalAIRequest(message, ctx);
            }
        }).exceptionally(throwable -> {
            ctx.sendError.accept(throwable.getMessage());
            return null;
        });
    }

    /**
     * 处理任务计划（多步骤）
     */
    private void handleTaskPlan(TaskPlan taskPlan, String message, RequestContext ctx) {
        PluginLogger.debug("AI请求", "识别到多步骤任务：{}", taskPlan.getGoal());

        TaskExecutor taskExecutor = new TaskExecutor(plugin.getSkillManager());
        SkillContext context = new SkillContext(ctx.player(), null, new HashMap<>());

        // TaskExecutor 返回 AnalysisSummary，通过中间层进行 LLM 二次分析
        taskExecutor.executeTask(taskPlan, context, ctx.history(), message).thenAccept(summary -> {
            PluginLogger.debug("AI请求", "任务计划执行完成，开始 LLM 二次分析...");

            // 通过中间层输出（验证通过后已显示占位符，这里不需要再显示）
            plugin.getLlmOutputCoordinator().outputAnalysisResult(ctx.player(), summary, context, ctx.history(), OutputScenario.TASK_RESULT, false).thenAccept(result -> {
                // 保存历史记录
                validator.saveToHistory(ctx.history(), message, result.getMessage(), ctx.player() != null ? ctx.player().getUniqueId() : null, null, ctx.source());

                // 公屏广播（outputAnalysisResult只输出给触发者，公屏需要额外广播）
                // 如果场景载体是CHAT，需要排除触发者避免重复；否则传null让所有人都收到
                if (ctx.isBroadcast()) {
                    boolean isChatChannel = (plugin.getResponsePipeline().getChannelForScenario(OutputScenario.TASK_RESULT) == com.zm.kilacraftAI.enums.OutputChannel.CHAT);
                    plugin.getResponsePipeline().broadcast(result.getMessage(), isChatChannel ? ctx.player() : null);
                }
            });
        });
    }

    /**
     * 处理技能意图（单意图）
     */
    private void handleSkillIntent(SkillIntent intent, String message, RequestContext ctx) {
        PluginLogger.debug("AI请求", "识别到单意图：{}", intent.getAction());

        SkillContext context = new SkillContext(ctx.player(), intent.getAction(), intent.getEntities());

        plugin.getSkillManager().executeSkillByIntent(intent, context).thenCompose(execResult -> {
            if (execResult.isSuccess()) {
                PluginLogger.debug("技能执行", "技能执行成功");
                AnalysisSummary summary = new AnalysisSummary().userMessage(message).addResult("SUCCESS", execResult.getMessage()).statistics(1, 0, 0);

                // 通过中间层输出（验证通过后已显示占位符，这里不需要再显示）
                return plugin.getLlmOutputCoordinator().outputAnalysisResult(ctx.player(), summary, context, ctx.history(), OutputScenario.SKILL_RESULT, false).thenApply(result -> {
                    // 公屏广播（outputAnalysisResult只输出给触发者，公屏需要额外广播）
                    // 如果场景载体是CHAT，需要排除触发者避免重复；否则传null让所有人都收到
                    if (ctx.isBroadcast()) {
                        boolean isChatChannel = (plugin.getResponsePipeline().getChannelForScenario(OutputScenario.SKILL_RESULT) == com.zm.kilacraftAI.enums.OutputChannel.CHAT);
                        plugin.getResponsePipeline().broadcast(result.getMessage(), isChatChannel ? ctx.player() : null);
                    }

                    return result;
                });
            } else {
                return CompletableFuture.completedFuture(execResult);
            }
        }).thenAccept(finalResult -> {
            if (finalResult.isSuccess()) {
                // 保存历史记录
                validator.saveToHistory(ctx.history(), message, finalResult.getMessage(), ctx.player() != null ? ctx.player().getUniqueId() : null, null, ctx.source());
            } else {
                PluginLogger.debug("技能执行", "技能执行失败：{}", finalResult.getMessage());
                PluginLogger.debug("技能执行", "已回退到普通 AI 处理");
                // 将技能失败信息注入消息上下文，回退到普通AI兜底
                // LLM看到失败信息后可以理解原因并引导玩家（如提示取消旧的挂机任务）
                String enrichedMessage = message + "\n" + I18nService.tr("[系统提示：技能执行失败 - {}]", finalResult.getMessage());
                handleNormalAIRequest(enrichedMessage, ctx, message);
            }
        }).exceptionally(throwable -> {
            ctx.sendError.accept(throwable.getMessage());
            PluginLogger.error("技能执行", I18nService.tr("技能执行异常: {}", throwable.getMessage()), throwable);
            return null;
        });
    }

    /**
     * 处理普通 AI 请求（无技能调用）
     */
    private void handleNormalAIRequest(String message, RequestContext ctx) {
        handleNormalAIRequest(message, ctx, null);
    }

    private void handleNormalAIRequest(String message, RequestContext ctx, String originalMessage) {
        PluginLogger.debug("AI请求", "{} 的历史记录数量：{}", ctx.name(), ctx.history().size());

        String historyMessage = (originalMessage != null) ? originalMessage : message;

        AIResponseHandler handler;
        if (ctx.player() != null) {
            handler = new PlayerResponseHandler(plugin, ctx.player(), ctx.scenario(), ctx.sendResponse);
        } else {
            handler = new ConsoleResponseHandler(getSenderFromContext(ctx));
        }

        // 构建系统提示词（玩家时注入画像摘要，控制台不注入）
        String systemPrompt = plugin.getConfigManager().getSystemPrompt();
        if (ctx.player() != null && plugin.getConfigManager().isProfileInjectionEnabled()) {
            var profileManager = plugin.getProfileManager();
            if (profileManager != null) {
                String profileSummary = profileManager.buildProfileSummary(ctx.player().getUniqueId());
                if (!profileSummary.isEmpty()) {
                    systemPrompt = systemPrompt + "\n\n" + profileSummary;
                }
            }
        }

        plugin.getLlmManager().getCurrentProvider().processRequestWithCustomSystemPrompt(message, ctx.name(), ctx.history(), handler, systemPrompt).thenAccept(fullResponse -> validator.saveToHistory(ctx.history(), historyMessage, fullResponse, ctx.player() != null ? ctx.player().getUniqueId() : null, null, ctx.source())).exceptionally(throwable -> {
            ctx.sendError.accept(throwable.getMessage());
            return null;
        });
    }

    /**
     * 获取或创建历史记录
     */
    private Deque<ConversationManager.Message> getOrCreateHistory(UUID playerId) {
        ConversationManager convManager = plugin.getConversationManager();
        Deque<ConversationManager.Message> history = convManager.getHistory(playerId);
        if (history == null) {
            history = convManager.getHistory().computeIfAbsent(playerId, k -> new ArrayDeque<>());
        }
        return history;
    }

    /**
     * 从上下文获取 CommandSender（用于控制台）
     */
    private CommandSender getSenderFromContext(RequestContext ctx) {
        return plugin.getServer().getConsoleSender();
    }

    /**
     * 请求上下文 - 统一玩家和控制台的差异
     */
    private record RequestContext(String name, Player player, Deque<ConversationManager.Message> history,
                                  java.util.function.Consumer<String> sendResponse,
                                  java.util.function.Consumer<String> sendError, OutputScenario scenario,
                                  boolean isBroadcast, ConversationSource source) {
    }
}
